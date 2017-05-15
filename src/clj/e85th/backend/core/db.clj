(ns e85th.backend.core.db
  (:require [hugsql.core :refer [def-db-fns]]
            [clojure.java.jdbc :as jdbc]
            [schema.core :as s]
            [clj-time.core :as t]
            [taoensso.timbre :as log]
            [e85th.commons.sql :as sql]
            [e85th.backend.core.models :as m]))

(def-db-fns "sql/e85th/backend/core.sql")

(def ^{:doc "Escaped keyword for ease of use"}
  user-tbl (keyword "\"user\""))

(s/defn insert-user :- s/Int
  "Insert the user record and return the id for the row"
  [db new-user :- m/UserSave user-id :- s/Int]
  (:id (sql/insert! db user-tbl new-user user-id)))

(s/defn update-user :- s/Int
  [db user-id user-update updater-user-id]
  (sql/update! db user-tbl (dissoc user-update :id) ["id = ?" user-id] updater-user-id))

(s/defn insert-channel :- s/Int
  [db channel :- m/NewChannel user-id :- s/Int]
  (:id (sql/insert! db :channel channel user-id)))

(s/defn update-channel :- s/Int
  [db channel-id :- s/Int channel :- m/UpdateChannel user-id :- s/Int]
  (sql/update! db :channel channel ["id = ?" channel-id] user-id))

(s/defn insert-channels
  [db channels :- [m/NewChannel] user-id :- s/Int]
  (sql/insert-multi-with-audits! db :channel channels user-id))

(s/defn delete-user
  [db user-id :- s/Int]
  (sql/delete! db user-tbl ["id = ?" user-id]))

(s/defn delete-channel
  [db channel-id :- s/Int]
  (sql/delete! db :channel ["id = ?" channel-id]))

(def ^:private default-channel-params
  {:id-nil? true
   :id nil
   :user-id-nil? true
   :user-id nil
   :channel-type-id-nil? true
   :channel-type-id nil
   :identifier-nil? true
   :identifier nil
   :token-nil? true
   :token nil
   :token-expiration-nil? true
   :token-expiration nil
   :verified-at-nil? true
   :verified-at nil})

(s/defn select-channels*
  [db params]
  (->> (merge default-channel-params params)
       (select-channels db)))

(s/defn select-channels-by-user-id :- [m/Channel]
  "Select channels by user-id"
  [db user-id :- s/Int]
  (select-channels* db {:user-id-nil? false :user-id user-id}))

(s/defn select-channel-by-id :- (s/maybe m/Channel)
  [db id :- s/Int]
  (first (select-channels* db {:id-nil? false :id id})))

(s/defn select-channel-by-type :- (s/maybe m/Channel)
  "Select channel by the channel type and identifier."
  [db channel-type-id :- s/Int identifier :- s/Str]
  (let [params {:channel-type-id-nil? false :channel-type-id channel-type-id
                :identifier-nil? false :identifier identifier}
        rs (select-channels* db params)]
    (assert (<= (count rs) 1)
            (format "Expected at most 1 row for channel-type %s, identifier %s" channel-type-id identifier))
    (first rs)))

(s/defn select-channels-by-identifier :- [m/Channel]
  [db identifier :- s/Str]
  (select-channels* db {:identifier-nil? false :identifier identifier}))

(s/defn select-channel-for-user-auth :- (s/maybe m/Channel)
  [db identifier :- s/Str token :- s/Str]
  (let [params {:identifier-nil? false :identifier identifier
                :token-nil? false :token token
                :token-expiration-nil? false :token-expiration (t/now)}
        chans (select-channels* db params)]
    (assert (<=  (count chans) 1) "Expected at most 1 chan to match.")
    (first chans)))

(s/defn select-channel-by-token :- (s/maybe m/Channel)
  [db token :- s/Str]
  (let [chans (select-channels* db {:token-nil? false :token token})]
    (assert (<= (count chans) 1) "Expected at most 1 chan to match.")
    (first chans)))

(s/defn select-channel-by-active-token :- (s/maybe m/Channel)
  [db token :- s/Str]
  (let [params {:token-nil? false :token token
                :token-expiration-nil? false :token-expiration (t/now)}
        chans (select-channels* db params)]
    (assert (<= (count chans) 1) "Expected at most 1 chan to match.")
    (first chans)))

(s/defn insert-address :- s/Int
  [db address :- m/Address creator-id :- s/Int]
  (assert (not (contains? address :id)))
  (:id (sql/insert! db :address address creator-id)))

(s/defn update-address
  [db address-id :- s/Int data :- m/UpdateAddress user-id :- s/Int]
  (sql/update! db :address data ["id = ?" address-id] user-id))

(s/defn insert-user-address :- s/Int
  [db user-id :- s/Int address-id :- s/Int creator-id :- s/Int]
  (:id (sql/insert-with-create-audits! db :user-address {:user-id user-id
                                                         :address-id address-id} creator-id)))


(s/defn select-user-auth-by-user-id
  [db user-id :- s/Int]
  (select-user-auth db {:user-id user-id}))


(s/defn insert-user-roles
  [db user-id :- s/Int roles :- #{s/Int} creator-id :- s/Int]
  (let [xs (map #(hash-map :role-id % :user-id user-id) roles)]
    (sql/insert-multi-with-create-audits! db :user-role xs creator-id)))

(s/defn delete-user-roles
  [db user-id :- s/Int roles :- #{s/Int}]
  (let [xs (map #(hash-map :role-id % :user-id user-id) roles)]
    (jdbc/with-db-transaction [txn db]
      (doseq [role-id roles]
        (sql/delete! txn :user-role ["user_id = ? and role_id = ?" user-id role-id])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Roles
(s/defn insert-role :- s/Int
  "Inserts a role record and returns the id for the row."
  [db role :- m/Role user-id]
  (:id (sql/insert! db :role role user-id)))

(s/defn update-role :- s/Int
  "Updates a role record and returns the count updated."
  [db role-id role :- m/UpdateRole user-id]
  (sql/update! db :role (dissoc role :id) ["id = ?" role-id] user-id))

(s/defn delete-role
  [db id :- s/Int]
  (first (sql/delete! db :role ["id = ?" id])))

(def ^:private default-role-params
  {:id-nil? true :id nil
   :name-nil? true :name nil})

(s/defn select-role*
  [db params]
  (->> params
       (merge default-role-params)
       (select-role db)
       (map #(update-in % [:name] keyword))))

(s/defn select-role-by-id :- (s/maybe m/Role)
  [db role-id :- s/Int]
  (first (select-role* db {:id-nil? false :id role-id})))

(s/defn select-role-by-name :- (s/maybe m/Role)
  [db role-name :- s/Str]
  (first (select-role* db {:name-nil? false :name role-name})))

(s/defn select-all-roles :- [m/Role]
  [db]
  (select-role* db {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Permissions
(s/defn insert-permission :- s/Int
  "Inserts a permission record and returns the id for the row."
  [db permission :- m/Permission user-id]
  (:id (sql/insert! db :permission permission user-id)))

(s/defn update-permission :- s/Int
  "Updates a permission record and returns the count updated."
  [db permission-id permission :- m/UpdatePermission user-id]
  (sql/update! db :permission (dissoc permission :id) ["id = ?" permission-id] user-id))

(s/defn delete-permission
  [db id :- s/Int]
  (first (sql/delete! db :permission ["id = ?" id])))

(def ^:private default-permission-params
  {:id-nil? true :id nil
   :name-nil? true :name nil})

(s/defn select-permission*
  [db params]
  (->> params
       (merge default-permission-params)
       (select-permission db)
       (map #(update % :name keyword))))

(s/defn select-all-permissions :- [m/Permission]
  [db]
  (select-permission* db {}))

(s/defn select-permission-by-id :- (s/maybe m/Permission)
  [db permission-id :- s/Int]
  (first (select-permission* db {:id-nil? false :id permission-id})))

(s/defn select-permission-by-name :- (s/maybe m/Permission)
  [db permission-name :- s/Str]
  (first (select-permission* db {:name-nil? false :name permission-name})))


(s/defn select-permissions-by-role-ids :- [m/Permission]
  [db role-ids :- [s/Int]]
  (->> {:role-ids role-ids}
       (select-permissions-by-roles db)
       (map #(update % :name keyword))))

(s/defn select-roles-by-permission-ids :- [m/Role]
  [db permission-ids :- [s/Int]]
  (->> {:permission-ids permission-ids}
       (select-roles-by-permissions db)
       (map #(update % :name keyword))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Address
(def ^:private default-address-params
  {:ids-nil? true :ids nil})

(defn- select-address*
  [db params]
  (select-address db (merge default-address-params params)))

(s/defn select-addresses-by-ids :- [m/Address]
  [db ids :- [s/Int]]
  (if (seq ids)
    (select-address* db {:ids-nil? false :ids ids})
    []))

(s/defn select-address-ids-by-user-id :- [s/Int]
  [db user-id :- s/Int]
  (->> {:user-id user-id}
       (select-user-address db)
       (map :address-id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; User Role
(s/defn select-user-role*
  [db user-ids :- [s/Int] role-ids :- [s/Int]]
  (let [params (cond-> {:role-ids-nil? true :role-ids role-ids
                        :user-ids-nil? true :user-ids user-ids}
                 (some? (seq role-ids)) (assoc :role-ids-nil? false)
                 (some? (seq user-ids)) (assoc :user-ids-nil? false))]
    (select-user-role db params)))

(s/defn select-users-by-roles :- [s/Int]
  [db role-ids :- [s/Int]]
  (assert (seq role-ids) "Must specify at least one role id.")
  (map :user-id
       (select-user-role* db [] role-ids)))

(s/defn insert-role-permissions
  [db role-id :- s/Int permissions :- #{s/Int} creator-id :- s/Int]
  (let [xs (map #(hash-map :permission-id % :role-id role-id) permissions)]
    (sql/insert-multi-with-create-audits! db :role-permission xs creator-id)))


(s/defn delete-role-permissions-by-role-id
  [db role-id]
  (sql/delete! db :role-permission ["role_id = ?" role-id]))

(s/defn delete-role-permissions-by-permission-id
  [db permission-id]
  (sql/delete! db :role-permission ["permission_id = ?" permission-id]))

(s/defn delete-role-permissions
  [db role-id :- s/Int permissions :- #{s/Int}]
  (let [xs (map #(hash-map :permission-id % :role-id role-id) permissions)]
    (jdbc/with-db-transaction [txn db]
      (doseq [permission-id permissions]
        (sql/delete! txn :role-permission ["role_id = ? and permission_id = ?" role-id permission-id])))))
