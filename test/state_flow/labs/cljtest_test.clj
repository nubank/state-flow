(ns state-flow.labs.cljtest-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [matcher-combinators.test :refer [match?]]
            [state-flow.core :as state-flow]
            [state-flow.labs.cljtest :as labs.cljtest]
            [state-flow.state :as state]
            [state-flow.test-helpers :as test-helpers]))

(deftest testing-macro
  (testing "works for failure cases"
    (let [[flow-ret flow-state]
          (test-helpers/shhh! (state-flow/run (state-flow/flow "desc"
                                                [v (state/gets :value)]
                                                (labs.cljtest/testing "contains with monadic left value"
                                                  (is (= {:a 1 :b 5} v))))
                                {:value {:a 2 :b 5}}))]
      (is (false? flow-ret))
      (is (match? {:value {:a 2 :b 5}}
                  flow-state)))))
