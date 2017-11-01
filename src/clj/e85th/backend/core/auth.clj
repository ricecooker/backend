(ns e85th.backend.core.auth
  (:require [e85th.backend.core.user :as user]
            [e85th.backend.core.db :as db]
            [e85th.backend.core.channel :as channel]
            [e85th.backend.core.domain :as domain]
            [e85th.commons.sms :as sms]
            [e85th.commons.ex :as ex]
            [e85th.commons.ext :as ext]
            [buddy.hashers :as hashers]
            [clojure.java.jdbc :as jdbc]
            [clj-time.core :as t]
            [e85th.commons.sql :as sql]
            [taoensso.timbre :as log]
            [e85th.commons.token :as token]
            [e85th.commons.email :as email]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(def ^:const channel-auth-failed-rr :auth.error/channel-auth-failed)


;;----------------------------------------------------------------------
(s/fdef send-mobile-token
        :args (s/cat :res map? :req ::domain/one-time-pass-request :user-id ::domain/user-id)
        :ret boolean?)

(defn send-mobile-token
  "Send a mobile token to the user with mobile-nbr. Store in db to verify.
   Throws an exception if the mobile channel doesn't exist for the identifier."
  [{:keys [sms] :as res} req user-id]
  (let [{mobile-nbr :identifier} req
        sms-msg {:to-nbr mobile-nbr :body (:message-body req)}
        {channel-id :id} (channel/get-by-mobile! res mobile-nbr)
        channel-update (select-keys req [:token :token-expiration])]
    (channel/update-by-id res channel-id channel-update user-id)
    ;; text the token to the user
    (sms/send sms sms-msg)
    true))


(defn user->token
  [{:keys [token-factory]} user]
  (assert token-factory)
  (->> (select-keys user [:id :first-name :last-name])
       (token/data->token token-factory)))

(defn user-id->token
  [res user-id]
  (user->token res (user/get-by-id! res user-id)))

(defn token->user
  "Inverse of user->token. Otherwise throws an AuthExceptionInfo."
  [{:keys [token-factory]} token]
  (assert token-factory)
  (token/token->data! token-factory token))

;;----------------------------------------------------------------------
(s/fdef via-token
        :args (s/cat :res map? :identifier string? :token string?)
        :ret  ::domain/user-id)

(defn via-token
  "Check the identifier and token combination is not yet expired. Answers with the user-id.
   Throws exceptions if unexpired identifier and token combo is not found."
  [{:keys [db] :as res} identifier token]
  (let [{:keys [id user-id verified-at] :as chan} (db/select-channel-for-user-auth db identifier token)]
    (when-not chan
      (throw (ex/auth channel-auth-failed-rr "Auth failed.")))
    (let [channel-update (cond-> {:token nil :token-expiration nil}
                           (not verified-at) (assoc :verified-at (t/now)))]
      (channel/update-by-id res id channel-update user-id)
      user-id)))

;;----------------------------------------------------------------------
(s/fdef password-matches?
        :args (s/cat :res map? :user-id ::domain/user-id ::password string?)
        :ret  boolean?)

(defn password-matches?
  [res user-id password]
  (let [{:keys [password-digest]} (user/get-all-fields-by-id res user-id)]
    (and (ext/not-blank? password-digest)
         (ext/not-blank? password)
         (hashers/check password password-digest))))

(defn via-password
  [res email password]
  (let [user-id (some->> (channel/get-by-email res email) :user-id)]
    (if (and user-id
             (password-matches? res user-id password))
      user-id
      (throw (ex/auth :user/invalid-password "Invalid password.")))))

;;----------------------------------------------------------------------
(s/fdef reset-password
        :args (s/cat :res map? :token string? :password string?)
        :ret  ::domain/user-id)

(defn reset-password
  "Resets the password for the user with the unexpired token. Returns the user-id for the user whose password was reset."
  [{:keys [db] :as res} token password]
  (let [{:keys [id user-id token-expiration verified-at]} (db/select-channel-by-token db token)
        err (cond
              (not token-expiration) channel/token-invalid-err
              (t/before? token-expiration (t/now)) channel/token-expired-err)]

    (when err
      (throw (ex/validation err)))

    (jdbc/with-db-transaction [txn db]
      (let [res (assoc res :db txn)
            chan-data (cond-> {:token nil :token-expiration nil}
                        (not verified-at) (assoc :verified-at (t/now)))]
        (user/set-password res user-id password user-id)
        (channel/update-by-id res id chan-data user-id)))
    user-id))


;;----------------------------------------------------------------------
(s/fdef change-password-by-id
        :args (s/cat :res map? :user-id ::domain/user-id :current-password string? :new-password string?)
        :ret  any?)

(defn change-password-by-id
  "Change the password for the user specified by user-id. Validate that
  `current-password` matches what's in the database and only then
  sets `new-password` as the password. Throws and auth exception if
  `current-password` does not match what is in the DB."
  [res user-id current-password new-password]
  (if (password-matches? res user-id current-password)
    (user/set-password res user-id new-password user-id)
    (throw (ex/auth :user/invalid-password "Invalid password."))))
