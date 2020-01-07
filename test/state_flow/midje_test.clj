(ns state-flow.midje-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [matcher-combinators.test :refer [match?]]
            [cats.data :as d]
            [cats.monad.state :as state]
            [midje.sweet :refer [contains just]]
            [state-flow.core :as state-flow]
            [state-flow.midje :as midje]
            [state-flow.state :as sf.state]
            [state-flow.test-helpers :as test-helpers]))

(deftest verify
  (testing "add two to state 1, result is 3, doesn't change world"
    (let [[ret state] (state-flow/run (midje/verify "description" test-helpers/add-two 3) {:value 1})]
      (is (= 3 ret))
      (is (match? {:value 1 :meta {:description [["description"] []]}}
                  state))))

  (testing "works with non-state values"
    (is (= (d/pair 3 {:meta {:description [["description"] []]}})
           (state-flow/run (midje/verify "description" 3 3) {}))))

  (testing "add two with small delay"
    (let [world {:value (atom 0)}]
      (is (nil? (first (state-flow/run (test-helpers/delayed-add-two 100) world))))
      (is (= 2 (first (state-flow/run (midje/verify "description" test-helpers/get-value-state 2) world))))))

  (testing "add two with too much delay"
    (let [world {:value (atom 0)}]
      (is (nil? (first (state-flow/run (test-helpers/delayed-add-two 4000) world))))
      (is (= 0 (first (state-flow/run (midje/verify "description" test-helpers/get-value-state 0) world))))))

  (testing "extended equality"
    (let [val {:a 2 :b 5}]
      (= (d/pair val val)
         (state/run (midje/verify-probe "contains with monadic left value"
                                        (sf.state/get) (contains {:a 2}) {}) val)
         (state/run (midje/verify-probe "just with monadic left value"
                                        (sf.state/get) (just {:a 2 :b 5}) {}) val)))))
