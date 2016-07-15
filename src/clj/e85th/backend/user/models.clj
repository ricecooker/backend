(ns e85th.backend.user.models
  (:require [schema.core :as s])
  (:import [org.joda.time DateTime]))

(def ^{:doc "mobile phone channel"}
  mobile-channel-id 1)

(def ^{:doc "email channel"}
  email-channel-id 2)

(s/defschema User
  {:id s/Int
   :first-name s/Str
   :last-name s/Str})

(s/defschema UserAllFields
  ^{:doc "All fields including password digest"}
  (merge User {:password-digest (s/maybe s/Str)}))

(s/defschema NewUser
  {:first-name s/Str
   :last-name s/Str
   (s/optional-key :password-digest) s/Str})

(s/defschema Channel
  {:id s/Int
   :user-id s/Int
   :channel-type-id s/Int
   :identifier s/Str
   :token (s/maybe s/Str)
   :token-expiration (s/maybe DateTime)
   :verified-at (s/maybe DateTime)})

(s/defschema NewChannel
  {:user-id s/Int
   :channel-type-id s/Int
   :identifier s/Str})
