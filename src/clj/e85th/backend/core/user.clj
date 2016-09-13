(ns e85th.backend.core.user
  (:require [e85th.backend.core.db :as db]
            [e85th.backend.core.models :as m]
            [e85th.commons.sms :as sms]
            [e85th.commons.tel :as tel]
            [e85th.commons.ex :as ex]
            [e85th.commons.util :as u]
            [buddy.hashers :as hashers]
            [clj-time.core :as t]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [schema.core :as s]
            [e85th.commons.token :as token]))

(def duplicate-channel-ex ::duplicate-channel-ex)
(def password-required-ex ::password-required-ex)
(def channel-auth-failed-ex ::channel-auth-failed-ex)

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
  [{:keys [db]} user-id :- s/Int]
  (db/select-channels-by-user-id db user-id))

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
  (find-channel-by-type res m/email-channel-type-id email))

(def find-email-channel! (ex/wrap-not-found find-email-channel))

(s/defn create-channel :- m/Channel
  "Creates a new channel and returns the Channel record."
  [{:keys [db] :as res} channel :- m/NewChannel user-id :- s/Int]
  (->> (db/insert-channel db channel user-id)
       (find-channel-by-id res)))

(s/defn update-channel :- m/Channel
  "Update the channel attributes and return the updated Channel record."
  [{:keys [db] :as res} channel-id :- s/Int channel :- m/UpdateChannel user-id :- s/Int]
  (db/update-channel db channel-id channel user-id)
  (find-channel-by-id! res channel-id))

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
    (throw (ex/new-validation-exception duplicate-channel-ex (format "Identifier %s already exists." identifier)))))

(s/defn validate-new-user
  [res {:keys [channel-type-id password] :as new-user} :- m/NewUser]
  (validate-channel-identifiers res new-user)
  (when (= m/email-channel-type-id channel-type-id)
    (when-not (seq password)
      (throw (ex/new-validation-exception password-required-ex "Passowrd is required for e-mail signup.")))))

(s/defn ^:private new-user->user :- m/CreateUser
  "User record with password-digest."
  [{:keys [password] :as new-user}]
  (-> new-user
      (select-keys [:first-name :last-name])
      (assoc :password-digest (some-> password hashers/derive))))

(defn- new-user->channels
  "NewUser to map required for inserting into channel."
  [new-user user-id]
  (-> new-user
      (select-keys [:channel-type-id :identifier])
      (assoc :user-id user-id)))

(s/defn ^:private create-user-address
  [txn user-id :- s/Int address :- m/NewAddress creator-id :- s/Int]
  (let [address-id (db/insert-address txn address creator-id)]
    (db/insert-user-address txn user-id address-id creator-id)))

(s/defn create-new-user :- m/User
  "Creates a new user from a channel."
  [{:keys [db] :as res} {:keys [channels roles] :as new-user} :- m/NewUser creator-id :- s/Int]
  (validate-new-user res new-user)
  (let [user-record (new-user->user new-user)
        user-id (volatile! nil)]
    (jdbc/with-db-transaction [txn db]
      (vreset! user-id (db/insert-user txn user-record creator-id))
      (db/insert-channels txn (map #(assoc % :user-id @user-id) channels) creator-id)
      (db/insert-user-roles txn @user-id roles creator-id)
      (when-let [address (:address new-user)]
        (create-user-address txn @user-id address creator-id)))

    (find-user-by-id res @user-id)))

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

(s/defn ^:private auth-with-token :- m/User
  "Check the mobile-nbr and token combination is not yet expired. Answers with the user-id.
   Throws exceptions if token decrypt fails or user does not exist."
  [{:keys [db] :as res} identifier :- s/Str token :- s/Str]
  (let [{:keys [id user-id verified-at] :as chan} (db/select-channel-for-user-auth db identifier token)]
    (when-not chan
      (throw (ex/new-auth-exception channel-auth-failed-ex "Auth failed.")))
    (let [channel-update (cond-> {:token nil :token-expiration nil}
                           (not verified-at) (assoc :verified-at (t/now)))]
      (update-channel res id channel-update user-id)
      (find-user-by-id! res user-id))))

(s/defn authenticate-via-token :- m/AuthResponse
  "Authenticate a user, if identifier and token combination are not valid, then throws AuthExceptionInfo."
  [res identifier :- s/Str token :- s/Str]
  (let [user (auth-with-token res identifier token)]
    {:user user
     :roles (find-user-roles res (:id user))
     :token (user->token res user)}))
