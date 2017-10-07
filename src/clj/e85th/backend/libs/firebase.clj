(ns e85th.backend.libs.firebase
  "Firebase integration for auth. Must call init! before using other functions."
  (:require [schema.core :as s]
            [clojure.core.match :refer [match]]
            [clojure.java.io :as io])
  (:import [com.google.firebase FirebaseApp FirebaseOptions$Builder]
           [com.google.firebase.auth FirebaseAuth FirebaseToken]
           [com.google.firebase.tasks OnFailureListener OnSuccessListener]
           [clojure.lang IFn]))

(s/defschema Token
  {:uid s/Str
   :name s/Str
   :email (s/maybe s/Str)
   :picture s/Str
   :email-verified? s/Bool
   :issuer s/Str})

(s/defn token->map :- Token
  [token :- FirebaseToken]
  {:name (.getName token)
   :email (.getEmail token)
   :picture (.getPicture token)
   :email-verified? (.isEmailVerified token)
   :issuer (.getIssuer token)
   :uid (.getUid token)})

(s/defn ^:private make-firebase-options
  [service-account-file :- s/Str database-url :- s/Str]
  (-> (doto (FirebaseOptions$Builder.)
        (.setServiceAccount (io/input-stream service-account-file))
        (.setDatabaseUrl database-url))
      .build))

(s/defn init!
  "Inits the default FirebaseApp."
  [service-account-file :- s/Str database-url :- s/Str]
  (-> (make-firebase-options service-account-file database-url)
      FirebaseApp/initializeApp))

(s/defn inited?
  []
  (some? (seq (FirebaseApp/getApps))))

(s/defn async-verify-id-token
  "Verifies the token. Calls on-success with the FirebaseToken or
   on-error with the FirebaseAuthException instance."
  [jwt :- s/Str on-success :- IFn on-error :- IFn]
  (-> (FirebaseAuth/getInstance)
      (.verifyIdToken jwt); returns a Task to which listeners are added
      (.addOnSuccessListener (reify OnSuccessListener
                               (onSuccess [this token]
                                 (on-success token))))
      (.addOnFailureListener (reify OnFailureListener
                               (onFailure [this ex]
                                 (on-error ex))))))

(s/defn verify-id-token
  "Verifies the token. Returns a tuple [true user-data] or
  [false FirebaseAuthException]."
  [jwt :- s/Str]
  (let [p (promise)]
    (async-verify-id-token jwt #(deliver p [true (token->map %)]) #(deliver p [false %]))
    @p))

(s/defn verify-id-token!
  "Same as verify-id-token but throws an exception if auth fails.
   Answers with a map of keys."
  ([jwt :- s/Str]
   (verify-id-token! jwt #(throw %)))
  ([jwt :- s/Str on-error-fn :- IFn]
   (match (verify-id-token jwt)
     [true user-data] user-data
     [false ex] (on-error-fn ex))))
