(ns e85th.backend.middleware
  (:require [taoensso.timbre :as log]
            [clj-time.coerce :as tc]
            [ring.middleware.cors :as cors]
            [ring.middleware.cookies :as cookies]
            [ring.util.request :as request-utils]
            [ring.util.http-response :as http-response]
            [schema.coerce :as coerce]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [ring.swagger.coerce :as rsc]
            [e85th.backend.web :as web]
            [e85th.commons.util :as u]
            [e85th.commons.ex :as ex]
            [compojure.api.middleware :as compojure-api-mw])
  (:import [e85th.commons.exceptions InvalidDataException NotFoundException]))

(def coercion-matchers
  (merge compojure.api.middleware/default-coercion-matchers
         {:body u/schema-string-coercion-matcher
          :string u/schema-string-coercion-matcher}))

(defn error-actions
  "Logs errors, notify via airbrake if airbrake-key is present and returns a 500 response. "
  [^Throwable t data request]
  (let [uuid (u/uuid)]
    (u/log-throwable t uuid)
    (http-response/internal-server-error {:error (str "Unexpected server error. " uuid)})))


(defn wrap-log-request
  [f context-str]
  (fn [{:keys [uri request-method] :as request}]
    (log/infof "%s %s %s" context-str request-method uri)
    ;(log/infof "request is: %s" request)
    (let [{:keys [status body] :as response} (f request)]
      (log/infof "%s %s %s %s" context-str request-method uri status)
      ;(println "status: " status " body: " (slurp body))
      (cond
        (#{400 401 403 422} status) (log/warnf "%s Request: %s, Response: %s" status (web/raw-request request) body)
        (= 500 status) (log/errorf "Request: %s, Response: %s" (web/raw-request request) body))
      response)))

(defn wrap-cors
  [handler]
  (cors/wrap-cors handler
                  :access-control-allow-origin [#".*"]
                  :access-control-allow-methods [:get :put :post :delete]))

(defn wrap-api-key-in-header
  [f]
  (fn [request]
    (let [{:keys [params]} request
          api-key (get-in request [:params "api_key"])
          request (cond-> request
                    api-key (assoc-in [:headers "authorization"] (str "Bearer " api-key)))]
      (f request))))

(defn wrap-swagger-remove-content-length
  "Total hack to get swagger ui to work.  Undertow seems to be sending wrong content lengths for some reason."
  [handler]
  (fn [{:keys [uri request-method] :as request}]
    (let [response (handler request)]
      (if (and (= :get request-method)
               (or (= uri "/lib/jquery-1.8.0.min.js")
                   (= uri "/swagger-ui.js")))
        (update-in response [:headers] dissoc "Content-Length")
        response))))

(defn wrap-api-exception-handling
  "API exception handling also logs request disposition. on-ex-fn is a fn that takes the request and the exception.
  on-ex-fn is called during what would result in a 500 server error."
  ([f]
   (wrap-api-exception-handling f (fn [_ _])))
  ([f on-ex-fn]
   (fn [{:keys [uri request-method] :as req}]
     (try
       (let [{:keys [status] :as resp} (f req)]
         ;; resp will be nil for 404s (no such route)
         (log/infof "%s %s %s" request-method uri (or status ""))
         resp)
       (catch e85th.commons.exceptions.ValidationExceptionInfo ex
         (let [[error-code :as error-tuple] (ex/error-tuple ex)]
           (log/infof "%s %s 422 %s" request-method uri error-code)
           (http-response/unprocessable-entity {:errors [error-tuple]})))
       (catch e85th.commons.exceptions.AuthExceptionInfo ex
         (log/infof "%s %s 401" request-method uri)
         (http-response/unauthorized {:errors [(ex/error-tuple ex)]}))
       (catch clojure.lang.ExceptionInfo ex
         (log/debug ex)
         (let [{:keys [type error] :as data} (ex-data ex) ;; compojure api exceptions
               [ex-type ex-msg data] (ex/error-tuple ex)
               ;; normalize to have just one type, errors
               ex-type (or type ex-type)
               error-tuple (ex/error-tuple ex-type (or error ex-msg) data)
               errors {:errors [error-tuple]}]
           (condp = ex-type
             :compojure.api.exception/request-validation (do
                                                           (log/infof "%s %s 400" request-method uri)
                                                           (log/warnf "req %s, message: %s" (web/raw-request req) (.getMessage ex))
                                                           (compojure.api.exception/request-validation-handler ex data req))
             ex/not-found (do
                            (log/infof "%s %s 404" request-method uri)
                            (http-response/not-found errors))
             ;; nothing matched, rethrow
             (do
               (on-ex-fn req ex)
               (throw ex)))))
       (catch Exception ex
         (let [uuid (u/uuid)
               error-tuple (ex/error-tuple :http/internal-server-error (str "Unexpected server error. " uuid) {:uuid uuid})]
           (u/log-throwable ex uuid)
           (log/errorf "req %s, message: %s" (web/raw-request req) (.getMessage ex))
           (on-ex-fn req ex)
           (http-response/internal-server-error {:errors [error-tuple]})))))))

(defn wrap-site-exception-handling
  "Exception handling for websites, also logs request disposition.
   login-page is where the redirect happens if AuthExceptionInfo is raised.
   error-page-fn is a function that takes a request and the exception."
  [f login-page error-page-fn]
  (fn [{:keys [uri request-method] :as req}]
    (try
      (let [{:keys [status] :as resp} (f req)]
        ;; resp will be nil for 404s (no such route)
        (log/infof "%s %s %s" request-method uri (or status ""))
        resp)
      (catch e85th.commons.exceptions.AuthExceptionInfo ex
        (log/infof "%s %s 401" request-method uri)
        (http-response/see-other login-page))
      (catch clojure.lang.ExceptionInfo ex
        (log/debug ex)
        (let [{:keys [type error] :as data} (ex-data ex) ;; compojure api exceptions
              [ex-type ex-msg] (ex/type+msg ex)
              ;; normalize to have just one type, errors
              ex-type (or type ex-type)
              errors {:errors [[ex-type (or error ex-msg)]]}]
          (condp = ex-type
            :compojure.api.exception/request-validation (do
                                                          (log/infof "%s %s 400" request-method uri)
                                                          (log/warnf "req %s, message: %s" (web/raw-request req) (.getMessage ex))
                                                          (compojure.api.exception/request-validation-handler ex data req))
            ex/not-found (do
                           (log/infof "%s %s 404" request-method uri)
                           (http-response/not-found errors))
            ;; nothing matched, rethrow
            (do
              (error-page-fn req ex)
              (throw ex)))))
      (catch Exception ex
        (let [uuid (u/uuid)]
          (u/log-throwable ex uuid)
          (log/errorf "req %s, message: %s" (web/raw-request req) (.getMessage ex))
                                        ;(http-response/internal-server-error {:errors [(str "Unexpected server error. " uuid)]})
          (http-response/internal-server-error (error-page-fn req ex)))))))


;; -- For UI purposes
(def components-key :compojure.api.middleware/components)

(defn wrap-cookie-value-in-components
  "Adds a res-key to resources that are available in all routes.  Then res-key can be used to access the cookie value.
   Useful for hanlding cookies that are tokens which need to be used to make api calls etc.
   Usage (wrap-cookie-value-in-components handler :res :auth-token :bearer).  In this case,
   the :auth-token cookie's value will be added to the component's under the :bearer."
  [f resources-name cookie-name res-key]
  (let [cookie-name (cond
                      (keyword? cookie-name) (name cookie-name)
                      (string? cookie-name) cookie-name
                      :else (throw (ex-info "Unknown cookie-name type." {:cookie-name cookie-name})))]
    (fn [request]
      (let [v (web/cookie-value request cookie-name)
            request' (update-in request [components-key resources-name] assoc res-key v)]
        (f request')))))
