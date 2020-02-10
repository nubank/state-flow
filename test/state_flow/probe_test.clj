(ns state-flow.probe-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [state-flow.core :as state-flow]
            [state-flow.probe :as probe]
            [state-flow.test-helpers :as test-helpers]))

(deftest test-probe
  (let [[flow-return flow-state] (state-flow/run (probe/probe test-helpers/add-two #(= % 3)) {:value 1})]
    (testing "returns a tuple of [check-fn-result state-fn-return]"
      (is (= [{:check-result true :value 3}] flow-return)))
    (testing "doesn't change state if state-fn doesn't change state"
      (is (= {:value 1} flow-state))))
  (testing "with delay < timeout"
    (let [state {:value (atom 0)}]
      (is (= 0 (->> (state-flow/run (test-helpers/delayed-add-two 100) state) last :value deref)))
      (is (= [{:value 0 :check-result false} {:value 2 :check-result true}]
             (first (state-flow/run (probe/probe test-helpers/get-value-state #(= 2 %)) state))))))
  (testing "with delay > timeout"
    (let [state {:value (atom 0)}]
      (is (= 0 (->> (state-flow/run (test-helpers/delayed-add-two 1500) state) last :value deref)))
      (is (= (repeat 5 {:check-result false :value 0})
             (first (state-flow/run (probe/probe test-helpers/get-value-state #(= 2 %)) state))))))
  (testing "with modified sleep-time and times-to-try"
    (let [state {:value (atom 0)}]
      (is (= 0 (->> (state-flow/run (test-helpers/delayed-add-two 950) state) last :value deref)))
      (is (= (conj (vec (repeat 4 {:check-result false :value 0})) {:check-result true :value 2})
             (first (state-flow/run (probe/probe test-helpers/get-value-state #(= 2 %)
                                                 {:sleep-time   250
                                                  :times-to-try 5})
                      state)))))))
