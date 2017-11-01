(ns e85th.backend.core.user
  (:require [e85th.backend.core.db :as db]
            [e85th.backend.core.address :as address]
            [e85th.backend.core.domain :as domain]
            [e85th.backend.core.channel :as channel]
            [e85th.commons.sms :as sms]
            [e85th.commons.tel :as tel]
            [e85th.commons.ex :as ex]
            [e85th.commons.ext :as ext]
            [e85th.commons.sql :as sql]
            [taoensso.timbre :as log]
            [buddy.hashers :as hashers]
            [clj-time.core :as t]
            [clojure.spec.alpha :as s]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [clojure.core.match :refer [match]]
            [e85th.commons.token :as token]
            [e85th.commons.email :as email]
            [clojure.string :as str]))

;(remove-ns (ns-name *ns*))

(def ^:const password-required-err :user.error/password-required)
(def ^:const channel-verification-failed-err :user.error/channel-verification-failed)


;;----------------------------------------------------------------------
(s/fdef get-all-fields-by-id
        :args (s/cat :res map? :id ::domain/id)
        :ret  (s/nilable ::domain/user))

(defn get-all-fields-by-id
  [{:keys [db]} id]
  (db/select-user-by-id db id))

(def get-all-fields-by-id! (ex/wrap-not-found get-all-fields-by-id))


;;----------------------------------------------------------------------
(s/fdef get-by-id
        :args (s/cat :res map? :id ::domain/id)
        :ret  (s/nilable ::domain/user))

(defn get-by-id
  "This exists to make it a little bit harder to expose the password digest."
  [res id]
  (some-> (get-all-fields-by-id res id)
          (dissoc :password-digest)))

(def get-by-id! (ex/wrap-not-found get-by-id))

;;----------------------------------------------------------------------
(s/fdef validate-new-user
        :args (s/cat :res map? :new-user ::domain/new-user)
        :ret nil?)

(defn- validate-new-user
  [res {:keys [channel-identifiers password] :as new-user}]
  (channel/validate-channel-identifiers res channel-identifiers)
  (when (contains? (set (map :channel-type-id channel-identifiers)) channel/email-type-id)
    (when-not (seq password)
      (throw (ex/validation password-required-err "Passowrd is required for e-mail signup.")))))

(defn- user->user-setup
  "User record with password-digest if password is present."
  [{:keys [password] :as user}]
  (cond-> (select-keys user [:first-name :last-name])
    (seq password) (assoc :password-digest (hashers/derive password))))

(defn- new-user->channels
  "NewUser to map required for inserting into channel."
  [new-user user-id]
  (-> new-user
      (select-keys [:channel-type-id :identifier])
      (assoc :user-id user-id)))


;;----------------------------------------------------------------------
(s/fdef setup
        :args (s/cat :res map? :new-user ::domain/new-user :creator-id ::domain/user-id)
        :ret ::domain/user-id)

(defn setup
  "Creates a new user from a channel."
  [{:keys [db] :as res} {:keys [channel-identifiers role-ids] :as new-user} creator-id]
  (validate-new-user res new-user)
  (let [user-record (user->user-setup new-user)
        user-id (volatile! nil)]
    (jdbc/with-db-transaction [txn db]
      (vreset! user-id (db/insert-user txn user-record creator-id))
      (db/insert-channels txn (map #(assoc % :user-id @user-id) channel-identifiers) creator-id)
      (db/insert-user-roles txn @user-id role-ids creator-id)
      (when-let [address (:address new-user)]
        (address/assoc-user (assoc res :db txn) address @user-id creator-id)))
    @user-id))


(defn- user->user-update
  "Only allows updating first and last name. Updating password is done via a dedicated method."
  [user]
  (select-keys user [:first-name :last-name]))

;;----------------------------------------------------------------------
(s/fdef update-by-id
        :args (s/cat :res map? :user-id ::domain/user-id :user map? :update-user-id ::domain/user-id)
        :ret int?)

(defn update-by-id
  "NB. This won't update the password. Call set-password separately."
  [{:keys [db] :as res} user-id user updater-user-id]
  (let [user-record (user->user-update user)]
    (db/update-user-by-id db user-id user-record updater-user-id)))


;;----------------------------------------------------------------------
(s/fdef set-password
        :args (s/cat :res map? :user-id ::domain/user-id :password string? :modifier-id ::domain/user-id)
        :ret  any?)

(defn set-password
  [{:keys [db]} user-id password modifier-id]
  (db/update-user-by-id db user-id {:password-digest (hashers/derive password)} modifier-id))


;;----------------------------------------------------------------------
(s/fdef get-roles-by-id
        :args (s/cat :res map? :user-id ::domain/user-id)
        :ret (s/coll-of ::domain/role))

(defn get-roles-by-id
  [{:keys [db]} user-id]
  (db/select-user-roles-by-user-id db user-id))

;;----------------------------------------------------------------------
(s/fdef get-permissions-by-id
        :args (s/cat :res map? :user-id ::domain/user-id)
        :ret (s/coll-of ::domain/permission))

(defn get-permissions-by-id
  [{:keys [db]} user-id]
  (db/select-user-permissions-by-user-id db user-id))

(defn get-user-ids-by-role-id
  [{:keys [db]} role-id]
  (db/select-user-ids-with-role-ids db [role-id]))

;;----------------------------------------------------------------------
(s/fdef assoc-roles
        :args (s/cat :res map? :user-id ::domain/user-id :role-ids (s/coll-of ::domain/id) :updater-id ::domain/user-id)
        :ret any?)

(defn assoc-roles
  [{:keys [db]} user-id role-ids updater-id]
  (db/insert-user-roles db user-id (set role-ids) updater-id))

;;----------------------------------------------------------------------
(s/fdef dissoc-roles
        :args (s/cat :res map? :user-id ::domain/user-id :role-ids (s/coll-of ::domain/id) :updater-id ::domain/user-id)
        :ret any?)

(defn dissoc-roles
  [{:keys [db]} user-id role-ids updater-id]
  (db/delete-user-roles db user-id (set role-ids)))

;;----------------------------------------------------------------------
(s/fdef delete-by-id
        :args (s/cat :res map? :id ::domain/user-id :updater-id ::domain/user-id)
        :ret any?)

(defn delete-by-id
  [{:keys [db]} id updater-id]
  (db/delete-user-by-id db id))

;;----------------------------------------------------------------------
(s/fdef set-roles
        :args (s/cat :res map? :user-id ::domain/user-id :role-ids (s/coll-of ::domain/id) :updater-id ::domain/user-id)
        :ret any?)

(defn set-roles
  [{:keys [db] :as res} user-id role-ids updater-id]
  (let [role-ids (set role-ids)
        cur-roles (set (map :id (get-roles-by-id res user-id)))
        rm-roles (set/difference cur-roles role-ids)
        add-roles (set/difference role-ids cur-roles)]

    ;(log/debugf "roles: %s, cur-roles: %s, rm-roles: %s, add-roles: %s" role-ids cur-roles rm-roles add-roles)
    (jdbc/with-db-transaction [txn db]
      (let [res (assoc res :db txn)]
        (when (seq rm-roles)
          (dissoc-roles res user-id rm-roles updater-id))
        (when (seq add-roles)
          (assoc-roles res user-id add-roles updater-id))))))

(defn get-by-ids
  [{:keys [db]} ids]
  (if (seq ids)
    (db/select-users-by-ids db {:ids ids})
    []))

;;----------------------------------------------------------------------
(s/fdef get-role-and-permission-names
        :args (s/cat :res map? :user-id ::domain/user-id)
        :ret ::domain/role-and-permission-names)

(defn get-role-and-permission-names
  "When no caching is involved this will get both the role and permission names in
   one sql query, potentially useful for authorization off of roles and permissions."
  [{:keys [db]} user-id]
  (let [data (->> (db/select-user-role-and-permission-names db {:user-id user-id})
                  (ext/group-by+ :kind (comp keyword :name) set))]
    (merge {:role-names #{} :permission-names #{}}
     (set/rename-keys data {"role" :role-names "permission" :permission-names}))))
