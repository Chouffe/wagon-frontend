(ns template.handler
  (:require [compojure.core :refer [ANY GET defroutes]]
            [compojure.route :refer [not-found resources]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            [template.middleware :refer [wrap-middleware]]

            [ring.util.response :refer [resource-response response]]
            [ring.middleware.json :as middleware-json]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults api-defaults]]

            [clojure-csv.core :as csv]

            [cheshire.core :as json]
            [environ.core :refer [env]]))

(def lazy-csv
  (->> "resources/public/csv/input.csv"
       slurp
       csv/parse-csv))

(defn get-csv-rows
  ([n csv] (get-csv-rows 0 n csv))
  ([offset n csv] (->> csv
                       (drop (inc offset))
                       (take n)
                       (cons (first csv)))))

(def mount-target
  [:div#app
      [:h3 "ClojureScript has not been compiled!"]
      [:p "please run "
       [:b "lein figwheel"]
       " in order to start the compiler"]])

(def loading-page
  (html
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1"}]
     (include-css (if (env :dev)
                    "bower_components/bootstrap/dist/css/bootstrap.css"
                    "bower_components/bootstrap/dist/css/bootstrap.min.css"))]
    [:body
     mount-target
     (include-js "js/app.js")]]))

(defroutes routes
  (GET "/" [] loading-page)
  (GET "/about" [] loading-page)

  (resources "/")
  (not-found "Not Found"))

(def html-routes
  (wrap-middleware #'routes))

(defroutes api-routes
  (GET "/api/csv" [n offset]
       ;; TODO: Add error handling when casting from str to int
       (response (get-csv-rows (Integer. offset) (Integer. n) lazy-csv))))

(defn wrap-api-middleware [handler]
  (-> handler
      (middleware-json/wrap-json-body)
      (middleware-json/wrap-json-response)
      (wrap-defaults api-defaults)
      wrap-params))

(defroutes main-routes
  (ANY "/api/*" [] (wrap-api-middleware #'api-routes))
  (ANY "*" [] (wrap-middleware #'routes)))

(def app main-routes)
