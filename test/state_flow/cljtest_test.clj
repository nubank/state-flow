(ns state-flow.cljtest-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [matcher-combinators.test :refer [match?]]
            [matcher-combinators.matchers :as matchers]
            [state-flow.test-helpers :as test-helpers]
            [state-flow.cljtest :as cljtest :refer [defflow]]
            [state-flow.core :as state-flow :refer [flow]]
            [state-flow.state :as state]))

(def get-value-state (state/gets (comp deref :value)))

(deftest test-match?
  (testing "add two to state 1, result is 3, doesn't change state"
    (let [[ret state] (state-flow/run (cljtest/match? "test-1" test-helpers/add-two 3) {:value 1})]
      (is (= 3 ret))
      (is (= 1 (:value state)))))

  (testing "works with non-state values"
    (let [[ret state] (state-flow/run (cljtest/match? "test-2" 3 3) {})]
      (is (= 3 ret))
      (is (empty? (:value state)))))

  (testing "works with matcher combinators (embeds by default)"
    (let [val {:value {:a 2 :b 5}}
          [ret state] (state-flow/run (cljtest/match? "contains with monadic left value" (state/gets :value) {:a 2}) val)]
      (is (= {:a 2 :b 5} ret))
      (is (= {:a 2 :b 5} (:value state)))))

  (testing "works with matcher combinators equals"
    (let [val {:value {:a 2 :b 5}}
          [ret state] (state-flow/run (cljtest/match? "contains with monadic left value" (state/gets :value) (matchers/equals {:a 2 :b 5})) {:value {:a 2 :b 5}})]
      (is (= {:a 2 :b 5} ret))
      (is (= {:a 2 :b 5} (:value state)))))

  (testing "failure case"
    (let [{:keys [flow-ret flow-state]}
          (test-helpers/run-flow (cljtest/match? "contains with monadic left value"
                                                 (state/gets :value)
                                                 (matchers/equals {:a 1 :b 5}))
                                 {:value {:a 2 :b 5}})]
      (is (= {:a 2 :b 5} flow-ret))
      (is (= {:a 2 :b 5} (:value flow-state)))))

  (testing "add two with small delay"
    (let [state  {:value (atom 0)}
          {:keys [flow-ret]}
          (test-helpers/run-flow
           (flow ""
             (test-helpers/delayed-add-two 100)
             (cljtest/match? "" get-value-state 2))
           state)]
      (is (= 2 flow-ret))))

  (testing "we can tweak timeout and times to try"
    (let [state  {:value (atom 0)}
          {:keys [report-data flow-ret flow-state]}
          (test-helpers/run-flow
           (flow ""
             (test-helpers/delayed-add-two 100)
             (cljtest/match? "" get-value-state 2 {:sleep-time   0
                                                   :times-to-try 1}))
           state)]
      (is (match? {:matcher-combinators.result/type :mismatch
                   :matcher-combinators.result/value {:expected 2 :actual 0}}
                  (-> report-data :actual :match-result)))
      (is (= 0 flow-ret))))

  (testing "add two with too much delay (timeout)"
    (let [state  {:value (atom 0)}
          {:keys [report-data flow-ret flow-state]}
          (test-helpers/run-flow
           (flow ""
             (test-helpers/delayed-add-two 4000)
             (cljtest/match? "" get-value-state 2))
           state)]
      (is (match? {:matcher-combinators.result/type :mismatch
                   :matcher-combinators.result/value {:expected 2 :actual 0}}
                  (-> report-data :actual :match-result)))
      (is (= 0 flow-ret))))

  (testing "works with matcher combinators in any order"
    (let [val {:value [1 2 3]}
          {:keys [flow-state]}
          (test-helpers/run-flow (cljtest/match? "contains with monadic left value"
                                                 (state/gets :value)
                                                 (matchers/in-any-order [1 3 2])) val)]
      (is (match? {:value [1 2 3]} flow-state)))))

;; TODO:(dchelimsky,2019-12-27) I do not understand why, but inlining these expansions
;; in the deftest below causes test failures. I think it has to do with calling macroexpand
;; within a macro body.
(def flow-with-defaults
  (macroexpand-1 '(defflow my-flow (cljtest/match? "equals" 1 1))))
(def flow-with-optional-args
  (macroexpand-1 '(defflow my-flow {:init (constantly {:value 1})} (cljtest/match? "equals" 1 1))))
(def flow-with-binding-and-match
  (macroexpand-1 '(defflow my-flow {:init (constantly {:value 1
                                                       :map {:a 1 :b 2}})}
                    [value (state/gets :value)]
                    (cljtest/match? value 1)
                    (cljtest/match? (state/gets :map) {:b 2}))))

(deftest test-defflow
  (testing "defines flow with default parameters"
    (is (= '(clojure.test/deftest
              my-flow
              (state-flow.core/run*
                {}
                (state-flow.core/flow "my-flow" (cljtest/match? "equals" 1 1))))
           flow-with-defaults)))

  (testing "defines flow with optional parameters"
    (is (= '(clojure.test/deftest
              my-flow
              (state-flow.core/run*
                {:init (constantly {:value 1})}
                (state-flow.core/flow "my-flow" (cljtest/match? "equals" 1 1))))
           flow-with-optional-args)))

  (testing "defines flow with binding and flow inside match?"
    (is (= '(clojure.test/deftest
              my-flow
              (state-flow.core/run*
                {:init (constantly {:map {:a 1 :b 2} :value 1})}
                (state-flow.core/flow
                  "my-flow"
                  [value (state/gets :value)]
                  (cljtest/match? value 1)
                  (cljtest/match? (state/gets :map) {:b 2}))))
           flow-with-binding-and-match))))

(defflow my-flow {:init (constantly {:value 1
                                     :map   {:a 1 :b 2}})}
  [value (state/gets :value)]
  (cljtest/match? "" value 1)
  (cljtest/match? "" (state/gets :map) {:b 2}))

(deftest run-a-flow
  (is (match? {:value 1
               :map   {:a 1 :b 2}}
              (second ((:test (meta #'my-flow)))))))
