(ns e85th.backend.core.models
  (:require [schema.core :as s]
            [e85th.commons.email :as email]
            [e85th.commons.tel :as tel]
            [e85th.commons.util :as u]
            [clojure.string :as str])
  (:import [org.joda.time DateTime]))

(def ^{:doc "mobile phone channel"}
  mobile-channel-type-id 1)

(def ^{:doc "email channel"}
  email-channel-type-id 2)

(def ^{:doc "Answers if the input integer is an email channel type"}
  email-channel-type-id? (partial = email-channel-type-id))

(def ^{:doc "Answers if the associative data input is an email channel"}
  email-channel? (comp email-channel-type-id? :channel-type-id))


(s/defschema User
  {:id s/Int
   :first-name s/Str
   :last-name s/Str})

(s/defschema UserInfo
  (assoc User :roles #{s/Keyword}))

(s/defschema UserAllFields
  ^{:doc "All fields including password digest"}
  (merge User {:password-digest (s/maybe s/Str)}))


(s/defschema Channel
  {:id s/Int
   :user-id s/Int
   :channel-type-id s/Int
   :channel-type-name s/Str
   :identifier s/Str
   :token (s/maybe s/Str)
   :token-expiration (s/maybe DateTime)
   :verified-at (s/maybe DateTime)})

(s/defschema NewChannel
  (select-keys Channel [:user-id :channel-type-id :identifier]))

(s/defschema UpdateChannel
  (-> Channel
      (dissoc :id :channel-type-name)
      u/make-all-keys-optional))

(s/defschema OneTimePassRequest
  {:token s/Str
   :token-expiration DateTime
   :identifier s/Str
   :message-body s/Str})

(s/defschema Address
  {(s/optional-key :id) s/Int
   :street-1 (s/maybe s/Str)
   :street-2 (s/maybe s/Str)
   :city s/Str
   :state s/Str
   :postal-code (s/maybe s/Str)
   :lat (s/maybe s/Num)
   :lng (s/maybe s/Num)})

(s/defschema UpdateAddress
  (u/make-all-keys-optional Address))

(s/defschema UserSave
  {(s/optional-key :first-name) s/Str
   (s/optional-key :last-name) s/Str
   (s/optional-key :password-digest) (s/maybe s/Str)})

(s/defschema ChannelIdentifier
  {:channel-type-id s/Int
   :identifier s/Str})

(s/defschema NewUser
  {:first-name s/Str
   :last-name s/Str
   :channels [ChannelIdentifier]
   :roles #{s/Int}
   (s/optional-key :password) s/Str
   (s/optional-key :address) Address})

(s/defschema UpdateUser
  {(s/optional-key :first-name) s/Str
   (s/optional-key :last-name) s/Str
   (s/optional-key :password) s/Str})

(s/defschema UserAuth
  {:roles #{s/Keyword}
   :permissions #{s/Keyword}})


(s/defschema Role
  {:id s/Int
   :name s/Keyword
   :description s/Str})

(s/defschema Permission
  {:id s/Int
   :name s/Keyword
   :description s/Str})

(s/defschema AuthRequest
  {:identifier s/Str
   :token s/Str})

(s/defschema AuthResponse
  {:user User
   :roles #{s/Keyword}
   :token s/Str})


(s/defschema WithPasswordAuth
  {:email s/Str
   :password s/Str})

(s/defschema WithFirebaseAuth
  {:token s/Str})

(s/defschema WithTokenAuth
  {:identifier s/Str
   :token s/Str})

(s/defschema WithGoogleAuth
  {:token s/Str})

(s/defschema UserAuthRequest
  {(s/optional-key :with-firebase) WithFirebaseAuth
   (s/optional-key :with-google) WithGoogleAuth
   (s/optional-key :with-token) WithTokenAuth
   (s/optional-key :with-password) WithPasswordAuth})


(s/defschema ChannelPersistResult
  [(s/one s/Keyword "status")
   (s/one (s/maybe Channel) "channel")])


(def channel-type->normalizer
  {email-channel-type-id email/normalize
   mobile-channel-type-id tel/normalize})

(defn normalize-identifier
  [{:keys [channel-type-id identifier] :as ch}]
  (if (str/blank? identifier)
    ch
    (let [f (get channel-type->normalizer channel-type-id identity)]
      (update ch :identifier f))))
