(ns state-flow.state-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [cats.core :as m]
            [cats.data :as d]
            [cats.monad.exception :as e]
            [cats.monad.state :as state]
            [midje.sweet :as midje]
            [state-flow.state :as sf.state]))

(deftest test-state
  (let [increment-state       (m/mlet [x (sf.state/get)
                                       _ (sf.state/put (inc x))]
                                      (m/return x))
        double-state          (sf.state/modify #(* 2 %))
        state-with-fact-check (m/mlet [x increment-state
                                       y increment-state
                                       z (sf.state/get)]
                                      (m/return (midje/fact "z is equal to initial state incremented by two"
                                                            z => (+ x 2))))
        state-with-assertion  (m/mlet [x increment-state
                                       y increment-state
                                       z (sf.state/get)]
                                      (m/return (is (= z (+ x 2)))))]
    (testing "modify state with get and put"
      (is (= (d/pair 2 3)
             (state/run increment-state 2))))
    (testing "modify state with modify"
      (is (= (d/pair 2 4)
             (state/run double-state 2))))
    (testing "state with fact check"
      (is (= (d/pair true 2)
             (state/run state-with-fact-check 0))))
    (testing "state with clojure test assertion"
      (is (= (d/pair true 2)
             (state/run state-with-assertion 0))))
    (testing "state with an exception"
      (let [[res state] (state/run (m/>> double-state
                                         double-state
                                         (sf.state/modify (fn [s] (throw (Exception. "My exception"))))
                                         double-state) 2)]
        (is (e/failure? res))
        (is (= 8 state))))))

(comment
  )
(t/run-tests)
