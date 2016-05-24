(ns controller-communication.core
  (:require-macros
   [reagent.ratom :refer [reaction]])
  (:require
   [reagent.core :as reagent]
   [keechma.ui-component :as ui]
   [keechma.controller :as controller]
   [keechma.app-state :as app-state]
   [cljs.core.async :refer [put!]]))


;; Controllers

(defrecord Counter []
  controller/IController

  (params [_ _] true)

  (start [_ params app-db]
    (assoc-in app-db [:count] 0))

  (handler [_ app-db-atom in-chan _]
    (controller/dispatcher
     app-db-atom
     in-chan
     {:dec #(swap! app-db-atom update-in [:count] dec)
      :inc #(swap! app-db-atom update-in [:count] inc)})))


(defrecord CounterProxy []
  controller/IController

  (params [_ _] true)

  (start [_ params app-db]
    (assoc-in app-db [:count] 0))

  (handler [_ app-db-atom in-chan out-chan]
    (controller/dispatcher
     app-db-atom
     in-chan
     {:send-to-counter #(put! out-chan [[:Counter :inc]])})))

;; Subs

(defn counter-value-sub [app-db]
  (reaction
   (get-in @app-db [:count])))


;; Components

(defn counter-render [app-db]
  (let [counter-sub (ui/subscription app-db :counter-value)]
    (fn []
      [:div
       [:button {:on-click #(ui/send-command app-db :dec)} "Decrement"]
       [:button {:on-click #(ui/send-command app-db :inc)} "Increment"]
       [:p (str "Count: " @counter-sub)]
       [(ui/component app-db :counter-proxy)]])))

(def counter-component
  (ui/constructor
   {:renderer          counter-render
    :subscription-deps [:counter-value]
    :component-deps [:counter-proxy]}))

(defn counter-proxy [ctx]
  [:button {:on-click #(ui/send-command ctx :send-to-counter)} "Send to Counter (inc)"])

(def counter-proxy-component
  (ui/constructor
   {:renderer counter-proxy}))

;; Initialize App

(def app-definition
  {:components    {:main (assoc counter-component :topic :Counter)
                   :counter-proxy (assoc counter-proxy-component :topic :CounterProxy)}
   :controllers   {:Counter (->Counter)
                   :CounterProxy (->CounterProxy)}
   :subscriptions {:counter-value counter-value-sub}
   :html-element  (.getElementById js/document "app")})

(defonce running-app (clojure.core/atom))

(defn start-app! []
  (reset! running-app (app-state/start! app-definition)))

(defn reload []
  (let [current @running-app]
    (if current
      (app-state/stop! current start-app!)
      (start-app!))))

(defn ^:export main []
  (start-app!))
