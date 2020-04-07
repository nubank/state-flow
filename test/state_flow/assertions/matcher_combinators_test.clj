(ns state-flow.assertions.matcher-combinators-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [cats.core :as m]
            [matcher-combinators.clj-test]
            [matcher-combinators.matchers :as matchers]
            [state-flow.test-helpers :as test-helpers :refer [this-line-number]]
            [state-flow.assertions.matcher-combinators :as mc]
            [state-flow.test-helpers :as test-helpers :refer [shhh!]]
            [state-flow.state :as state]
            [state-flow.core :as state-flow :refer [flow]]))

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
              (test-helpers/delayed-modify 100 update :value swap! + 2)
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
          {:keys [flow-ret flow-state report-data]}
          (test-helpers/run-flow
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
             (test-helpers/delayed-modify 200 update :count swap! + 2)
             (testing "2" (mc/match? 2
                                     (state/gets (comp deref :count))
                                     {:times-to-try 2
                                      :sleep-time   75})))
           {:count (atom 0)})]
      (testing "returns match result"
        (is (match? {:match/result :mismatch} flow-ret)))
      (testing "reports match-results to clojure.test"
        (is (match? {:matcher-combinators.result/type  :mismatch
                     :matcher-combinators.result/value {:expected 2 :actual 0}}
                    (-> report-data :actual :match-result)))))))

(deftest test-report->actual
  (is (= :actual
         (first (shhh! (state/run
                         (m/fmap mc/report->actual (mc/match? :expected :actual))
                         {}))))))
