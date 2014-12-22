(ns dulcet.handler
  (:use [org.httpkit.server :only [run-server]])
  (:require [dulcet.dev :refer [browser-repl start-figwheel]]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found resources]]
            [compojure.handler]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [clojure.core.async :as async :refer (<! <!! >! >!! put! chan go go-loop)]
            [ring.middleware.reload :as reload]
            [selmer.parser :refer [render-file]]
            [environ.core :refer [env]]
            [prone.middleware :refer [wrap-exceptions]]
            [ring.middleware.anti-forgery :as ring-anti-forgery]
            [taoensso.timbre    :as timbre]
            [taoensso.sente :as sente]             
            ;;[taoensso.sente.packers.transit :as sente-transit]
            ))


(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
              connected-uids]}
      (sente/make-channel-socket! {})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
  )

(def packer
  "Defines our packing (serialization) format for client<->server comms."
  :edn ; Default
  ;;(sente-transit/get-flexi-packer :edn) ; Experimental, needs Transit deps
  )

#_(go (while true
      (println "Got" (<! ch-chsk))
      ))

(defroutes routes
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post                req))
  (GET "/" [] (render-file "templates/index.html" {:dev (env :dev?)}))
  (resources "/")
  (not-found "Not Found"))

(defn- logf [fmt & xs] (println (apply format fmt xs)))

(defmulti event-msg-handler :id) ; Dispatch on event-id
;; Wrap for logging, catching, etc.:
(defn     event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (logf "Event: %s" event)
  (event-msg-handler ev-msg))

(do ; Server-side methods
  (defmethod event-msg-handler :default ; Fallback
    [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
    (let [session (:session ring-req)
          uid     (:uid     session)]
      (logf "Unhandled event: %s" event)
      (when ?reply-fn
        (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))
  (defmethod event-msg-handler :dulcet.handler/bleh
    [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
    (let [session (:session ring-req)
          uid     (:uid     session)]
      (logf "Yipee.  Got %s" ev-msg)
      (when ?reply-fn
        (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

  ;; Add your (defmethod event-msg-handler <event-id> [ev-msg] <body>)s here...
  )

(defonce router_ (atom nil))
(defn  stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! router_ (sente/start-chsk-router! ch-chsk event-msg-handler*)))


(def app
  (let [handler (wrap-defaults routes site-defaults)]
    (start-router!)
    (if (env :dev?) (wrap-exceptions handler) handler)))


;; This stuff doesn't get used by figwheel.
(defn in-dev? [& args] true)
(def port 3000)
(defonce kill-server (atom nil))
(defonce server-args (atom nil))
(defn start-server [& args]
  (let [handler (if(in-dev? args)
                  (reload/wrap-reload (compojure.handler/site #'routes))
                  (compojure.handler/site routes))]
    (println "Starting server at port" port)
    (reset! server-args args)
    (reset! kill-server (run-server handler {:port port})))  )
(defn restart-server []
  (@kill-server)
  (start-server @server-args))
(defn -main [& args]
  (start-server args))
