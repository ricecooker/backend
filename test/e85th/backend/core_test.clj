(ns e85th.backend.core-test
  (:require [e85th.test.http :as http]
            [e85th.commons.util :as u]
            [apis.common.conf :as conf]
            [apis.routes :as routes]
            [com.stuartsierra.component :as component]
            [apis.test-system :as test-system]
            [schema.core :as s]
            [clojure.test :refer :all]
            [taoensso.timbre :as log]))


(def config-file "./conf/test.edn")

(defonce system nil)

(defn init!
  "Call this first before using any other functions."
  []
  (u/set-utc-tz)
  (s/set-fn-validation! true)
  (let [sys-config (conf/read-config config-file :test)]
    (alter-var-root #'system (constantly (component/start (test-system/make sys-config))))
    (http/init! {:routes (routes/make-handler (:app system))})))


(defn add-admin-auth
  [request]
  (http/add-auth-header request auth-token-name (:admin-auth-token system)))

(def api-call http/api-call)

(def admin-api-call (http/make-api-caller add-admin-auth))

(defn with-system
  "Runs tests using the test system."
  [f]
  (when (not system)
    (println "Starting test system")
    (init!))
  (f))


(use-fixtures :once with-system)

(deftest ^:integration coercion-test
  (testing "get"
    (let [[status response] (api-call :get "/v1/coercion-test" {:int-field "42" :num-field "-99.99" :bool-field "true" :date-field "2016-12-31"})]
      (is (= 200 status))
      (is (= response {:int-field 42 :num-field -99.99 :bool-field true :date-field "2016-12-31T00:00:00.000Z"}))))

  (testing "post"
    (let [[status response] (api-call :post "/v1/coercion-test" {:int-field "42" :num-field "-99.99" :bool-field "true" :date-field "2016-12-31"})]
      (is (= 200 status))
      (is (= response {:int-field 42 :num-field -99.99 :bool-field true :date-field "2016-12-31T00:00:00.000Z"})))))
