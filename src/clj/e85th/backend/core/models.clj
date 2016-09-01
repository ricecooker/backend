(ns e85th.backend.core.models
  (:require [schema.core :as s]
            ;; load geo ns for access to LatLng class/defrecord
            [e85th.commons.geo])
  (:import [org.joda.time DateTime]
           [e85th.commons.geo LatLng]))

(def ^{:doc "mobile phone channel"}
  mobile-channel-type-id 1)

(def ^{:doc "email channel"}
  email-channel-type-id 2)

(s/defschema User
  {:id s/Int
   :first-name s/Str
   :last-name s/Str})

(s/defschema UserAllFields
  ^{:doc "All fields including password digest"}
  (merge User {:password-digest (s/maybe s/Str)}))

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

(s/defschema Address
  {:id s/Int
   :street-1 (s/maybe s/Str)
   :street-2 (s/maybe s/Str)
   :city s/Str
   :state s/Str
   :postal-code (s/maybe s/Str)
   :lat (s/maybe s/Num)
   :lng (s/maybe s/Num)})

(s/defschema NewAddress
  (dissoc Address :id))

(s/defschema CreateUser
  {:first-name s/Str
   :last-name s/Str
   :password-digest (s/maybe s/Str)})

(s/defschema NewUser
  {:first-name s/Str
   :last-name s/Str
   :channels [{:channel-type-id s/Int :identifier s/Str}]
   :roles #{s/Int}
   (s/optional-key :password) s/Str
   (s/optional-key :address) NewAddress})


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
