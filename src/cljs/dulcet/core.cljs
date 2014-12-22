(ns dulcet.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [reagent.core :as reagent :refer [atom]]
            [secretary.core :as secretary :include-macros true]
            [clojure.string  :as str]
            [cljs.core.async :as async  :refer (<! >! chan timeout)]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [taoensso.sente  :as sente]
            [taoensso.encore :as encore :refer (logf)]
            ;; Optional, for Transit encoding:
            ;;[taoensso.sente.packers.transit :as sente-transit]
            ;;[keithwhor.audiosynth :as audio]
            ;;[gwilson.geo :as geo]
            )


  (:import goog.History))


;;; Sente
(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" ; Note the same path as before
       {:type :auto ; e/o #{:auto :ajax :ws}
       })]
  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom
  )

(def packer
  "Defines our packing (serialization) format for client<->server comms."
  :edn ; Default
  ;;(sente-transit/get-flexi-packer :edn) ; Experimental, needs Transit deps
  )


(defmulti event-msg-handler :id) ; Dispatch on event-id
;; Wrap for logging, catching, etc.:
(defn     event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (logf "Event: %s" event)
  (event-msg-handler ev-msg))

(do ; Client-side methods
  (defmethod event-msg-handler :default ; Fallback
    [{:as ev-msg :keys [event]}]
    (logf "Unhandled event: %s" event))

  (defmethod event-msg-handler :chsk/state
    [{:as ev-msg :keys [?data]}]
    (if (= ?data {:first-open? true})
      (logf "Channel socket successfully established!")
      (logf "Channel socket state change: %s" ?data)))

  (defmethod event-msg-handler :chsk/recv
    [{:as ev-msg :keys [?data]}]
    (logf "Push event from server: %s" ?data))

  ;; Add your (defmethod handle-event-msg! <event-id> [ev-msg] <body>)s here...
  )


(def router_ (atom nil))
(defn  stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
  (stop-router!)
  (reset! router_ (sente/start-chsk-router! ch-chsk event-msg-handler*)))


;; -------------------------
;; State
(defonce app-state (atom {:text "Hello, this is: " :count 0}))

(defn get-state [k & [default]]
  (clojure.core/get @app-state k default))

(defn put! [k v]
  (swap! app-state assoc k v))

;; -------------------------
;; Views

(defmulti page identity)


(defn send-callback [kw]
  (fn [x]( chsk-send! [:dulcet.handler/bleh {kw x}])  ))

(defn send-loc-callback [loc]
  (let [coords (.-coords loc)
        latitude (.-latitude coords)
        longitude (.-longitude coords )
        location  {:location {:latitude latitude :longitude longitude}}]
    (chsk-send! [:dulcet.handler/bleh {:location location}]))  )

(defmethod page :page1 [_]
  (navigator.geolocation.getAccurateCurrentPosition send-local-callback
                                                    (send-callback :error)
                                                    send-loc-callback
                                                    send-loc-callback
                                                    send-loc-callback
                                                    {:desiredAccuracy 20, :maxWait 15000})
  [:div [:h2 (get-state :count) ": "(get-state :text) "Page 1"]
   [:div [:a {:href "#/page2"} "go to page 2"]]])

(defmethod page :page2 [_]
  (let [piano (. js/Synth (createInstrument "piano"))]
    (fn []
      (println "Hey let's play" piano)
      (. piano (play "C" 4 2))
      (println "Hey let's play" piano)
      [:div [:h2 (get-state :count) (get-state :text "Type something.") "Page 2"]
       [:div [:a {:href "#/"} "go to page 1"]]
       [:div [:input {:type "text" :value (get-state :input)
                      :on-change #(let [v (-> % .-target .-value)]
                                    (println "howdy" v)
                                    (put! :input v)
                                    (chsk-send! [:dulcet.handler/bleh {:value  v}]))}]]
       ])))

(defmethod page :default [_]
  [:div "Invalid/Unknown route"])






(defn main-page []
  [:div [page (get-state :current-page)]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (swap! app-state update-in [:count] dec)
  (put! :current-page :page1))

(secretary/defroute "/page2" []
  (swap! app-state update-in [:count] dec)
  (put! :current-page :page2))


;; fiddles
(go
  (while true
    (<! (timeout 10000))
    (swap! app-state update-in [:count] inc)
    )  )

  ;; -------------------------
  ;; Initialize app
(defn init! []
  (start-router!)
  (reagent/render-component [main-page] (.getElementById js/document "app")))

;; -------------------------
;; History
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))
;; need to run this after routes have been defined
(hook-browser-navigation!)
