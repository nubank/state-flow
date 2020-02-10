(ns state-flow.cljtest
  (:require [cats.core :as m]
            [clojure.test :as t]
            [matcher-combinators.core :as matcher-combinators]
            [matcher-combinators.test]
            [state-flow.core :as core]
            [state-flow.probe :as probe]
            [state-flow.state :as state]))

(defn match-probe
  "Returns the right value returned by probe/probe."
  ([state matcher]
   (match-probe state matcher {}))
  ([state matcher params]
   (m/fmap (comp :value last)
           (probe/probe state
                        #(matcher-combinators/match? (matcher-combinators/match matcher %))
                        params))))

(defmacro match?
  "Builds a clojure.test assertion using matcher-combinators.

  - actual can be a literal value, a primitive, or a flow
  - expected can be a literal value or a matcher-combinators matcher"
  [match-desc actual expected & [params]]
  ;; Nesting m/do-let inside a call the function core/flow* is
  ;; a bit ugly, but it supports getting the correct line number
  ;; information from core/current-description.
  (core/flow*
   {:description match-desc
    :caller-meta (meta &form)}
   `(m/do-let
     [flow-desc# (core/current-description)
      actual#    (if (state/state? ~actual)
                   (match-probe ~actual ~expected ~params)
                   (state/return ~actual))]
     (state/wrap-fn #(t/testing flow-desc# (t/is (~'match? ~expected actual#))))
     (state/return actual#))))

(defmacro defflow
  {:arglists '([name & flows]
               [name parameters & flows])}
  [name & forms]
  (let [[parameters & flows] (if (map? (first forms))
                               forms
                               (cons {} forms))]
    `(t/deftest ~name
       (core/run* ~parameters (core/flow ~(str name) ~@flows)))))
