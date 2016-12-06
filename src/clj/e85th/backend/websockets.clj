(ns e85th.backend.websockets
  (:require [taoensso.sente :as sente]
            [com.stuartsierra.component :as component]
            [ring.util.http-response :as http-response]
            [taoensso.timbre :as log]
            [schema.core :as s])
  (:import [clojure.lang IFn]))

(defmulti event-handler :id)

(defmethod event-handler :chsk/ws-ping
  [msg]
  (log/debug "ping received"))

(defmethod event-handler :default
  [{:keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (log/infof "Unhandled event: %s, id: %s, ?data: %s" event id ?data)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-server event}))))

(defprotocol IWebSocket
  (notify! [this user-ids msg])
  (broadcast! [this msg])
  (do-get [this req])
  (do-post [this req]))

;; event-router is the stop function
(defrecord SenteWebSocket [sente-server-adapter req->user-id]
  component/Lifecycle
  (start [this]
    (let [{:keys [ch-recv] :as ch-sock-info} (sente/make-channel-socket! sente-server-adapter {:user-id-fn req->user-id})]
      (-> this
          (assoc :ch-sock-info ch-sock-info)
          (assoc :event-router (sente/start-chsk-router! ch-recv event-handler)))))

  (stop [this]
    (when-let [event-router (:event-router this)]
      (log/infof ";; Stopping Sente websocket router.")
      (event-router))
    (dissoc this :event-router :ch-sock-info))

  IWebSocket
  (notify! [this user-ids msg]
    (when-let [send-fn (get-in this [:ch-sock-info :send-fn])]
      (doseq [uid (remove nil? user-ids)]
        (send-fn uid msg))))

  (broadcast! [this msg]
    (log/debugf "broadcast msg: %s" msg)
    (when-let [connected-uids @(get-in this [:ch-sock-info :connected-uids])]
      (log/debugf "connected-uids: %s" connected-uids)
      (notify! this (:any connected-uids) msg)))

  (do-get [this req]
    (let [f (get-in this [:ch-sock-info :ajax-get-or-ws-handshake-fn])]
      (f req)))

  (do-post [this req]
    (let [f (get-in this [:ch-sock-info :ajax-post-fn])]
      (f req))))

(s/defn new-sente-websocket
  "sente-server-adapter ie instance of taoensso.sente.interfaces.IServerChanAdapter"
  [sente-server-adapter req->user-id :- IFn]
  (map->SenteWebSocket {:sente-server-adapter sente-server-adapter
                        :req->user-id req->user-id}))


(defrecord NilWebSocket []
  component/Lifecycle
  (start [this])
  (stop [this])

  IWebSocket
  (notify! [this user-ids msg])
  (broadcast! [this msg])

  (do-get [this req]
    (http-response/ok {}))
  (do-post [this req]
    (http-response/ok {})))

(defn new-nil-websocket
  []
  (map->NilWebSocket {}))
