(ns state-flow.probe-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [state-flow.core :as state-flow]
            [state-flow.probe :as probe]
            [state-flow.test-helpers :as test-helpers]))

(deftest test-probe
  (let [[flow-return flow-state] (state-flow/run (probe/probe test-helpers/add-two #(= % 3)) {:value 1})]
    (testing "returns a tuple of [check-fn-result state-fn-return]"
      (is (= [true 3] flow-return)))
    (testing "doesn't change state if state-fn doesn't change state"
      (is (= {:value 1} flow-state))))
  (testing "with delay < timeout"
    (let [state {:value (atom 0)}]
      (is (= 0 (->> (state-flow/run (test-helpers/delayed-add-two 100) state) last :value deref)))
      (is (= [true 2]
             (first (state-flow/run (probe/probe test-helpers/get-value-state #(= 2 %)) state))))))
  (testing "with delay > timeout"
    (let [state {:value (atom 0)}]
      (is (= 0 (->> (state-flow/run (test-helpers/delayed-add-two 1500) state) last :value deref)))
      (is (= [false 0]
             (first (state-flow/run (probe/probe test-helpers/get-value-state #(= 2 %)) state))))))
  (testing "with modified sleep-time and times-to-try"
    (let [state {:value (atom 0)}]
      (is (= 0 (->> (state-flow/run (test-helpers/delayed-add-two 1100) state) last :value deref)))
      (is (= [true 2]
             (first (state-flow/run (probe/probe test-helpers/get-value-state #(= 2 %)
                                                 {:sleep-time   250
                                                  :times-to-try 5})
                      state)))))))
