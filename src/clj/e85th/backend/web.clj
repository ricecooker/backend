(ns e85th.backend.web
  (:require [ring.util.http-response :as http-response]
            [compojure.api.meta :as meta]
            ;[compojure.api.sweet :refer [defapi defroutes context GET POST ANY]]
            [e85th.commons.util :as u]
            [e85th.commons.ex :as ex]
            [ring.swagger.json-schema :as json-schema]
            [e85th.commons.geo] ; to load LatLng
            [schema.core :as s]))

(defmethod json-schema/convert-class e85th.commons.geo.LatLng [_ _] {:type "map"})

(defn text-response
  "Returns a ring response with content-type set to text/plain."
  [body]
  (-> body
      http-response/ok
      (http-response/content-type "text/plain")))

(defn html-response
  "Returns a ring response with content-type set to text/html."
  [body]
  (-> body
      http-response/ok
      (http-response/content-type "text/html")))

(s/defn user-agent :- s/Str
  "Answers with the user-agent otherwise returns unk or optionally specify not-found."
  ([request]
   (user-agent "unk"))
  ([request not-found]
   (get-in request [:headers "user-agent"] not-found)))

(s/defn request-host :- s/Str
  "Answers with the user-agent otherwise returns unk or optionally specify not-found."
  [request]
  (get-in request [:headers "host"]))

(s/defn request-server-name :- s/Str
  "Answers with the user-agent otherwise returns unk or optionally specify not-found."
  [request]
  (get-in request [:server-name]))

(defn raw-request
  "Answers with a request that is free of extraneus keys."
  [request]
  ;; identity is something buddy-auth uses, :server-exchange is undertow
  (dissoc request :identity :server-exchange :async-channel :compojure.api.middleware/components :compojure.api.middleware/options :ring.swagger.middleware/data))

(s/defn cookie-value :- (s/maybe s/Str)
  "Extracts the cookie's value otherwise returns nil"
  [request cookie-name]
  (get-in request [:cookies (name cookie-name) :value]))

(s/defn set-cookie
  "Handles dissocing domain and secure when domain is localhost."
  [response cookie-name cookie-value {:keys [domain] :as opts}]
  (let [opts (cond-> opts
               (= domain "localhost") (dissoc opts :domain :secure))]
    (apply http-response/set-cookie [response cookie-name cookie-value opts])))

;; See https://github.com/metosin/compojure-api/wiki/Creating-your-own-metadata-handlers
(defmethod meta/restructure-param :exists restructure-exists
  [_ [item expr error-msg] {:keys [body] :as acc}]
  (assert (symbol? item) "Please specify a symbol to bind the expression to for :exists.")
  (assert expr "Please specify an expression for :exists")
  (let [errors [(ex/error-tuple :http/not-found (or error-msg "Resource not found.") {})]]
    (-> acc
        (update-in [:letks] into [item expr])
        (assoc :body `((if ~item
                         (do ~@body)
                         (http-response/not-found {:errors ~errors})))))))


;; validate-expr should return ex/error-tuple(s)
(defmethod meta/restructure-param :validate restructure-validate
  [_ validate-expr {:keys [body] :as acc}]
  (-> acc
      (assoc :body `((let [errors# ~validate-expr]
                       (if (seq errors#)
                         (http-response/unprocessable-entity {:errors (u/as-coll errors#)})
                         (do ~@body)))))))


(defn wrap-authenticated
  "This is a ring middleware handler."
  [handler]
  (fn [request]
    (if (:identity request)
      (handler request)
      (http-response/unauthorized {:errors [(ex/error-tuple :http/unauthorized "Not authenticated." {})]}))))

;; Implementations must return a variant [authorized? msg]
(defmulti authorized? :auth-type)

;; allow permission and auth-fn to be nil
;; sometimes you don't care about permission, just that the user is logged in
(defmethod meta/restructure-param :auth restructure-auth
  [_ [user permission-or-auth-fn auth-fn] {:keys [body] :as acc}]
  (assert user "no user specified for :auth restructuring")
  (let [perm-or-auth? (some? permission-or-auth-fn)
        perm? (keyword? permission-or-auth-fn)
        auth-fn? (some? auth-fn)
        auth-params {:auth-type :standard
                     :user user
                     :required-permission (when perm? permission-or-auth-fn)
                     :auth-fn (if perm? auth-fn permission-or-auth-fn)
                     :request '+compojure-api-request+}]
    (when auth-fn?
      (assert perm-or-auth? "No permission specified as 2nd arg in :auth restructuring."))
    (-> acc
        (update-in [:middleware] conj wrap-authenticated)
        (update-in [:lets] into [user '(:identity +compojure-api-request+)])
        (assoc :body `((let [[authorized?# msg#] (authorized? ~auth-params)]
                         (if authorized?#
                           (do ~@body)
                           (http-response/forbidden {:errors [(ex/error-tuple :http/forbidden msg# {})]}))))))))


(comment
  (macroexpand-1
   `(GET "/admin" []
      (ok {:message "welcome!"})))

  (macroexpand-1
   `(GET "/admin" []
      :auth [foo-user jf]
      (ok {:message "welcome!"})))

  (macroexpand-1
   `(defn do-something
      [arg-1]
      (let [let-1 20]
        (* arg-1 let-1))))
  )
