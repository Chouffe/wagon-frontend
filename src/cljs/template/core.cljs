(ns template.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as reagent :refer [atom create-class]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [cljs.core.async :refer [<! put! chan timeout]]
            [cljs-http.client :as http]
            [template.scroll :as scroll]
            [template.utils :refer [header->coercer]]
            [goog.events :as events]
            [goog.events.EventType :as EventType]
            [goog.dom :as dom]))

;; ---------
;; App State

(def app-state (atom {:csv {:headers nil
                            :rows []
                            :offset 0
                            :n-loaded 0}
                      :search nil}))

(defn add-csv! [[headers & rows :as raw-csv]]
  (let [coercers (map header->coercer headers)
        clean-rows (mapv (fn [row coercer-fns]
                          (mapv (fn [cell coerce-fn] (coerce-fn cell)) row coercer-fns))
                        rows (repeat coercers))]
          (swap! app-state assoc-in [:csv :headers] headers)
          (swap! app-state update-in [:csv :rows] (fn [rows] (apply conj rows (remove (set rows) clean-rows))))))

;; API call
(defn load-chunk-csv! [n offset]
  (go (when-let [{:keys [body]} (<! (http/get (str "http://localhost:3000/api/csv?n=" n "&offset=" offset)))]
        (add-csv! body)
        (swap! app-state update-in [:csv :n-loaded] + n)
        (swap! app-state assoc-in [:csv :offset] offset))))

(defn load-more! []
  (let [n-loaded (get-in @app-state [:csv :n-loaded])]
    (when (< n-loaded 1000000)
      (load-chunk-csv! 200 n-loaded))))

(defn- sort-rows-by-header
  [rows column-index compar]
  (let [nil-cell (comp nil? #(get % column-index))]
  (concat (sort-by (fn [row] (get row column-index)) compar (remove nil-cell rows))
          (filter nil-cell rows))))

(defn sort-rows! [column-index compar]
  (swap! app-state update-in [:csv :rows] sort-rows-by-header column-index compar))

;; -------------------------
;; Views

(defn navbar []
  [:nav.navbar.navbar-default
   [:div.container-fluid
    [:div.navbar-header
     [:a.navbar-brand {:href "#"}
      [:img {:src "/images/wagon.png" :style {:width "50px"}}]]]]])

(defn make-page [& components]
  [:div
   [navbar]
   (into [:div.container] components)])

(defn row->tr [row]
  (into [:tr] (map (fn [cell] [:td (str cell)]) row)))

(defn header->th [indx header]
  [:th header
   [:i.glyphicon.glyphicon-arrow-up   {:on-click (fn [_] (sort-rows! indx >))}]
   [:i.glyphicon.glyphicon-arrow-down {:on-click (fn [_] (sort-rows! indx <))}]])

(defn search-box []
  [:div.row
   [:div.col-lg-2
    [:div.input-group
     [:input.form-control {:on-change (fn [e]
                                        (let [value (.-target.value e)]
                                          (swap! app-state assoc :search (when-not (empty? value) value)) ))
                           :placeholder "Search for"}]
     [:span.input-group-btn
      [:button.btn.btn-default "Search"]]]]])

(def table
  (fn [[headers & rows]]
    [:table.table.table-striped
     (into [:thead] (map-indexed header->th headers))
     (into [:tbody] (apply map row->tr rows))]))

(defn filter-with
  [q-string rows]
  (if (empty? q-string)
    rows
    (filter (partial some #(re-find (re-pattern q-string) (str %))) rows)))

(defn table-comp []
  (create-class
    {:component-will-mount (fn [_] (load-more!))
     :component-did-mount (fn [_] (scroll/listen! load-more!))
     :reagent-render (fn [_]
                       [:div
                        (search-box)
                        [:br]
                        [:br]
                        (table [(get-in @app-state [:csv :headers])
                                (filter-with (get @app-state :search)
                                             (get-in @app-state [:csv :rows]))])])}))

(defn home-page []
  (make-page
    [:div.jumbotron
     [:h2 "Frontend coding challenge for Wagon"]
     [:ul
      [:li "Backend: Clojure (Compojure, Ring)"]
      [:li "Frontend: ClojureScript (React, Hiccup)"]
      [:li "Implemented"
       [:ul
        [:li "Lazy csv parsing"]
        [:li "Infinite scroll"]
        [:li "Sort by column"]
        [:li "Search Box"]
        [:li "Responsive (Bootstrap)"]]]]]
    [table-comp]))

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!)
  (accountant/dispatch-current!)
  (mount-root))
