(ns state-flow.cljtest-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [matcher-combinators.test :refer [match?]]
            [matcher-combinators.matchers :as matchers]
            [state-flow.assertions.matcher-combinators :as assertions.matcher-combinators]
            [state-flow.test-helpers :as test-helpers :refer [this-line-number]]
            [state-flow.cljtest :refer [defflow]]
            [state-flow.core :as state-flow :refer [flow]]
            [state-flow.state :as state]))

(def get-value-state (state/gets (comp deref :value)))

(deftest test-match?
  (testing "passing cases"
    (testing "with literals for expected and actual"
      (let [[ret state] (state-flow/run
                          (testing "DESC" (assertions.matcher-combinators/match? 3 3))
                          {:initial :state})]
        (testing "returns match result"
          (is (match? {:expected 3
                       :actual   3
                       :report   {:match/result :match}}
                      ret)))
        (testing "doesn't change state"
          (is (= {:initial :state} state)))))

    (testing "with state monad for actual"
      (let [[ret state] (state-flow/run (testing "DESC" (assertions.matcher-combinators/match? 3 test-helpers/add-two)) {:value 1})]
        (testing "returns match result"
          (is (match? {:report {:match/result :match}} ret)))
        (testing "doesn't change state"
          (is (= {:value 1} state)))))

    (testing "with explicit matcher for expected"
      (let [[ret state] (state-flow/run (testing "DESC" (assertions.matcher-combinators/match? (matchers/equals 3)
                                                                                               test-helpers/add-two)) {:value 1})]
        (testing "returns match result"
          (is (match? {:report {:match/result :match}} ret)))
        (testing "doesn't change state"
          (is (= {:value 1} state)))))

    (testing "forcing probe to try more than once"
      (let [{:keys [flow-ret flow-state]}
            (test-helpers/run-flow
             (flow "flow"
               (test-helpers/delayed-add-two 100)
               (testing "2" (assertions.matcher-combinators/match? 2 get-value-state {:times-to-try 3
                                                                                      :sleep-time   110})))
             {:value (atom 0)})]
        (testing "returns match result"
          (is (match? {:expected 2
                       :actual   2
                       :report   {:match/result :match}}
                      flow-ret)))
        (testing "doesn't change state after the last probe"
          (is (= 2 (-> flow-state :value deref)))))))

  (testing "failure case"
    (testing "with probe result that never changes"
      (let [three-lines-before-call-to-match (this-line-number)
            {:keys [flow-ret flow-state report-data]}
            (test-helpers/run-flow
             (testing "contains with monadic left value" (assertions.matcher-combinators/match? (matchers/equals {:n 1})
                                                                                                (state/gets :value)
                                                                                                {:times-to-try 2}))
             {:value {:n 2}})]
        (testing "returns match result"
          (is (match? {:report {:match/result :mismatch}} flow-ret)))
        (testing "reports match-results to clojure.test"
          (testing "including the line number where match? was called"
            (= (+ three-lines-before-call-to-match 3) (:line report-data)))
          (is (match? {:matcher-combinators.result/type  :mismatch
                       :matcher-combinators.result/value {:n {:expected 1 :actual 2}}}
                      (-> report-data :actual :match-result))))))

    (testing "with probe result that only changes after timeout"
      (let [{:keys [flow-ret flow-state report-data]}
            (test-helpers/run-flow
             (flow "flow"
               (test-helpers/delayed-add-two 200)
               (testing "2" (assertions.matcher-combinators/match? 2 get-value-state {:times-to-try 2
                                                                                      :sleep-time   75})))
             {:value (atom 0)})]
        (testing "returns match result"
          (is (match? {:report {:match/result :mismatch}} flow-ret)))
        (testing "reports match-results to clojure.test"
          (is (match? {:matcher-combinators.result/type  :mismatch
                       :matcher-combinators.result/value {:expected 2 :actual 0}}
                      (-> report-data :actual :match-result))))))))

;; TODO:(dchelimsky,2019-12-27) I do not understand why, but inlining these expansions
;; in the deftest below causes test failures. I think it has to do with calling macroexpand
;; within a macro body.
(def flow-with-defaults
  (macroexpand-1 '(defflow my-flow (testing "equals" (assertions.matcher-combinators/match? 1 1)))))
(def flow-with-optional-args
  (macroexpand-1 '(defflow my-flow {:init (constantly {:value 1})} (testing "equals" (assertions.matcher-combinators/match? 1 1)))))
(def flow-with-binding-and-match
  (macroexpand-1 '(defflow my-flow {:init (constantly {:value 1
                                                       :map {:a 1 :b 2}})}
                    [value (state/gets :value)]
                    (testing "1" (assertions.matcher-combinators/match? 1 value))
                    (testing "b is 2" (assertions.matcher-combinators/match? {:b 2} (state/gets :map))))))

(deftest test-defflow
  (testing "defines flow with default parameters"
    (is (= '(clojure.test/deftest
              my-flow
              (state-flow.core/run*
               {}
               (state-flow.core/flow "my-flow" (testing "equals" (assertions.matcher-combinators/match? 1 1)))))
           flow-with-defaults)))

  (testing "defines flow with optional parameters"
    (is (= '(clojure.test/deftest
              my-flow
              (state-flow.core/run*
               {:init (constantly {:value 1})}
               (state-flow.core/flow "my-flow" (testing "equals" (assertions.matcher-combinators/match? 1 1)))))
           flow-with-optional-args)))

  (testing "defines flow with binding and flow inside match?"
    (is (= '(clojure.test/deftest
              my-flow
              (state-flow.core/run*
               {:init (constantly {:map {:a 1 :b 2} :value 1})}
               (state-flow.core/flow
                "my-flow"
                 [value (state/gets :value)]
                 (testing "1" (assertions.matcher-combinators/match? 1 value))
                 (testing "b is 2" (assertions.matcher-combinators/match? {:b 2} (state/gets :map))))))

           flow-with-binding-and-match))))

(defflow my-flow {:init (constantly {:value 1
                                     :map   {:a 1 :b 2}})}
  [value (state/gets :value)]
  (testing "1" (assertions.matcher-combinators/match? 1 value))
  (testing "b is 2" (assertions.matcher-combinators/match? {:b 2} (state/gets :map))))

(deftest run-a-flow
  (is (match? {:value 1
               :map   {:a 1 :b 2}}
              (second ((:test (meta #'my-flow)))))))
