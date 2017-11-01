(ns e85th.backend.interceptors
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.content-negotiation :as conneg]
            [io.pedestal.http.body-params :as body-params]
            [cheshire.core :as json]
            [e85th.backend.web :as backend-web]
            [e85th.commons.token :as token]
            [e85th.commons.transit-io :as transit-io]
            [e85th.commons.edn-io :as edn-io]
            [e85th.commons.ext :as ext]
            [e85th.commons.util :as e85th.util]
            [e85th.backend.core.user :as backend.user]
            [e85th.backend.web :as web]
            [taoensso.timbre :as log]))

(def supported-types ["application/edn" "application/json" "application/transit+json"])

(def content-negotiator (conneg/negotiate-content supported-types))

(defn accepted-type
  [context]
  (get-in context [:request :accept :field] "application/edn"))

(defn transform-content
  [body content-type]
  (case content-type
    "application/edn"  (pr-str body)
    "application/transit+json" (transit-io/encode body)
    "application/json" (json/generate-string body)))

(defn coerce-to
  [response content-type]
  (-> response
      (update :body transform-content content-type)
      (assoc-in [:headers "Content-Type"] content-type)))

(def coerce-body
  {:name ::coerce-body
   :leave
   (fn [context]
     (cond-> context
       (nil? (get-in context [:response :body :headers "Content-Type"]))
       (update-in [:response] coerce-to (accepted-type context))))})

; context is a map, ex is an ex-info exception
(def ex-handler
  {:name ::ex-handler
   :error (fn [ctx ex]
            (let [uuid (ext/random-uuid)]
              (e85th.util/log-throwable ex uuid)
              (assoc ctx :response {:status 500
                                    :body {:message "Unexpected server error."
                                           :error-id uuid}})))})

(defn make-components-injector
  [res]
  {:name ::inject-components
   :enter (fn [context]
            (assoc context ::components res))})

(def merge-params
  {:name ::merge-params
   :enter (fn [{:keys [request] :as ctx}]
            (update ctx :request (fn [{:keys [json-params edn-params transit-params form-params query-params] :as request}]
                                   (assoc request :params
                                          (merge json-params edn-params transit-params form-params query-params)))))})



(def extract-components ::components)
