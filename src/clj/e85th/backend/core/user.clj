(ns e85th.backend.core.user
  (:require [e85th.backend.core.db :as db]
            [e85th.backend.core.models :as m]
            [e85th.backend.core.address :as address]
            [e85th.commons.sms :as sms]
            [e85th.commons.tel :as tel]
            [e85th.commons.ex :as ex]
            [e85th.commons.util :as u]
            [taoensso.timbre :as log]
            [buddy.hashers :as hashers]
            [e85th.backend.core.firebase :as firebase]
            [e85th.backend.core.google-oauth :as google-oauth]
            [clj-time.core :as t]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [schema.core :as s]
            [clojure.core.match :refer [match]]
            [e85th.commons.token :as token]
            [e85th.commons.email :as email]
            [clojure.string :as str]))

(def duplicate-channel-ex ::duplicate-channel-ex)
(def password-required-ex ::password-required-ex)
(def channel-auth-failed-ex ::channel-auth-failed-ex)
(def channel-verification-failed-ex ::channel-verification-failed-ex)
(def channel-in-use ::channel-in-use)

(s/defn find-user-all-fields-by-id :- (s/maybe m/UserAllFields)
  "This exists to make it a little bit harder to expose the password digest."
  [{:keys [db]} id :- s/Int]
  (db/select-user db {:id id}))

(def find-user-all-fields-by-id! (ex/wrap-not-found find-user-all-fields-by-id))

(s/defn find-user-by-id :- (s/maybe m/User)
  "This exists to make it a little bit harder to expose the password digest."
  [res id :- s/Int]
  (some-> (find-user-all-fields-by-id res id)
          (dissoc :password-digest)))

(def find-user-by-id! (ex/wrap-not-found find-user-by-id))

(s/defn find-channel-by-id :- (s/maybe m/Channel)
  [{:keys [db]} id :- s/Int]
  (db/select-channel-by-id db id))

(def find-channel-by-id! (ex/wrap-not-found find-channel-by-id))

(s/defn find-channels-by-user-id :- [m/Channel]
  "Enumerates all channels by the user-id."
  ([{:keys [db]} user-id :- s/Int]
   (db/select-channels-by-user-id db user-id))
  ([res user-id :- s/Int ch-type-id :- s/Int]
   (->> (find-channels-by-user-id res user-id)
        (filter (u/key= :channel-type-id ch-type-id)))))

(s/defn find-channels-by-identifier :- [m/Channel]
  "Enumerate all channels matching the identifier."
  [{:keys [db]} identifier :- s/Str]
  (db/select-channels-by-identifier db identifier))

(s/defn find-channel-by-type :- (s/maybe m/Channel)
  [{:keys [db]} channel-type-id :- s/Int identifier :- s/Str]
  (db/select-channel-by-type db channel-type-id identifier))

(def find-channel-by-type! (ex/wrap-not-found find-channel-by-type))

(s/defn find-mobile-channel :- (s/maybe m/Channel)
  "Finds an mobile channel for the given mobile phone number."
  [res mobile-nbr :- s/Str]
  (find-channel-by-type res m/mobile-channel-type-id (tel/normalize mobile-nbr)))

(def find-mobile-channel! (ex/wrap-not-found find-mobile-channel))

(s/defn find-email-channel :- (s/maybe m/Channel)
  "Finds an email channel for the given email address."
  [res email :- s/Str]
  (find-channel-by-type res m/email-channel-type-id (email/normalize email)))

(def find-email-channel! (ex/wrap-not-found find-email-channel))

(s/defn create-channel :- m/Channel
  "Creates a new channel and returns the Channel record."
  [{:keys [db] :as res} channel :- m/NewChannel user-id :- s/Int]
  (->> (db/insert-channel db (m/normalize-identifier channel) user-id)
       (find-channel-by-id res)))

(s/defn update-channel :- m/Channel
  "Update the channel attributes and return the updated Channel record."
  [{:keys [db] :as res} channel-id :- s/Int channel :- m/UpdateChannel user-id :- s/Int]
  (db/update-channel db channel-id (m/normalize-identifier channel) user-id)
  (find-channel-by-id! res channel-id))

(s/defn rm-channel
  [{:keys [db]} channel-id :- s/Int]
  (db/delete-channel db channel-id))

(s/defn ensure-channel
  "Ensures the channel exists for the user. Returns a variant [status ch]
   Status can be :ok if already exists,
   or ::channel-in-use if the channel already belongs to another user
   or :created when the new channel is created."
  [res
   {:keys [channel-type-id identifier user-id] :as new-chan} :- m/NewChannel
   modifier-user-id :- s/Int]
  (let [ch (find-channel-by-type res channel-type-id identifier)]
    (if ch
      (if (= user-id (:user-id ch))
        [:ok ch]
        [channel-in-use ch])
      [:created (create-channel res new-chan modifier-user-id)])))

(defn channel-used-by-another?
  "Answers if channel is being used by another user."
  [res channel-type-id identifier user-id]
  (if-let [ch (find-channel-by-type res channel-type-id identifier)]
    (not= (:user-id ch) user-id)
    false))

(defn persist-channel
  "Either a variant [status channel] Status can be one of :no-action :created :removed :updated or channel-in-use
   channel can be nil when no-action is required in the degenerate case of persisting blank identifier
   when no channel exists for that channel-type.
   Expects there to be at most 1 channel for the type otherwise throws an exception."
  [{:keys [db] :as res} {:keys [user-id identifier channel-type-id] :as new-data} clear-verified? modifier-user-id]
  (if (and (seq identifier)
           (channel-used-by-another? res channel-type-id identifier user-id))
    [channel-in-use nil]
    (let [chans (find-channels-by-user-id res user-id channel-type-id)
          ch (first chans)
          new-identifier (:identifier new-data)
          data (cond-> new-data
                 clear-verified? (assoc :verified-at nil))]

      (match [(count chans)
              (if (str/blank? new-identifier) :new-blank :new-some)
              (if (= new-identifier (:identifier ch)) :unchanged :changed)]
             [0 :new-blank _] [:no-action nil]
             [0 :new-some _] [:created (create-channel res (dissoc new-data :verified-at) modifier-user-id)]
             [1 :new-blank _] [:removed (rm-channel res (:id ch))]
             [1 :new-some :changed] [:updated (update-channel res (:id ch) new-data modifier-user-id)]
             [1 :new-some :unchanged] [:no-action ch]
             :else (throw (ex-info "Expected at most 1 channel." {:data new-data
                                                                  :count (count chans)}))))))

(s/defn persist-email :- m/ChannelPersistResult
  "Updates in place the email associated with the user only if it differs.
   Expects there to only be one email channel otherwise throws an exception."
  [res user-id :- s/Int new-email :- (s/maybe s/Str) clear-verified? :- s/Bool modifier-user-id :- s/Int]
  (let [data {:user-id user-id
              :identifier (some-> new-email email/normalize)
              :channel-type-id m/email-channel-type-id}]
    (persist-channel res data clear-verified? modifier-user-id)))

(s/defn persist-mobile :- m/ChannelPersistResult
  "Updates in place the email associated with the user only if it differs.
   Expects there to only be one email channel otherwise throws an exception."
  [res user-id :- s/Int new-mobile :- (s/maybe s/Str) clear-verified? :- s/Bool modifier-user-id :- s/Int]
  (let [data {:user-id user-id
              :identifier (some-> new-mobile tel/normalize)
              :channel-type-id m/mobile-channel-type-id}]
    (persist-channel res data clear-verified? modifier-user-id)))

;; -- New User
(s/defn filter-existing-identifiers :- [m/ChannelIdentifier]
  [res channels :- [m/ChannelIdentifier]]
  (let [chan-exists? (fn [{:keys [channel-type-id identifier]}]
                       (some? (find-channel-by-type res channel-type-id identifier)))]
    (doall (filter chan-exists? channels))))

(s/defn validate-channel-identifiers
  "Validates that the channel identifier are not already present.
   Throws a validation exception when an identifier exists."
  [res {:keys [channels]} :- m/NewUser]
  (when-let [{:keys [identifier]} (first (filter-existing-identifiers res channels))]
    (throw (ex/validation duplicate-channel-ex (format "Identifier %s already exists." identifier)))))

(s/defn validate-new-user
  [res {:keys [channel-type-id password] :as new-user} :- m/NewUser]
  (validate-channel-identifiers res new-user)
  (when (= m/email-channel-type-id channel-type-id)
    (when-not (seq password)
      (throw (ex/validation password-required-ex "Passowrd is required for e-mail signup.")))))

(s/defn ^:private user->user-save :- m/UserSave
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


(s/defn find-addresses-by-user-id :- [m/Address]
  "Find all addresses by the user-id."
  [{:keys [db] :as res} user-id :- s/Int]
  (->> (db/select-address-ids-by-user-id db user-id)
       (address/find-addresses-by-ids res)))

(s/defn ^:private create-user-address
  [{:keys [db] :as res} user-id :- s/Int address :- m/Address creator-id :- s/Int]
  (let [address-id (:id (address/create-address res address creator-id))]
    (db/insert-user-address db user-id address-id creator-id)))

(s/defn create-new-user :- m/User
  "Creates a new user from a channel."
  [{:keys [db] :as res} {:keys [channels roles] :as new-user} :- m/NewUser creator-id :- s/Int]
  (validate-new-user res new-user)
  (let [user-record (user->user-save new-user)
        user-id (volatile! nil)]
    (jdbc/with-db-transaction [txn db]
      (vreset! user-id (db/insert-user txn user-record creator-id))
      (db/insert-channels txn (map #(assoc % :user-id @user-id) channels) creator-id)
      (db/insert-user-roles txn @user-id roles creator-id)
      (when-let [address (:address new-user)]
        (create-user-address (assoc res :db txn) @user-id address creator-id)))

    (find-user-by-id res @user-id)))

(s/defn update-user
  [{:keys [db] :as res} user-id :- s/Int user :- m/UpdateUser updater-user-id]
  (let [user-record (user->user-save user)]
    (db/update-user db user-id user-record updater-user-id)))

(s/defn find-user-auth :- m/UserAuth
  "Finds roles and permissions for the user specified by user-id."
  [{:keys [db]} user-id :- s/Int]
  (let [rename-map {"role" :roles "permission" :permissions}
        xs (seq (db/select-user-auth-by-user-id db user-id))]
    (merge {:roles #{} :permissions #{}}
           (set/rename-keys (u/group-by+ :kind (comp keyword :name) set xs)
                            rename-map))))

(s/defn find-user-roles :- #{s/Keyword}
  "Look up all roles for a user."
  [res user-id :- s/Int]
  (:roles (find-user-auth res user-id)))

(def find-roles-by-user-id find-user-roles)

(s/defn find-user-ids-by-role-ids :- [s/Int]
  [{:keys [db]} role-ids :- [s/Int]]
  (db/select-users-by-roles db role-ids))


(s/defn send-mobile-token :- s/Bool
  "Send a mobile token to the user with mobile-nbr. Store in db to verify.
   Throws an exception if the mobile channel doesn't exist for the identifier."
  [{:keys [sms] :as res} req :- m/OneTimePassRequest user-id :- s/Int]
  (let [{mobile-nbr :identifier} req
        sms-msg {:to-nbr mobile-nbr :body (:message-body req)}
        {channel-id :id} (find-mobile-channel! res mobile-nbr)
        channel-update (select-keys req [:token :token-expiration])]
    (update-channel res channel-id channel-update user-id)
    ;; text the token to the user
    (sms/send sms sms-msg)
    true))

(s/defn user->token :- s/Str
  [{:keys [token-factory]} user :- m/User]
  (assert token-factory)
  (token/data->token token-factory user))

(s/defn user-id->token :- s/Str
  [res user-id :- s/Int]
  (user->token res (find-user-by-id! res user-id)))

(s/defn token->user :- m/User
  "Inverse of user->token. Otherwise throws an AuthExceptionInfo."
  [{:keys [token-factory]} token :- s/Str]
  (assert token-factory)
  (token/token->data! token-factory token))

(s/defn verify-channel :- m/Channel
  "Verify the channel if the token is valid. Returns the updated channel or throws an auth exception"
  [{:keys [db] :as res} token :- s/Str]
  (let [{:keys [id user-id verified-at] :as chan} (db/select-channel-by-token db token)]
    (when-not chan
      (throw (ex/auth channel-auth-failed-ex "Invalid token.")))
    (let [channel-update (cond-> {:token nil :token-expiration nil}
                           (not verified-at) (assoc :verified-at (t/now)))]
      (update-channel res id channel-update user-id))))

(s/defn ^:private auth-with-token :- s/Int
  "Check the identifier and token combination is not yet expired. Answers with the user-id.
   Throws exceptions if unexpired identifier and token combo is not found."
  [{:keys [db] :as res} identifier :- s/Str token :- s/Str]
  (let [{:keys [id user-id verified-at] :as chan} (db/select-channel-for-user-auth db identifier token)]
    (when-not chan
      (throw (ex/auth channel-auth-failed-ex "Auth failed.")))
    (let [channel-update (cond-> {:token nil :token-expiration nil}
                           (not verified-at) (assoc :verified-at (t/now)))]
      (update-channel res id channel-update user-id)
      user-id)))

(s/defn user->auth-response :- m/AuthResponse
  [res user :- m/User]
  {:user user
   :roles (find-user-roles res (:id user))
   :token (user->token res user)})

(s/defn user-id->auth-response :- m/AuthResponse
  [res user-id :- s/Int]
  (user->auth-response res (find-user-by-id! res user-id)))

(s/defn find-email-channels-by-user-id :- [m/Channel]
  "Enumerates all email channels for the user."
  [{:keys [db]} user-id :- s/Int]
  (filter m/email-channel? (db/select-channels-by-user-id db user-id)))

(s/defn find-user-info! :- m/UserInfo
  [res user-id :- s/Int]
  (-> (find-user-by-id! res user-id)
      (assoc :roles (find-user-roles res user-id))))

(s/defn add-user-roles
  [{:keys [db]} user-id :- s/Int role-ids :- [s/Int] editor-user-id :- s/Int]
  (db/insert-user-roles db user-id (set role-ids) editor-user-id))

(s/defn rm-user-roles
  [{:keys [db]} user-id :- s/Int role-ids :- [s/Int] editor-user-id :- s/Int]
  (db/delete-user-roles db user-id (set role-ids)))

(s/defn rm-user
  [{:keys [db]} user-id :- s/Int delete-user-id :- s/Int]
  (db/delete-user db user-id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Authenticate
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti authenticate
  (fn [res user-auth]
    (first (keys user-auth))))

(defmethod authenticate :with-firebase
  [res {:keys [with-firebase]}]
  (let [jwt (:token with-firebase)
        auth-ex-fn (fn [ex]
                     (throw
                      (ex/auth :firebase/auth-failed "Firebase Auth Failed" {} ex)))
        {:keys [email]} (firebase/verify-id-token! jwt auth-ex-fn)]

    (assert email "We don't handle anonymous logins a la firebase.")

    (if-let [{:keys [user-id]} (find-email-channel res email)]
      (user-id->auth-response res user-id)
      (throw (ex/auth :user/no-such-user "No such user.")))))

(defmethod authenticate :with-google
  [res {:keys [with-google]}]
  (let [jwt (:token with-google)
        auth-ex-fn (fn [ex]
                     (throw
                      (ex/auth :google/auth-failed "Google Auth Failed" {} ex)))
        {:keys [email]} (google-oauth/verify-token jwt)]
    (try
      (let [{:keys [email]} (google-oauth/verify-token jwt)]
        (assert email "We don't handle anonymous logins.")
        (if-let [{:keys [user-id]} (find-email-channel res email)]
          (user-id->auth-response res user-id)
          (throw (ex/auth :user/no-such-user "No such user."))))
      (catch Exception ex
        (throw
         (ex/auth :google/auth-failed "Google Auth Failed" {} ex))))))

(defmethod authenticate :with-token
  [res {:keys [with-token]}]
  (let [{:keys [identifier token]} with-token
        identifier (cond-> identifier
                     (email/valid? identifier) identity
                     (tel/valid? identifier) tel/normalize)
        user-id (auth-with-token res identifier token)]
    (user-id->auth-response res user-id)))

(defmethod authenticate :with-password
  [res {:keys [with-password]}]
  (let [{:keys [email password]} with-password
        {:keys [id password-digest]} (some->> (find-email-channel res email)
                                              :user-id
                                              (find-user-all-fields-by-id res))]

    (when-not (seq password-digest)
      (throw (ex/auth :user/invalid-password "Invalid password.")))

    (when-not (hashers/check password password-digest)
      (throw (ex/auth :user/invalid-password "Invalid password.")))

    (user-id->auth-response res id)))
