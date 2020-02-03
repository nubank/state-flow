(ns state-flow.cljtest
  (:require [cats.core :as m]
            [clojure.test :as t]
            [matcher-combinators.core :as matcher-combinators]
            [matcher-combinators.test]
            [state-flow.core :as core]
            [state-flow.probe :as probe]
            [state-flow.state :as state]))

(defn match-probe
  "Returns a map of :value (bound to the right value of the result of
  probe/probe) and :match-results."
  ([state matcher]
   (match-probe state matcher {}))
  ([state matcher params]
   (let [match-results (atom [])]
     (m/fmap (fn [[_ r]] {:value r
                          :match-results @match-results})
             (probe/probe state
                          (fn [v]
                            (let [res (matcher-combinators/match matcher v)]
                              (swap! match-results conj res)
                              (matcher-combinators/match? res)))
                          params)))))

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
     [flow-desc#    (core/current-description)
      probe-result# (if (state/state? ~actual)
                      (match-probe ~actual ~expected ~params)
                      (match-probe (state/return ~actual)
                                   ~expected
                                   {:sleep-time 0 :times-to-try 1}))
      :let [value#         (:value probe-result#)
            match-results# (:match-results probe-result#)]]
     (state/wrap-fn #(t/testing flow-desc# (t/is (~'match? ~expected value#))))
     (core/modify-meta update :match-results (fnil conj []) match-results#)
     (state/return value#))))

(defmacro defflow
  {:arglists '([name & flows]
               [name parameters & flows])}
  [name & forms]
  (let [[parameters & flows] (if (map? (first forms))
                               forms
                               (cons {} forms))]
    `(t/deftest ~name
       (core/run* ~parameters (core/flow ~(str name) ~@flows)))))
