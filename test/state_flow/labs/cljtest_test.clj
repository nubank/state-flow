(ns state-flow.labs.cljtest-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [matcher-combinators.test :refer [match?]]
            [state-flow.test-helpers :as th]
            [state-flow.labs.cljtest :as labs.cljtest]
            [state-flow.core :as state-flow]
            [state-flow.state :as state]))

(deftest testing-macro
  (testing "works for failure cases"
    (let [{:keys [flow-ret flow-state]}
          (th/run-flow (state-flow/flow "desc"
                         [v (state/gets :value)]
                         (labs.cljtest/testing "contains with monadic left value"
                           (is (= {:a 1 :b 5} v))))
                       {:value {:a 2 :b 5}})]
      (is (false? flow-ret))
      (is (match? {:value {:a 2 :b 5}}
                  flow-state)))))
