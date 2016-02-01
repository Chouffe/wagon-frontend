(ns template.utils)

(defn wrap-nil-fn [f]
  (fn [s] (when-not (empty? s) (f s))))

(defn header->coercer [string]
  (case (last (re-find #"\((.*)\)" string))
    "id"    (wrap-nil-fn str)
    "text"  (wrap-nil-fn str)
    "float" (wrap-nil-fn #(js/parseFloat %))
    "int"   (wrap-nil-fn #(js/parseInt %))))

