(ns state-flow.internals.description
  (:require [clojure.string :as string]))

(def ^:private abbr-size 15)
(defn- abbr-list [expr-str ellipse-end]
  (let [[head & tail] (string/split expr-str #" ")]
    (if (empty? tail)
      expr-str
      (str head ellipse-end))))

(defn- ellipsify [expr-str]
  (case (first expr-str)
    \( (abbr-list expr-str " ...)")
    \[ (abbr-list expr-str " ...]")
    expr-str))

(defn abbr-sexpr [expr]
  (let [expr-str (str expr)]
    (if (< abbr-size (count expr-str))
      (ellipsify expr-str)
      expr-str)))

