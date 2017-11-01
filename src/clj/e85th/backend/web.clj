(ns e85th.backend.web
  (:require [e85th.commons.ex :as ex]
            [e85th.commons.ext :as ext]
            [ring.util.response :as response]))

(defn ok
  "200 OK (Success)
  OK"
  ([] (ok nil))
  ([body]
   {:status 200
    :headers {}
    :body body}))

(defn found
  "302 Found (Redirection)
  The resource was found but at a different URI."
  ([url]
   {:status 302
    :headers {"Location" url}
    :body ""}))

(defn text-response
  "Returns a ring response with content-type set to text/plain."
  [body]
  (-> body
      ok
      (response/content-type "text/plain")))

(defn html-response
  "Returns a ring response with content-type set to text/html."
  [body]
  (-> body
      ok
      (response/content-type "text/html")))

(defn user-agent
  "Answers with the user-agent otherwise returns unk or optionally specify not-found."
  ([request]
   (user-agent "unk"))
  ([request not-found]
   (get-in request [:headers "user-agent"] not-found)))

(defn request-host
  "Answers with the user-agent otherwise returns unk or optionally specify not-found."
  [request]
  (get-in request [:headers "host"]))

(defn request-server-name
  "Answers with the user-agent otherwise returns unk or optionally specify not-found."
  [request]
  (get-in request [:server-name]))

(defn raw-request
  "Answers with a request that is free of extraneus keys."
  [request]
  ;; identity is something buddy-auth uses, :server-exchange is undertow
  (dissoc request :identity :server-exchange :async-channel))

(defn cookie-value
  "Extracts the cookie's value otherwise returns nil"
  [request cookie-name]
  (get-in request [:cookies (name cookie-name) :value]))

(defn set-cookie
  "Handles dissocing domain and secure when domain is localhost."
  [response cookie-name cookie-value {:keys [domain] :as opts}]
  (let [opts (cond-> opts
               (= domain "localhost") (dissoc opts :domain :secure))]
    (response/set-cookie response cookie-name cookie-value opts)))
