(ns e85th.backend.user.user-db
  (:require [hugsql.core :refer [def-db-fns]]
            [clojure.java.jdbc :as jdbc]
            [schema.core :as s]
            [clj-time.core :as t]
            [taoensso.timbre :as log]
            [e85th.commons.sql :as sql]))

(def-db-fns "sql/user.sql")

(def default-channel-params
  {:user-id-nil? :user-id nil
   :channel-type-id-nil? true :channel-type-id nil
   :identifier-nil? true :identifier nil
   :token-nil? true :token nil
   :token-expiration-nil? true :token-expiration nil
   :verified-at-nil? true :verified-at nil})

(s/defn select-channels-by-user
  "Select channels by user-id"
  [db user-id :- s/Int]
  (->> {:user-id-nil? false :user-id user-id}
       (merge default-channel-params)
       (select-channels db)))
