(ns e85th.backend.web
  (:require [ring.util.http-response :as http-response]
            [schema.core :as s]))

(defn text-response
  "Returns a ring response with content-type set to text/plain."
  [body]
  (-> body
      http-response/ok
      (http-response/content-type "text/plain")))

(s/defn user-agent :- s/Str
  "Answers with the user-agent otherwise returns unk or optionally specify not-found."
  ([request]
   (user-agent "unk"))
  ([request not-found]
   (get-in request [:headers "user-agent"] not-found)))

(defn raw-request
  "Answers with a request that is free of extraneus keys."
  [request]
  ;; identity is something buddy-auth uses, :server-exchange is undertow
  (dissoc request :identity :server-exchange :compojure.api.middleware/components :compojure.api.middleware/options :ring.swagger.middleware/data))

(s/defn cookie-value :- (s/maybe s/Str)
  "Extracts the cookie's value otherwise returns nil"
  [request cookie-name]
  (get-in request [:cookies cookie-name :value]))
