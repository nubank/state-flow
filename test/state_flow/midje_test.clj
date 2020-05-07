(ns state-flow.midje-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [midje.sweet :refer [contains just]]
            [state-flow.core :as state-flow]
            [state-flow.midje :as midje]
            [state-flow.test-helpers :as test-helpers]))

(deftest verify
  (testing "doesn't change state when state-fn doesn't change state"
    (let [[ret state] (state-flow/run (midje/verify "description" test-helpers/add-two 3) {:value 1})]
      (is (= 3 ret))
      (is (= {:value 1} state))))

  (testing "works with non-state values"
    (is (= 3 (first (state-flow/run (midje/verify "description" 3 3) {})))))

  (testing "add two with small delay"
    (let [state {:value (atom 0)}]
      (is (= 0 @(:value (first (state-flow/run (test-helpers/delayed-add-two 100) state)))))
      (is (= 2 (first (state-flow/run (midje/verify "description" test-helpers/get-value-state 2) state))))))

  (testing "add two with too much delay"
    (let [state {:value (atom 0)}]
      (is (= 0 @(:value (first (state-flow/run (test-helpers/delayed-add-two 4000) state)))))
      (is (= 0 (first (state-flow/run (midje/verify "description" test-helpers/get-value-state 0) state))))))

  (testing "extended equality"
    (let [state {:a 2 :b 5}]
      (= state
         (first (state-flow/run (midje/verify-probe "contains with monadic left value"
                                                    (state-flow/get-state) (contains {:a 2}) {}) state))
         (first (state-flow/run (midje/verify-probe "just with monadic left value"
                                                    (state-flow/get-state) (just {:a 2 :b 5}) {}) state))))))
