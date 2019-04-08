(ns state-flow.cljtest
  (:require [cats.core :as m]
            [clojure.test :as ctest :refer [is]]
            [matcher-combinators.test]
            [state-flow.core :as core]
            [state-flow.state :as state]))

(defn match-expr
  [desc value checker]
  (let [test-name (symbol (clojure.string/replace desc " " "-"))]
    (list `ctest/testing desc (list `is (list 'match? checker value)))))

(defmacro match+meta
  [desc value checker meta]
  (with-meta (match-expr desc value checker) meta))

(defmacro match?
  "Builds a clojure.test assertion using matcher combinators"
  [desc value checker]
  (let [the-meta (meta &form)]
    `(core/flow ~desc
       [full-desc# (core/get-description)]
       (if (state/state? ~value)
         (m/mlet [extracted-value# ~value]
           (state/wrap-fn #(do (match+meta full-desc# extracted-value# ~checker ~the-meta)
                               extracted-value#)))
         (state/wrap-fn #(do (match+meta full-desc# ~value ~checker ~the-meta)
                             ~value))))))

(defmacro defflow
  {:arglists '([name & flows]
               [name parameters & flows])}
  [name & forms]
  (let [[parameters & flows] (if (map? (first forms))
                               forms
                               (cons {} forms))]
    `(ctest/deftest ~name
       (core/run* ~parameters (core/flow ~(str name) ~@flows)))))
