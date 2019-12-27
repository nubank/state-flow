(ns state-flow.probe-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [cats.data :as d]
            [state-flow.core :as state-flow]
            [state-flow.probe :as probe]
            [state-flow.test-helpers :as test-helpers]))

(deftest test-probe
  (testing "add two to state 1, result is 3, doesn't change world"
    (is (= [true 3]
           (first (state-flow/run (probe/probe test-helpers/add-two #(= % 3)) {:value 1})))))
  (testing "add two with small delay"
    (let [state {:value (atom 0)}]
      (is (= (d/pair nil state))
          (state-flow/run (test-helpers/delayed-add-two 100) state))
      (is (= (d/pair [true 2] state)
             (state-flow/run (probe/probe test-helpers/get-value-state #(= 2 %)) state)))))
  (testing "add two with too much delay"
        (let [state {:value (atom 0)}]
          (is (= (d/pair nil state)
                 (state-flow/run (test-helpers/delayed-add-two 4000) state)))
          (is (= (d/pair [false 0] state)
                 (state-flow/run (probe/probe test-helpers/get-value-state #(= 2 %)) state))))))

(comment
  (t/run-tests))
