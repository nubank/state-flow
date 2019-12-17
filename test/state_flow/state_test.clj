(ns state-flow.state-test
  (:require [cats.context :as ctx]
            [cats.core :as m]
            [cats.data :as d]
            [cats.monad.exception :as e]
            [cats.protocols :as p]
            [midje.sweet :refer :all]
            [state-flow.state :as sf.state]
            [cats.monad.state :as state]))

(def postincrement
  (m/mlet [x (sf.state/get)
           _ (sf.state/put (+ x 1))]
    (m/return x)))

(def double-state (sf.state/modify #(* 2 %)))

(fact "postincrement"
  (state/run postincrement 1) => (d/pair 1 2))

(def state-with-fact-check
  (m/mlet [x postincrement
           y postincrement
           z (sf.state/get)]
          (m/return (fact "z is equal to initial state incremented by two"
                      z => (+ x 2)))))

(state/run state-with-fact-check 0)

(def will-fail
  (m/>> double-state
        double-state
        (sf.state/modify (fn [s] (throw (Exception. "My exception"))))
        double-state))

(fact "Error short-circuits execution"
  (state/run will-fail 2) => (fn [[l r]] (and (e/failure? l)
                                              (= r 8))))
