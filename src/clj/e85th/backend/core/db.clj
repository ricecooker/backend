(ns e85th.backend.core.db
  (:require [hugsql.core :refer [def-db-fns]]
            [clojure.java.jdbc :as jdbc]
            [schema.core :as s]
            [clj-time.core :as t]
            [taoensso.timbre :as log]
            [e85th.commons.sql :as sql]
            [e85th.backend.core.models :as m]))

(def-db-fns "sql/e85th/backend/core.sql")

(def ^{:private true
       :doc "Escaped keyword for ease of use"}
  user-tbl (keyword "\"user\""))

(s/defn insert-user :- s/Int
  "Insert the user record and return the id for the row"
  [db new-user :- m/CreateUser user-id :- s/Int]
  (:id (sql/insert! db user-tbl new-user user-id)))

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

(s/defn select-channels-by-user-id :- [m/Channel]
  "Select channels by user-id"
  [db user-id :- s/Int]
  (->> {:user-id-nil? false :user-id user-id}
       (merge default-channel-params)
       (select-channels db)))

(s/defn select-channel-by-id :- (s/maybe m/Channel)
  [db id :- s/Int]
  (->> {:id-nil? false :id id}
       (merge default-channel-params)
       (select-channels db)
       first))

(s/defn select-channel-by-type :- (s/maybe m/Channel)
  "Select channel by the channel type and identifier."
  [db channel-type-id :- s/Int identifier :- s/Str]
  (let [rs (->> {:channel-type-id-nil? false :channel-type-id channel-type-id
                 :identifier-nil? false :identifier identifier}
                (merge default-channel-params)
                (select-channels db))]
    (assert (<= (count rs) 1)
            (format "Expected at most 1 row for channel-type %s, identifier %s" channel-type-id identifier))
    (first rs)))

(s/defn select-channel-for-user-auth :- (s/maybe m/Channel)
  [db identifier :- s/Str token :- s/Str]
  (let [chans (->> {:identifier-nil? false :identifier identifier
                    :token-nil? false :token token
                    :token-expiration-nil? false :token-expiration (t/now)}
                   (merge default-channel-params)
                   (select-channels db))]
    (assert (<=  (count chans) 1) "Expected at most 1 chan to match.")
    (first chans)))

(s/defn insert-address :- s/Int
  [db address :- m/NewAddress creator-id :- s/Int]
  (:id (sql/insert! db :address address creator-id)))

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

(def ^:private default-permission-params
  {:id-nil? true :id nil
   :name-nil? true :name nil})

(s/defn select-permission*
  [db params]
  (->> params
       (merge default-permission-params)
       (select-permission db)
       (map #(update-in % [:name] keyword))))

(s/defn select-permission-by-id :- (s/maybe m/Permission)
  [db permission-id :- s/Int]
  (first (select-permission* db {:id-nil? false :id permission-id})))

(s/defn select-permission-by-name :- (s/maybe m/Permission)
  [db permission-name :- s/Str]
  (first (select-permission* db {:name-nil? false :name permission-name})))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Address
(def ^:private default-address-params
  {:id-nil? true :id nil
   :user-id-nil? true :user-id nil})

(defn- select-address*
  [db params]
  (select-address db (merge default-address-params params)))

(s/defn select-address-by-id :- (s/maybe m/Address)
  [db id :- s/Int]
  (first (select-address* db {:id-nil? false :id id})))

(s/defn select-address-by-user-id :- [m/Address]
  [db user-id :- s/Int]
  (select-address* db {:user-id-nil? false :user-id user-id}))
