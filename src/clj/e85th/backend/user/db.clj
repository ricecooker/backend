(ns e85th.backend.user.db
  (:require [hugsql.core :refer [def-db-fns]]
            [clojure.java.jdbc :as jdbc]
            [schema.core :as s]
            [clj-time.core :as t]
            [taoensso.timbre :as log]
            [e85th.commons.sql :as sql]
            [e85th.backend.user.models :as m]))

(def-db-fns "sql/user.sql")

(def ^{:private true
       :doc "Escaped keyword for ease of use"} user-tbl (keyword "\"user\""))

(s/defn insert-user :- s/Int
  "Insert the user record and return the id for the row"
  [db new-user :- m/NewUser user-id :- s/Int]
  (:id (sql/insert! db user-tbl new-user user-id)))

(s/defn insert-channel :- s/Int
  [db channel :- m/Channel user-id]
  (:id (sql/insert! db :channel channel user-id)))

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
