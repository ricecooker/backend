(ns e85th.backend.core.google-oauth
  "Google OAuth."
  (:require [schema.core :as s]
            [clojure.set :as set])
  (:import [com.google.api.client.googleapis.auth.oauth2 GoogleIdToken GoogleIdToken$Payload GoogleIdTokenVerifier GoogleIdTokenVerifier$Builder]
           [com.google.api.client.json.gson GsonFactory]
           [com.google.api.client.http.javanet NetHttpTransport]))

(defonce ^:private client-id (atom nil))

(s/defn init!
  "Sets the oauth client id to use."
  [oauth-client-id :- (s/maybe s/Str)]
  (reset! client-id oauth-client-id))

(s/defn inited?
  []
  (some? @client-id))

(s/defschema Token
  {:email s/Str
   :email-verified? s/Bool
   :name (s/maybe s/Str)
   :picture-url (s/maybe s/Str)
   :locale (s/maybe s/Str)
   :family-name (s/maybe s/Str)
   :given-name (s/maybe s/Str)})

(s/defn token->map :- Token
  [^GoogleIdToken t]
  (let [p (.getPayload t)]
    {:email (.getEmail p)
     :email-verified? (.getEmailVerified p)
     :name (.get p "name")
     :picture-url (.get p "picture")
     :locale (.get p "locale")
     :family-name (.get p "family_name")
     :given-name (.get p "given_name")}))

(s/defn verify-token :- Token
  "Takes a google oauth token obtained from the client and verifies access on the server.
   oauth-client-id is obtained from the google developer console."
  ([token :- s/Str]
   (assert @client-id "init! has not been called. Otherwise use the 2-airty call.")
   (verify-token @client-id token))
  ([oauth-client-id :- s/Str token :- s/Str]
   (-> (GoogleIdTokenVerifier$Builder. (NetHttpTransport.) (GsonFactory/getDefaultInstance))
       (.setAudience [oauth-client-id])
       (.build)
       (.verify token)
       token->map)))
