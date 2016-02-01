(ns template.scroll
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as reagent :refer [atom create-class]]
            [cljs.core.async :refer [<! put! chan timeout]]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [goog.dom :as dom]))

(def prev-scroll-y (atom 0))
(def cur-scroll-y (atom 0))

(defn get-scroll []
  (-> (dom/getDocumentScroll) (.-y)))

(defn get-height []
  (dom/getDocumentHeight))

(defn- events->chan [el event-type c]
  (events/listen el event-type #(put! c %))
  c)

(defn scroll-chan []
  (events->chan js/window EventType/SCROLL (chan 1 (map get-scroll))))

(defn bottom-reached?  []
  (<= (- (get-height) (get-scroll)) 1000)) ;; Replace 1000 by size of the monitor

(defn listen! [action]
  (let [chan (scroll-chan)]
    (go-loop []
             (let [new-y (<! chan)]
               (reset! prev-scroll-y @cur-scroll-y)
               ;; not interested in negative values
               (reset! cur-scroll-y (max 0 new-y))
               (when (bottom-reached?)
                 (action)))
               (recur))))
