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
            [compojure.api.middleware :as compojure-api-mw]))

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
    (let [{:keys [status body] :as response} (f request)]
      (log/infof "%s %s %s %s" context-str request-method uri status)
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
                    api-key (assoc-in [:headers "authorization"] (str "Authorization " api-key)))]
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
