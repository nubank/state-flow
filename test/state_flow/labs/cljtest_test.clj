(ns state-flow.labs.cljtest-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [matcher-combinators.test :refer [match?]]
            [state-flow.test-helpers :as test-helpers]
            [state-flow.labs.cljtest :as labs.cljtest]
            [state-flow.core :as state-flow]
            [state-flow.state :as state]))

(def inc-b (state/modify #(update-in % [:value :b] inc)))

(def sample-flow
  (state-flow/flow "desc"
    inc-b
    [v (state/gets :value)]
    (labs.cljtest/testing-with "contains with monadic left value"
      [expected (state/return {:a 1 :b 5})]
      (is (= expected v)))))


(deftest testing-with-macro
  (testing "bindings are available during test"
    (is (true? (-> (state-flow/flow "desc"
                     (labs.cljtest/testing-with "testing with bindings"
                       [a (state/return 1)]
                       (is (= a 1))))
                   (test-helpers/run-flow {})
                   :flow-ret))))

  (testing "bindings are not available after test"
    (is (= 0 (-> (state-flow/flow "desc"
                   [a (state/return 0)]
                   (labs.cljtest/testing-with "testing with bindings"
                     [a (state/return 1)]
                     (is (= a 1)))
                   (state/return a))
                 (test-helpers/run-flow {})
                 :flow-ret))))

  (testing "returns test result"
    (is (true? (->> {:value {:a 1 :b 4}}
                    (test-helpers/run-flow sample-flow)
                    :flow-ret)))
    (is (false? (->> {:value {:a 1 :b 3}}
                     (test-helpers/run-flow sample-flow)
                     :flow-ret))))

  (testing "preserves flow state"
    (is (match? {:value {:a 1 :b 5}}
                (->> {:value {:a 1 :b 4}}
                     (test-helpers/run-flow sample-flow)
                     :flow-state)))

    (is (match? {:value {:a 1 :b 8}}
                (->> {:value {:a 1 :b 7}}
                     (test-helpers/run-flow sample-flow)
                     :flow-state)))))
