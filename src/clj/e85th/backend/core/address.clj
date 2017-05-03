(ns e85th.backend.core.address
  (:require [e85th.backend.core.db :as db]
            [e85th.backend.core.models :as m]
            [e85th.commons.ex :as ex]
            [e85th.commons.util :as u]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [schema.core :as s]
            [clojure.string :as str]))

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
  [{:keys [db] :as res} address :- m/Address user-id :- s/Int]
  (->> (db/insert-address db address user-id)
       (find-address-by-id res)))

(s/defn update-address :- m/Address
  [{:keys [db] :as res} address-id :- s/Int address :- m/UpdateAddress user-id :- s/Int]
  (db/update-address db address user-id)
  (find-address-by-id res address-id))


(s/defn same?
  "Address equality based on street-1, street-2, city, state and postal-code."
  [address-1 address-2]
  (let [norm #(str/lower-case (str/trim (or % "")))
        str-eq? #(= (norm %1) (norm %2))
        prop-eq? (fn [k]
                   (str-eq? (k address-1) (k address-2)))]
    (every? prop-eq? [:street-1 :street-2 :city :state :postal-code])))


(s/defn save
  "Creates a new address and returns the address id when address-id is nil or
   new address is different from the address referenced by address-id based on same?.
   Otherwise returns the current address id."
  [res address-id :- (s/maybe s/Int) new-address user-id]
  (let [db-address (some->> address-id (find-address-by-id res))
        address-same? (and db-address (same? db-address new-address))]
    (if address-same?
      (:id db-address)
      (:id (create-address res new-address user-id)))))
