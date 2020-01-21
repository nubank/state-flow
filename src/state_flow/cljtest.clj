(ns state-flow.cljtest
  (:require [cats.core :as m]
            [clojure.test :as ctest :refer [is]]
            [matcher-combinators.core :as matcher-combinators]
            [matcher-combinators.test]
            [state-flow.core :as core]
            [state-flow.probe :as probe]
            [state-flow.state :as state]))

(defmacro match-expr
  [desc value checker meta]
  (with-meta
    (list `ctest/testing desc (list `is (list 'match? checker value)))
    meta))

(defn match-probe
  ([state matcher]
   (match-probe state matcher {}))
  ([state matcher params]
   (m/fmap second
           (probe/probe state
                        #(matcher-combinators/match? (matcher-combinators/match matcher %))
                        params))))

(defmacro match?
  "Builds a clojure.test assertion using matcher combinators"
  [match-desc actual checker & [params]]
  (let [form-meta (meta &form)]
    `(core/flow ~match-desc
       [flow-desc# (core/current-description)
        actual#    (if (state/state? ~actual)
                     (match-probe ~actual ~checker ~params)
                     (state/return ~actual))]
       (state/wrap-fn #(do (match-expr flow-desc# actual# ~checker ~form-meta)
                           actual#)))))

(defmacro defflow
  {:arglists '([name & flows]
               [name parameters & flows])}
  [name & forms]
  (let [[parameters & flows] (if (map? (first forms))
                               forms
                               (cons {} forms))]
    `(do
       (def ~name
         (core/flow ~(str name) ~@flows))
       (ctest/deftest ~(symbol (str "run-" name))
         (core/run* ~parameters ~name)))))
