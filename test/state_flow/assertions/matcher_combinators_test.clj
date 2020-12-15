(ns state-flow.assertions.matcher-combinators-test
  (:require [cats.core :as m]
            [cats.monad.exception :as e]
            [clojure.test :as t :refer [deftest is testing]]
            [matcher-combinators.matchers :as matchers]
            [matcher-combinators.test]
            [state-flow.assertions.matcher-combinators :as mc]
            [state-flow.core :as state-flow :refer [flow]]
            [state-flow.state :as state]
            [state-flow.test-helpers :as test-helpers :refer [this-line-number]]
            [state-flow.test-helpers :as test-helpers :refer [shhh!]]))

(def get-value-state (state/gets (comp deref :value)))

(deftest test-match?-passing-cases
  (testing "with literals for expected and actual"
    (let [[ret state] (state/run (mc/match? 3 3) {:initial :state})]
      (testing "returns match result"
        (is (match? {:match/expected 3
                     :match/actual   3
                     :match/result   :match}
                    ret)))
      (testing "doesn't change state"
        (is (= {:initial :state} state)))))

  (testing "with step for actual"
    (let [[ret state] (state/run (mc/match? 3 (state/return 3)) {})]
      (testing "returns match result"
        (is (match? {:match/result :match} ret)))
      (testing "doesn't change state"
        (is (= {} state)))))

  (testing "with explicit matcher for expected"
    (let [[ret state] (state/run (mc/match? (matchers/equals 3) 3) {})]
      (testing "returns match result"
        (is (match? {:match/result :match} ret)))
      (testing "doesn't change state"
        (is (= {} state)))))

  (testing "forcing probe to try more than once"
    (let [[flow-ret flow-state]
          (state/run
           (flow "flow"
             (test-helpers/swap-later 100 :value + 2)
             (mc/match? 2 (state/gets (comp deref :value)) {:times-to-try 3
                                                            :sleep-time   110}))
           {:value (atom 0)})]
      (testing "returns match result with probe info"
        (is (match? {:probe/sleep-time   110
                     :probe/times-to-try 3
                     :probe/results      [{:check-result false :value 0} {:check-result true :value 2}]
                     :match/expected     2
                     :match/actual       2
                     :match/result       :match}
                    flow-ret)))
      (testing "doesn't change state after the last probe"
        (is (= 2 (-> flow-state :value deref)))))))

(deftest test-match?-failing-cases
  (testing "with probe result that never changes"
    (let [three-lines-before-call-to-match (this-line-number)
          [flow-ret flow-state]
          (state-flow/run
            (mc/match? (matchers/equals {:n 1})
                       (state/gets :value)
                       {:times-to-try 2})
            {:value {:n 2}})]
      (testing "returns match result"
        (is (match? {:match/result       :mismatch
                     :mismatch/detail    {:n {:expected 1 :actual 2}}
                     :probe/results      [{:check-result false :value {:n 2}}
                                          {:check-result false :value {:n 2}}]
                     :probe/sleep-time   200
                     :probe/times-to-try 2
                     :match/expected     {:expected {:n 1}}
                     :match/actual       {:n 2}}
                    flow-ret)))
      (testing "saves assertion report to state with current description stack"
        (is (match? {:flow/description-stack [{:description "match?"}]
                     :match/result       :mismatch
                     :mismatch/detail    {:n {:expected 1 :actual 2}}
                     :probe/results      [{:check-result false :value {:n 2}}
                                          {:check-result false :value {:n 2}}]
                     :probe/sleep-time   200
                     :probe/times-to-try 2
                     :match/expected     {:expected {:n 1}}
                     :match/actual       {:n 2}}
                    (first (get-in (meta flow-state) [:test-report :assertions])))))))

  (testing "with probe result that only changes after timeout"
    (let [[flow-ret flow-state]
          (state-flow/run
            (flow "flow"
              (test-helpers/swap-later 200 :count + 2)
              (testing "2" (mc/match? 2
                                      (state/gets (comp deref :count))
                                      {:times-to-try 2
                                       :sleep-time   75})))
            {:count (atom 0)})]
      (testing "returns match result"
        (is (match? {:match/result :mismatch} flow-ret)))
      (testing "pushes test report to metadata"
        (is (match? {:match/result  :mismatch
                     :match/expected 2
                     :match/actual 0}
                    (-> (meta flow-state) :test-report :assertions first))))))

  (testing "with times-to-try > 1 and a value instead of a step"
    (testing "throws"
      (is (re-find #"actual must be a step or a flow when :times-to-try > 1"
                   (.getMessage
                    (:failure
                     (first
                      (try
                        (state/run (mc/match? 3 (+ 30 7) {:times-to-try 2}) {})
                        (catch Exception e e))))))))))

(deftest test-report->actual
  (is (= :actual
         (first (shhh! (state/run
                        (m/fmap mc/report->actual (mc/match? :expected :actual))
                        {}))))))

(def bogus (state/gets (fn [_] (throw (Exception. "My exception")))))

(deftest short-circuiting
  (testing "flow with fail-fast stops at first failing assertion"
    (let [[just-match-ret _] (shhh! (state/run (mc/match? 1 2) {:initial :state}))
          result             (shhh! (state-flow/run* {:init       (constantly {:value 0})
                                                      :fail-fast? true
                                                      :on-error   state-flow/ignore-error}
                                                     (flow "stop before boom"
                                                       (mc/match? 1 2)
                                                       (flow "will explode" bogus))))]
      (testing "state is left as is"
        (is (match? {:value 0} (second result))))
      (testing "the return value is the same as a failing match?"
        (is (= just-match-ret (first result)))))))
