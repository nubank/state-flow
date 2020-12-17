(ns state-flow.cljtest-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [matcher-combinators.test :refer [match?]]
            [state-flow.assertions.matcher-combinators :as mc]
            [state-flow.cljtest :refer [defflow] :as cljtest]
            [state-flow.core :as state-flow]
            [state-flow.state :as state]))

(defflow my-flow {:init (constantly {:value 1
                                     :map   {:a 1 :b 2}})}
  [value (state/gets :value)]
  (testing "1" (mc/match? 1 value))
  (testing "b is 2" (mc/match? {:b 2} (state/gets :map))))

(deftest test-clojure-test-report
  (let [report {:match/expected 1
                :match/actual 2
                :match/result :mismatch
                :flow/description-stack [{:description "my test"
                                          :file "my-test-file.clj"
                                          :line 23}]}
        match-report (first (state-flow/run (mc/match? 1 2) {}))]
    (testing "we adapt state-flow assertion report into clojure-test report format"
      (is (match? {:type :fail
                   :message "my test (my-test-file.clj:23)"
                   :expected 1
                   :actual 2
                   :file "my-test-file.clj"
                   :line 23}
                  (#'cljtest/clojure-test-report report))))
    (testing "we save pretty printing metadata"
      (is (match? {:type :state-flow.cljtest/mismatch}
           (meta (:actual (#'cljtest/clojure-test-report match-report))))))))

(deftest run-a-flow
  ;; NOTE:(sovelten,2020-12-15) This test works when called via clojure.test/run-tests
  ;; It doesn't work when called independently as (run-a-flow) because t/*report-counters* is not initialized then
  (let [report-counters-before (deref t/*report-counters*)
        [ret state]            ((:test (meta #'my-flow)))
        report-counters-after  (deref t/*report-counters*)]
    (testing "flow returns a match report state remains as set"
      (is (match? [{:match/result :match
                    :match/actual {:a 1 :b 2}}
                   {:value 1
                    :map   {:a 1 :b 2}}]
                  [ret state])))
    (testing "meta of state contains the test report"
      (is (match? {:test-report {:assertions [{:match/result :match
                                               :match/expected 1
                                               :match/actual 1}
                                              {:match/result :match
                                               :match/expected {:b 2}
                                               :match/actual {:a 1 :b 2}}]}}
                  (meta state))))
    (testing "we have reported the assertions"
      (is (match? {:test 0 :pass 2 :fail 0 :error 0}
                  (merge-with - report-counters-after report-counters-before))))))

(deftest test-deprecated-match?
  (testing "with times-to-try > 1 and a value instead of a step"
    (testing "does not throw (to preserve backward compatibility)"
      (is (= :match
             (:match/result
              (state/eval (state-flow.cljtest/match? "description"
                                                     3
                                                     3
                                                     {:times-to-try 2}) {})))))))
