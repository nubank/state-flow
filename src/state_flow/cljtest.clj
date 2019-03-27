(ns state-flow.cljtest
  (:require [cats.core :as m]
            [clojure.test :as ctest :refer [is]]
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

(defmacro deftest
  "Define a test to be run.

  Receives optional parameters
  `initializer-fn`, a function with no arguments that returns the initial state.
  `cleanup-fn`, function receiving the final state to perform cleanup if necessary"
  [sym
   {:keys [initializer-fn
           cleanup-fn
           flow-runner]
    :or   {initializer-fn `(constantly {})
           cleanup-fn     `identity
           flow-runner    `core/run!}}
   & flows]
  `(def ~(vary-meta sym merge {::test       true
                               ::initialize initializer-fn
                               ::cleanup    cleanup-fn})
     (fn [initial-state#] (~flow-runner (core/flow ~(str sym) ~@flows) initial-state#))))

(defn ns->tests
  "Returns all flows defined with `deftest`"
  [ns]
  (->> ns ns-interns vals (filter (comp ::test meta))))

(defn run-test*
  [v]
  (let [{::keys [cleanup initialize]} (meta v)
        initial-state                 (initialize)
        [_ final-state :as result]    (@v initial-state)]
    (cleanup final-state)
    result))

(defmacro run-test
  "Runs test `test-name`defined with `deftest`"
  [test-name]
  `(run-test* (var ~test-name)))
