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
  (testing "passing cases"
    (testing "with literals for expected and actual"
      (let [[ret state] (state-flow/run (cljtest/match? "DESC" 3 3) {:initial :state})]
        (testing "returns actual (literal)"
          (is (= 3 ret)))
        (testing "doesn't change state"
          (is (= {:initial :state} state)))
        (testing "provides match-results on meta"
          (is (match? [[{:matcher-combinators.result/type   :match
                         :matcher-combinators.result/value  3
                         :matcher-combinators.result/weight 0}]]
                      (-> state meta :match-results))))))

    (testing "with state monad for actual"
      (let [[ret state] (state-flow/run (cljtest/match? "DESC" test-helpers/add-two 3) {:value 1})]
        (testing "returns actual (derived from state)"
          (is (= 3 ret)))
        (testing "doesn't change state"
          (is (= {:value 1} state)))
        (testing "provides match-results on meta"
          (is (match? [[{:matcher-combinators.result/type   :match
                         :matcher-combinators.result/value  3
                         :matcher-combinators.result/weight 0}]]
                      (-> state meta :match-results))))))

    (testing "with explicit matcher for expected"
      (let [[ret state] (state-flow/run (cljtest/match? "DESC"
                                                        test-helpers/add-two
                                                        (matchers/equals 3)) {:value 1})]
        (testing "returns actual (derived from state)"
          (is (= 3 ret)))
        (testing "doesn't change state"
          (is (= {:value 1} state)))
        (testing "provides match-results on meta"
          (is (match? [[{:matcher-combinators.result/type   :match
                         :matcher-combinators.result/value  3
                         :matcher-combinators.result/weight 0}]]
                      (-> state meta :match-results))))))

    (testing "forcing probe to try more than once"
      (let [{:keys [flow-ret flow-state]}
            (test-helpers/run-flow
             (flow "flow"
               (test-helpers/delayed-add-two 100)
               (cljtest/match? "2" get-value-state 2 {:times-to-try 2
                                                      :sleep-time 75}))
             {:value (atom 0)})]
        (testing "returns actual (derived from state)"
          (is (= 2 flow-ret)))
        (testing "match-results include mismatch then match"
          (is (match? [[{:matcher-combinators.result/type :mismatch
                         :matcher-combinators.result/value {:expected 2 :actual 0}
                         :matcher-combinators.result/weight 1}
                        {:matcher-combinators.result/type :match
                         :matcher-combinators.result/value 2
                         :matcher-combinators.result/weight 0}]]
                      (-> flow-state meta :match-results)))))))

  (testing "failure case"
    (testing "with probe result that never changes"
      (let [{:keys [flow-ret flow-state report-data]}
            (test-helpers/run-flow (cljtest/match? "contains with monadic left value"
                                                   (state/gets :value)
                                                   (matchers/equals {:n 1})
                                                   {:times-to-try 2})
                                   {:value {:n 2}})]
        (testing "returns actual"
          (is (= {:n 2} flow-ret)))
        (testing "provides match-results on meta"
          (is (match? [[{:matcher-combinators.result/type   :mismatch
                         :matcher-combinators.result/value  {:n {:expected 1 :actual 2}}
                         :matcher-combinators.result/weight 1}
                        {:matcher-combinators.result/type   :mismatch
                         :matcher-combinators.result/value  {:n {:expected 1 :actual 2}}
                         :matcher-combinators.result/weight 1}]]
                      (-> flow-state meta :match-results))))
        (testing "reports match-results to clojure.test"
          (is (match? {:matcher-combinators.result/type  :mismatch
                       :matcher-combinators.result/value {:n {:expected 1 :actual 2}}}
                      (-> report-data :actual :match-result))))))

    (testing "with probe result that only changes after timeout"
      (let [{:keys [flow-ret flow-state report-data]}
            (test-helpers/run-flow
             (flow "flow"
               (test-helpers/delayed-add-two 200)
               (cljtest/match? "2" get-value-state 2 {:times-to-try 2
                                                      :sleep-time 75}))
             {:value (atom 0)})]
        (testing "returns actual (derived from state)"
          (is (= 0 flow-ret)))
        (testing "match-results include mismatch then match"
          (is (match? [[{:matcher-combinators.result/type :mismatch
                         :matcher-combinators.result/value {:expected 2 :actual 0}
                         :matcher-combinators.result/weight 1}
                        {:matcher-combinators.result/type :mismatch
                         :matcher-combinators.result/value {:expected 2 :actual 0}
                         :matcher-combinators.result/weight 1}]]
                      (-> flow-state meta :match-results))))
        (testing "reports match-results to clojure.test"
          (is (match? {:matcher-combinators.result/type  :mismatch
                       :matcher-combinators.result/value {:expected 2 :actual 0}}
                      (-> report-data :actual :match-result))))))))

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
  (cljtest/match? "1" value 1)
  (cljtest/match? "b is 2" (state/gets :map) {:b 2}))

(deftest run-a-flow
  (is (match? {:value 1
               :map   {:a 1 :b 2}}
              (second ((:test (meta #'my-flow)))))))
