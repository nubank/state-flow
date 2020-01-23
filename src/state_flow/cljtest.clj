(ns state-flow.cljtest
  (:require [cats.core :as m]
            [clojure.test :as ctest :refer [is]]
            [matcher-combinators.core :as matcher-combinators]
            [matcher-combinators.test]
            [state-flow.core :as core]
            [state-flow.probe :as probe]
            [state-flow.state :as state]))

(defmacro match-expr
  [desc value matcher meta]
  (with-meta
    (list `ctest/testing desc (list `is (list 'match? matcher value)))
    meta))

(defn match-probe
  "Returns a map of :value (bound to the right value of the result of
  probe/probe), with :match-results added to the metadata."
  ([state matcher]
   (match-probe state matcher {}))
  ([state matcher params]
   (let [match-results (atom [])]
     (m/fmap (fn [pair]
               (with-meta {:value (second pair)}
                 {:match-results @match-results}))
             (probe/probe state
                          (fn [v]
                            (let [res (matcher-combinators/match matcher v)]
                              (swap! match-results conj res)
                              (matcher-combinators/match? res)))
                          params)))))

(defn expect*
  "Internal use - do not call directly. Use expect instead."
  [{:keys [form-meta] :as opts} expected actual & [params]]
  (core/flow* opts
              `(m/do-let
                [flow-desc#    (core/current-description)
                 probe-result# (if (state/state? ~actual)
                                 (match-probe ~actual ~expected ~params)
                                 (match-probe (state/return ~actual)
                                              ~expected
                                              {:sleep-time 0 :times-to-try 1}))]
                (core/modify-meta update :match-results (fnil conj []) (:match-results (meta probe-result#)))
                (state/wrap-fn #(do (match-expr flow-desc# (:value probe-result#) ~expected ~form-meta)
                                    (:value probe-result#))))))

(defmacro expect
  "Builds a clojure.test assertion using matcher combinators.

  - expected can be a matcher-combinators matcher or a literal value
    - literals will be used to infer default matchers
  - actual can be a state monad or a literal value"
  [expected actual & [params]]
  (expect* {:description "expect"
            :caller-meta (meta &form)}
           expected
           actual
           params))

(defmacro match?
  "Deprecated: Use expect instead."
  [match-desc actual matcher & [params]]
  (expect* {:description match-desc
            :caller-meta (meta &form)}
           matcher
           actual
           params))

(defmacro defflow
  {:arglists '([name & flows]
               [name parameters & flows])}
  [name & forms]
  (let [[parameters & flows] (if (map? (first forms))
                               forms
                               (cons {} forms))]
    `(ctest/deftest ~name
       (core/run* ~parameters (core/flow ~(str name) ~@flows)))))
