(ns e85th.backend.core.address
  (:require [e85th.backend.core.db :as db]
            [e85th.backend.core.models :as m]
            [e85th.commons.ex :as ex]
            [e85th.commons.util :as u]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [schema.core :as s]))

(s/defn find-addresses-by-ids :- [m/Address]
  "Find address by the address-ids"
  [{:keys [db]} address-ids :- [s/Int]]
  (db/select-addresses-by-ids db address-ids))

(s/defn find-address-by-id :- (s/maybe m/Address)
  "Find an addres by id."
  [res address-id :- s/Int]
  (first (find-addresses-by-ids res [address-id])))

(def find-address-by-id! (ex/wrap-not-found find-address-by-id))

(s/defn create-address :- m/Address
  [{:keys [db] :as res} address :- m/NewAddress user-id :- s/Int]
  (->> (db/insert-address db address user-id)
       (find-address-by-id res)))

(s/defn update-address :- m/Address
  [{:keys [db] :as res} address-id :- s/Int address :- m/UpdateAddress user-id :- s/Int]
  (db/update-address db address user-id)
  (find-address-by-id res address-id))
