(ns e85th.backend.components
  (:require [com.stuartsierra.component :as component]
            [e85th.commons.components :as commons-comp]
            [immutant.web :as immutant]
            [clojure.tools.nrepl.server :as nrepl]
            [taoensso.timbre :as log]
            [schema.core :as s])
  (:import [clojure.lang IFn]))


(defrecord ImmutantWebServer [server-opts make-handler app]
  component/Lifecycle
  (start [this]
    (log/infof "Starting Immutant Web Server")
    (assert app "The app dependency has not been set. Does your system have :app?")
    (assoc this :server (immutant/run (make-handler app) server-opts)))

  (stop [this]
    (log/infof "Stopping Immutant Web Server.")
    (some-> this :server immutant/stop)
    this))

(s/defschema WebServerOpts
  {:port s/Int
   (s/optional-key :host) s/Str})

(s/defn new-web-server
  "Creates a new web server instance.  make-handler-fn is a 1 arity function which
   is invoked to create the ring handler function, and is passed the app dependency."
  [server-opts :- WebServerOpts make-handler :- IFn]
  (let [params {:server-opts (merge {:host "0.0.0.0"} server-opts)
                :make-handler make-handler}]
    (component/using (map->ImmutantWebServer params) [:app])))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; nRepl server
(defrecord ReplServer [bind port]
  component/Lifecycle
  (start [this]
    (if (and bind port)
      (do
        (log/infof "Starting nRepl server on %s:%s" bind port)
        (assoc this :server (nrepl/start-server :bind bind :port port)))
      (do
        (log/info "Skipping nRepl server initialization.  Both bind and port must be specified.")
        this)))

  (stop [this]
    (log/infof "Stopping nRepl server.")
    (some-> this :server nrepl/stop-server)
    (assoc this :server nil)))

(s/defschema ReplServerOpts
  {:port s/Int
   (s/optional-key :bind) s/Str})

(s/defn new-repl-server
  "Create a new nrepl server running on host and port"
  [repl-opts :- ReplServerOpts]
  (let [params (merge {:bind "0.0.0.0"} repl-opts)]
    (map->ReplServer params)))
