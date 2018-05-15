(ns e85th.backend.core.db
  (:require [hugsql.core :refer [def-db-fns]]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clj-time.core :as t]
            [taoensso.timbre :as log]
            [e85th.backend.core.domain :as domain]
            [e85th.commons.sql :as sql]))

(def-db-fns "sql/e85th/backend/core.sql")

(def user-tbl "Escaped keyword for ease of use" (keyword "\"user\""))

;;----------------------------------------------------------------------
(s/fdef insert*
        :args (s/cat :table keyword? :db some? :row map? :user-id ::domain/user-id)
        :ret  ::domain/id)

(defn insert*
  "This is meant to be partialed which is why db doesn't appear first.."
  [table db row user-id]
  (:id (sql/insert! db table row user-id)))

;;----------------------------------------------------------------------
(s/fdef update*
        :args (s/cat :table keyword? :where string? :db some?
                     :id ::domain/id
                     :row map? :user-id ::domain/user-id)
        :ret  nat-int?)

(defn update*
  "This is meant to be partialed which is why db doesn't appear first.."
  [table where db id row user-id]
  (sql/update! db table (dissoc row :id) [where id] user-id))

;;----------------------------------------------------------------------
(s/fdef delete*
        :args (s/cat :table keyword? :where string? :db some?
                     :id ::domain/id)
        :ret  nat-int?)

(defn delete*
  "This is meant to be partialed which is why db doesn't appear first.."
  [table where db id]
  (first (sql/delete! db table [where id])))


;;----------------------------------------------------------------------
;; User
;;----------------------------------------------------------------------
(def insert-user              (partial insert* user-tbl))
(def update-user-by-id        (partial update* user-tbl "id = ?"))
(def delete-user-by-id        (partial delete* user-tbl "id = ?"))

(def ^:private default-user-params
  {:id-nil? true
   :id nil})

(defn- select-user*
  [db params]
  (->> (merge default-user-params params)
       (select-user db)))

(defn select-user-by-id
  [db id]
  (select-user* db {:id id :id-nil? false}))

;;----------------------------------------------------------------------
;; Channel
;;----------------------------------------------------------------------
(def insert-channel              (partial insert* :channel))
(def update-channel-by-id        (partial update* :channel "id = ?"))
(def delete-channel-by-id        (partial delete* :channel "id = ?"))

(defn insert-channels
  [db channels user-id]
  (sql/insert-multi-with-audits! db :channel channels user-id))

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

(defn select-channels*
  [db params]
  (->> (merge default-channel-params params)
       (select-channels db)))

(defn select-channels-by-user-id
  "Select channels by user-id"
  [db user-id]
  (select-channels* db {:user-id-nil? false :user-id user-id}))

(defn select-channel-by-id
  [db id]
  (first (select-channels* db {:id-nil? false :id id})))

(defn select-channel-by-type
  "Select channel by the channel type and identifier."
  [db channel-type-id identifier]
  (let [params {:channel-type-id-nil? false :channel-type-id channel-type-id
                :identifier-nil? false :identifier identifier}
        rs (select-channels* db params)]
    (assert (<= (count rs) 1)
            (format "Expected at most 1 row for channel-type %s, identifier %s" channel-type-id identifier))
    (first rs)))

(defn select-channels-by-identifier
  [db identifier]
  (select-channels* db {:identifier-nil? false :identifier identifier}))

(defn select-channel-for-user-auth
  [db identifier token]
  (let [params {:identifier-nil? false :identifier identifier
                :token-nil? false :token token
                :token-expiration-nil? false :token-expiration (t/now)}
        chans (select-channels* db params)]
    (assert (<=  (count chans) 1) "Expected at most 1 chan to match.")
    (first chans)))

(defn select-channel-by-token
  [db token]
  (let [chans (select-channels* db {:token-nil? false :token token})]
    (assert (<= (count chans) 1) "Expected at most 1 chan to match.")
    (first chans)))

(defn select-channel-by-active-token
  [db token]
  (let [params {:token-nil? false :token token
                :token-expiration-nil? false :token-expiration (t/now)}
        chans (select-channels* db params)]
    (assert (<= (count chans) 1) "Expected at most 1 chan to match.")
    (first chans)))


;;----------------------------------------------------------------------
;; Address
;;----------------------------------------------------------------------
(def insert-address              (partial insert* :address))
(def update-address-by-id        (partial update* :address "id = ?"))
(def delete-address-by-id        (partial delete* :address "id = ?"))

(defn insert-user-address
  [db user-id address-id creator-id]
  (:id (sql/insert-with-create-audits! db :user-address {:user-id user-id
                                                         :address-id address-id} creator-id)))

(def ^:private default-address-params
  {:id-nil? true :id nil})

(defn- select-address*
  [db params]
  (select-address db (merge default-address-params params)))

(defn select-address-by-id
  [db id]
  (first (select-address* db {:id-nil? false :id id})))


;;----------------------------------------------------------------------
;; Roles
;;----------------------------------------------------------------------
(def insert-role              (partial insert* :role))
(def update-role-by-id        (partial update* :role "id = ?"))
(def delete-role-by-id        (partial delete* :role "id = ?"))

(def ^:private default-role-params
  {:id-nil? true :id nil
   :name-nil? true :name nil})

(defn select-role*
  [db params]
  (->> params
       (merge default-role-params)
       (select-role db)
       (map #(update-in % [:name] keyword))))

(defn select-role-by-id
  [db role-id]
  (first (select-role* db {:id-nil? false :id role-id})))

(defn select-role-by-name
  [db role-name]
  (first (select-role* db {:name-nil? false :name role-name})))

(defn select-all-roles
  [db]
  (select-role* db {}))

;;----------------------------------------------------------------------
;; Permissions
;;----------------------------------------------------------------------
(def insert-permission              (partial insert* :permission))
(def update-permission-by-id        (partial update* :permission "id = ?"))
(def delete-permission-by-id        (partial delete* :permission "id = ?"))

(def ^:private default-permission-params
  {:id-nil? true :id nil
   :name-nil? true :name nil})

(defn select-permission*
  [db params]
  (->> params
       (merge default-permission-params)
       (select-permission db)
       (map #(update % :name keyword))))

(defn select-all-permissions
  [db]
  (select-permission* db {}))

(defn select-permission-by-id
  [db permission-id]
  (first (select-permission* db {:id-nil? false :id permission-id})))

(defn select-permission-by-name
  [db permission-name]
  (first (select-permission* db {:name-nil? false :name permission-name})))

(defn- select-permissions-by-roles*
  [db params]
  (->> (select-permissions-by-roles db params)
       (map #(update % :name keyword))))

(defn select-permissions-by-role-ids
  "role-ids are ints"
  [db role-ids]
  (if (seq role-ids)
    (select-permissions-by-roles* db {:role-ids role-ids})
    []))

(defn select-roles-by-permission-ids
  "permission-ids are ints"
  [db permission-ids]
  (->> {:permission-ids permission-ids}
       (select-roles-by-permissions db)
       (map #(update % :name keyword))))


;;----------------------------------------------------------------------
;; User Roles
;;----------------------------------------------------------------------
(defn insert-user-roles
  [db user-id role-ids creator-id]
  (let [xs (map #(hash-map :role-id % :user-id user-id) role-ids)]
    (sql/insert-multi-with-create-audits! db :user-role xs creator-id)))

(defn delete-user-roles
  [db user-id role-ids]
  (jdbc/with-db-transaction [txn db]
    (doseq [role-id role-ids]
      (sql/delete! txn :user-role ["user_id = ? and role_id = ?" user-id role-id]))))


(def ^:private default-user-role-params
  {:user-id-nil? true :user-id nil})

(defn- select-user-roles*
  [db params]
  (->> (merge default-user-role-params params)
       (select-user-roles db)
       (map #(update % :name keyword))))

(defn select-user-roles-by-user-id
  [db user-id]
  (select-user-roles* db {:user-id-nil? false :user-id user-id}))


(def ^:private default-user-permission-params
  {:user-id-nil? true :user-id nil})

(defn- select-user-permissions*
  [db params]
  (->> (merge default-user-permission-params params)
       (select-user-permissions db)
       (map #(update % :name keyword))))

(defn select-user-permissions-by-user-id
  [db user-id]
  (select-user-permissions* db {:user-id-nil? false :user-id user-id}))

(defn select-user-ids-with-role-ids
  [db role-ids]
  (assert (seq role-ids) "Must specify at least one role id.")
  (->> (select-users-with-roles db {:role-ids role-ids})
       (map :user-id)
       set))


;;----------------------------------------------------------------------
;; Role Permissions
;;----------------------------------------------------------------------
(defn insert-role-permissions
  [db role-id permission-ids creator-id]
  (let [xs (map #(hash-map :permission-id % :role-id role-id) permission-ids)]
    (sql/insert-multi-with-create-audits! db :role-permission xs creator-id)))

(def delete-role-permissions-by-role-id       (partial delete* :role-permission "role_id = ?"))
(def delete-role-permissions-by-permission-id (partial delete* :role-permission "permission_id = ?"))

(defn delete-role-permissions
  [db role-id permission-ids]
  (let [xs (map #(hash-map :permission-id % :role-id role-id) permission-ids)]
    (jdbc/with-db-transaction [txn db]
      (doseq [permission-id permission-ids]
        (sql/delete! txn :role-permission ["role_id = ? and permission_id = ?" role-id permission-id])))))
