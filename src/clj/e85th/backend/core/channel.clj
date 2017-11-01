(ns e85th.backend.core.channel
  (:refer-clojure :exclude [ensure])
  (:require [clojure.spec.alpha :as s]
            [e85th.commons.ex :as ex]
            [e85th.commons.ext :as ext]
            [e85th.commons.tel :as tel]
            [e85th.commons.email :as email]
            [e85th.backend.core.db :as db]
            [clj-time.core :as t]
            [clojure.core.match :refer [match]]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [e85th.backend.core.domain :as domain]
            [clojure.set :as set]))

(def ^:const channel-in-use-err       :channel.error/in-use)
(def ^:const duplicate-channel-err    :channel.error/duplicate-channel)
(def ^:const token-expired-err        :channel.error/token-expired)
(def ^:const token-invalid-err        :channel.error/token-invalid)

(def ^:const mobile-type "mobile")
(def ^:const email-type "email")

(def ^:const email-type-id  #uuid "4e3c5337-7d7d-4398-bfa7-c4e17bbffa21")
(def ^:const mobile-type-id #uuid "b9e260b7-72ce-4b38-9689-9f45266eff43")

(def email-channel?
  "Answers if the associative data input is an email channel"
  (ext/key= :channel-type-id email-type-id))

(def mobile-channel?
  "Answers if the associative data input is an mobile channel"
  (ext/key= :channel-type-id mobile-type-id))

(def channel-type->normalizer
  {email-type email/normalize
   mobile-type tel/normalize})

(defn normalize-identifier
  [{:keys [channel-type-id identifier] :as ch}]
  (if (str/blank? identifier)
    ch
    (let [f (get channel-type->normalizer channel-type-id identity)]
      (update ch :identifier f))))

;;----------------------------------------------------------------------
(s/fdef get-by-id
        :args (s/cat :res map? :id ::domain/id)
        :ret  (s/nilable ::domain/channel))

(defn get-by-id
  [{:keys [db]} id]
  (db/select-channel-by-id db id))

(def get-by-id! (ex/wrap-not-found get-by-id))

;;----------------------------------------------------------------------
(s/fdef get-by-user-id
        :args (s/cat :res map? :user-id ::domain/user-id :ch-type-id (s/? ::domain/id))
        :ret  (s/coll-of ::domain/channel))

(defn get-by-user-id
  "Enumerates all channels by the user-id."
  ([{:keys [db]} user-id]
   (db/select-channels-by-user-id db user-id))
  ([res user-id ch-type-id]
   (->> (get-by-user-id res user-id)
        (filter (ext/key= :channel-type-id ch-type-id)))))

;;----------------------------------------------------------------------
(s/fdef get-by-identifier
        :args (s/cat :res map? :identifier ::domain/identifier)
        :ret  (s/coll-of ::domain/channel))

(defn get-by-identifier
  "Enumerate all channels matching the identifier."
  [{:keys [db]} identifier]
  (db/select-channels-by-identifier db identifier))


;;----------------------------------------------------------------------
(s/fdef get-by-type
        :args (s/cat :res map? :channel-type-id ::domain/id :identifier ::domain/identifier)
        :ret  (s/nilable ::domain/channel))

(defn get-by-type
  [{:keys [db]} channel-type-id identifier]
  (db/select-channel-by-type db channel-type-id identifier))

(def get-by-type! (ex/wrap-not-found get-by-type))


;;----------------------------------------------------------------------
(s/fdef get-by-mobile
        :args (s/cat :res map? :mobile-nbr ::domain/identifier)
        :ret  (s/nilable ::domain/channel))

(defn get-by-mobile
  "Finds an mobile channel for the given mobile phone number."
  [res mobile-nbr]
  (get-by-type res mobile-type-id (tel/normalize mobile-nbr)))

(def get-by-mobile! (ex/wrap-not-found get-by-mobile))


;;----------------------------------------------------------------------
(s/fdef get-by-email
        :args (s/cat :res map? :email ::domain/identifier)
        :ret  (s/nilable ::domain/channel))

(defn get-by-email
  "Finds an email channel for the given email address."
  [res email]
  (get-by-type res email-type-id (email/normalize email)))

(def get-by-email! (ex/wrap-not-found get-by-email))


;;----------------------------------------------------------------------
(s/fdef create
        :args (s/cat :res map? :channel ::domain/channel :user-id ::domain/user-id)
        :ret  ::domain/id)

(defn create
  "Creates a new channel and returns the Channel record."
  [{:keys [db] :as res} channel user-id]
  (db/insert-channel db (normalize-identifier channel) user-id))

;;----------------------------------------------------------------------
(s/fdef update-by-id
        :args (s/cat :res map? :id ::domain/id :channel map? :user-id ::domain/user-id)
        :ret  int?)

(defn update-by-id
  "Update the channel attributes and return the updated Channel record."
  [{:keys [db] :as res} id channel user-id]
  (db/update-channel-by-id db id (normalize-identifier channel) user-id))

;;----------------------------------------------------------------------
(s/fdef delete-by-id
        :args (s/cat :res map? :id ::domain/id)
        :ret any?)

(defn delete-by-id
  [{:keys [db]} id]
  (db/delete-channel-by-id db id))

;;----------------------------------------------------------------------
(s/fdef ensure
        :args (s/cat :res map? :new-chan ::domain/channel :modifier-user-id ::domain/user-id)
        :ret (s/tuple keyword? ::domain/channel))


(defn ensure
  "Ensures the channel exists for the user. Returns a variant [status ch]
   Status can be :ok if already exists,
   or ::channel-in-use if the channel already belongs to another user
   or :created when the new channel is created."
  [res {:keys [channel-type-id identifier user-id] :as new-chan} modifier-user-id]
  (if-let [ch (get-by-type res channel-type-id identifier)]
    (if (= user-id (:user-id ch))
      [:ok ch]
      [channel-in-use-err ch])
    [:created (create res new-chan modifier-user-id)]))

;;----------------------------------------------------------------------
(s/fdef used-by-another?
        :args (s/cat :res map? :channel-type-id ::domain/id :identifier ::domain/identifier :user-id ::domain/user-id)
        :ret boolean?)

(defn used-by-another?
  "Answers if channel is being used by another user."
  [res channel-type-id identifier user-id]
  (if-let [ch (get-by-type res channel-type-id identifier)]
    (not= (:user-id ch) user-id)
    false))

;;----------------------------------------------------------------------
(s/fdef persist
        :args (s/cat :res map? :new-data map? :clear-verified? boolean? :modifier-user-id ::domain/user-id)
        :ret (s/tuple keyword? any?))

(defn- persist
  "Either a variant [status channel] Status can be one of :no-action :created :removed :updated or channel-in-use-err
   channel can be nil when no-action is required in the degenerate case of persisting blank identifier
   when no channel exists for that channel-type.
   Expects there to be at most 1 channel for the type otherwise throws an exception."
  [{:keys [db] :as res} {:keys [user-id identifier channel-type-id] :as new-data} clear-verified? modifier-user-id]
  (if (and (seq identifier)
           (used-by-another? res channel-type-id identifier user-id))
    [channel-in-use-err nil]
    (let [chans (get-by-user-id res user-id channel-type-id)
          ch (select-keys  (first chans) [:id :user-id :channel-type-id :identifier :token :token-expiration :verified-at]) ;; FIXME: this should look at all channels not just the first one
          new-identifier (:identifier new-data)
          data (cond-> new-data
                 clear-verified? (assoc :verified-at nil))]

      (match [(count chans)
              (if (str/blank? new-identifier) :new-blank :new-some)
              (if (= new-identifier (:identifier ch)) :unchanged :changed)]
             [0 :new-blank _] [:no-action nil]
             [0 :new-some _] [:created (create res (-> new-data
                                                       (assoc :id (ext/random-uuid))
                                                       (dissoc :verified-at))
                                                   modifier-user-id)]
             [1 :new-blank _] [:removed (delete-by-id res (:id ch))]
             [1 :new-some :changed] [:updated (update-by-id res (:id ch) (merge ch new-data) modifier-user-id)]
             [1 :new-some :unchanged] [:no-action ch]
             :else (throw (ex-info "Expected at most 1 channel." {:data new-data
                                                                  :count (count chans)}))))))

;;----------------------------------------------------------------------
(s/fdef persist-email
        :args (s/cat :res map? :user-id ::domain/user-id :new-email (s/nilable string?)
                     :clear-verified? boolean? :modifier-user-id ::domain/user-id)
        :ret (s/tuple keyword? any?))

(defn persist-email
  "Updates in place the email associated with the user only if it differs.
   Expects there to only be one email channel otherwise throws an exception."
  [res user-id new-email clear-verified? modifier-user-id]
  (let [data {:user-id user-id
              :identifier (some-> new-email email/normalize)
              :channel-type-id email-type-id}]
    (persist res data clear-verified? modifier-user-id)))

;;----------------------------------------------------------------------
(s/fdef persist-mobile
        :args (s/cat :res map? :user-id ::domain/user-id :new-mobile (s/nilable string?)
                     :clear-verified? boolean? :modifier-user-id ::domain/user-id)
        :ret (s/tuple keyword? any?))

(defn persist-mobile
  "Updates in place the email associated with the user only if it differs.
   Expects there to only be one email channel otherwise throws an exception."
  [res user-id new-mobile clear-verified? modifier-user-id]
  (let [data {:user-id user-id
              :identifier (some-> new-mobile tel/normalize)
              :channel-type-id mobile-type-id}]
    (persist res data clear-verified? modifier-user-id)))

;;----------------------------------------------------------------------
(s/fdef filter-existing-identifiers
        :args (s/cat :res map? :channels (s/coll-of ::domain/channel-identifier))
        :ret (s/coll-of ::domain/channel-identifier))

(defn filter-existing-identifiers
  "Returns a subset of identifiers which already exist."
  [res channels]
  (let [chan-exists? (fn [{:keys [channel-type-id identifier]}]
                       (some? (get-by-type res channel-type-id identifier)))]
    (doall (filter chan-exists? channels))))


;;----------------------------------------------------------------------
(s/fdef validate-channel-identifiers
        :args (s/cat :res map? :channels ::domain/channel-identifiers)
        :ret nil?)

(defn validate-channel-identifiers
  "Validates that the channel identifier are not already present.
   Throws a validation exception when an identifier exists."
  [res channels]
  (when-let [{:keys [identifier]} (first (filter-existing-identifiers res channels))]
    (throw (ex/validation duplicate-channel-err (format "Identifier %s already exists." identifier)))))

;;----------------------------------------------------------------------
(s/fdef get-email-channels-by-user-id
        :args (s/cat :res map? :user-id ::domain/user-id)
        :ret (s/coll-of ::domain/channel))

(defn get-email-channels-by-user-id
  "Enumerates all email channels for the user."
  [{:keys [db]} user-id]
  (filter email-channel? (db/select-channels-by-user-id db user-id)))

;;----------------------------------------------------------------------
(s/fdef verify
        :args (s/cat :res map? :token ::domain/token)
        :ret any?)

(defn verify
  "Verify the channel if the token is valid."
  [{:keys [db] :as res} token]
  (let [{:keys [id user-id token-expiration verified-at] :as chan} (db/select-channel-by-token db token)
        err (cond
              (not token-expiration) token-invalid-err
              (t/before? token-expiration (t/now)) token-expired-err)]

    (when err
      (throw (ex/validation err)))

    (let [chan-data (cond-> {:token nil :token-expiration nil}
                      (not verified-at) (assoc :verified-at (t/now)))]
      (update-by-id res id chan-data user-id))))
