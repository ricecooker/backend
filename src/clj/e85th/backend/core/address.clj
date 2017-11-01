(ns e85th.backend.core.address
  (:require [e85th.backend.core.db :as db]
            [e85th.commons.ex :as ex]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [e85th.backend.core.domain :as domain]
            [clojure.string :as str]))

;;----------------------------------------------------------------------
(s/fdef get-by-id
        :args (s/cat :res map? :id ::domain/id)
        :ret  (s/nilable ::domain/address))

(defn get-by-id
  "Gets an address by its id."
  [{:keys [db]} id]
  (db/select-address-by-id db id))

(def get-by-id! (ex/wrap-not-found get-by-id))

;;----------------------------------------------------------------------
(s/fdef create
        :args (s/cat :res map? :address ::domain/address :user-id ::domain/user-id)
        :ret  ::domain/id)

(defn create
  "Create a new address record. Returns the address ID."
  [{:keys [db] :as res} address user-id]
  (db/insert-address db address user-id))

;;----------------------------------------------------------------------
(s/fdef update-by-id
        :args (s/cat :res map? :id ::domain/id :address ::domain/address :user-id ::domain/user-id)
        :ret  int?)

(defn update-by-id
  "Updates the address by the db id and returns the count of rows updated."
  [{:keys [db] :as res} id address user-id]
  (db/update-address-by-id db id address user-id))

;;----------------------------------------------------------------------
(s/fdef same?
        :args (s/cat :address-1 ::domain/address :address-2 ::domain/address)
        :ret  boolean?)

(defn same?
  "Address equality based on street-number, street, unit, city, state and postal-code."
  [address-1 address-2]
  (let [norm #(str/lower-case (str/trim (or % "")))
        str-eq? #(= (norm %1) (norm %2))
        prop-eq? (fn [k]
                   (str-eq? (k address-1) (k address-2)))]
    (every? prop-eq? [:street-number :street :unit :city :state :postal-code])))


;;----------------------------------------------------------------------
(s/fdef save-by-id
        :args (s/cat :res map? :address-id ::domain/id :new-address ::domain/address :user-id ::domain/user-id)
        :ret  ::domain/id)

(defn save-by-id
  "Creates a new address and returns the address id when address-id is nil or
   new address is different from the address referenced by address-id based on same?.
   Otherwise returns the current address id."
  [res address-id new-address user-id]
  (let [db-address (get-by-id! res address-id)
        db-id (:id db-address)
        address-same? (and db-address (same? db-address new-address))]

    (if address-same?
      db-id
      (create res new-address user-id))))

(s/fdef assoc-user
        :args (s/cat :res map? :address ::domain/address :user-id ::domain/user-id :creator-id ::domain/user-id))

(defn assoc-user
  "Create an address and assoc it to the user."
  [{:keys [db] :as res} address user-id creator-id]
  (let [address-id (:id (create res address creator-id))]
    (db/insert-user-address db user-id address-id creator-id)))
