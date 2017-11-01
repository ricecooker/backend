(ns e85th.backend.components
  (:require [com.stuartsierra.component :as component]
            [clojure.spec.alpha :as s]
            [io.pedestal.http :as http]
            [clojure.tools.nrepl.server :as nrepl]
            [taoensso.timbre :as log]))


(s/def ::port nat-int?)
(s/def ::host string?)
(s/def ::bind string?)

(s/def ::repl-server-opts (s/keys :req-un [::port]
                                  :opt-un [::bind]))

(defrecord Pedestal [service-map]
  component/Lifecycle
  (start [this]
    ;(log/debug "Starting Pedestal Service.")
    (if (:service this)
      this
      (assoc this :service
             (-> service-map
                 http/create-server
                 http/start))))
  (stop [this]
    ;(log/debug "Stopping Pedestal Service.")
    (some-> this :service http/stop)
    (dissoc this :service)))

(defn new-pedestal
  [service-map]
  (map->Pedestal {:service-map service-map}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; nRepl server
(defrecord ReplServer [bind port]
  component/Lifecycle
  (start [this]
    (if (and bind port)
      (do
        ;(log/debugf "Starting nRepl server on %s:%s" bind port)
        (assoc this :server (nrepl/start-server :bind bind :port port)))
      (do
        ;(log/debugf "Skipping nRepl server initialization.  Both bind and port must be specified.")
        this)))

  (stop [this]
    ;(log/debug "Stopping nRepl server.")
    (some-> this :server nrepl/stop-server)
    (assoc this :server nil)))

;;----------------------------------------------------------------------
(s/fdef new-repl-server
        :args (s/cat :repl-opts ::repl-server-opts)
        :ret any?)

(defn new-repl-server
  "Create a new nrepl server running on host and port"
  [repl-opts]
  (let [params (merge {:bind "0.0.0.0"} repl-opts)]
    (map->ReplServer params)))
