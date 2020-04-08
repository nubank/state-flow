(ns state-flow.cljtest-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [matcher-combinators.test :refer [match?]]
            [state-flow.assertions.matcher-combinators :as mc]
            [state-flow.cljtest :refer [defflow]]
            [state-flow.state :as state]))

;; TODO:(dchelimsky,2019-12-27) I do not understand why, but inlining these expansions
;; in the deftest below causes test failures. I think it has to do with calling macroexpand
;; within a macro body.
(def flow-with-defaults
  (macroexpand-1 '(defflow my-flow
                    (testing "equals" (mc/match? 1 1)))))
(def flow-with-optional-args
  (macroexpand-1 '(defflow my-flow
                    {:init (constantly {:value 1})}
                    (testing "equals" (mc/match? 1 1)))))
(def flow-with-binding-and-match
  (macroexpand-1 '(defflow my-flow {:init (constantly {:value 1
                                                       :map {:a 1 :b 2}})}
                    [value (state/gets :value)]
                    (testing "1" (mc/match? 1 value))
                    (testing "b is 2" (mc/match? {:b 2} (state/gets :map))))))

(deftest test-defflow
  (testing "defines flow with default parameters"
    (is (= '(clojure.test/deftest
              my-flow
              (state-flow.core/run*
               {}
               (state-flow.core/flow "my-flow" (testing "equals" (mc/match? 1 1)))))
           flow-with-defaults)))

  (testing "defines flow with optional parameters"
    (is (= '(clojure.test/deftest
              my-flow
              (state-flow.core/run*
               {:init (constantly {:value 1})}
               (state-flow.core/flow "my-flow" (testing "equals" (mc/match? 1 1)))))
           flow-with-optional-args)))

  (testing "defines flow with binding and flow inside match?"
    (is (= '(clojure.test/deftest
              my-flow
              (state-flow.core/run*
               {:init (constantly {:map {:a 1 :b 2} :value 1})}
               (state-flow.core/flow
                "my-flow"
                 [value (state/gets :value)]
                 (testing "1" (mc/match? 1 value))
                 (testing "b is 2" (mc/match? {:b 2} (state/gets :map))))))

           flow-with-binding-and-match))))

(defflow my-flow {:init (constantly {:value 1
                                     :map   {:a 1 :b 2}})}
  [value (state/gets :value)]
  (testing "1" (mc/match? 1 value))
  (testing "b is 2" (mc/match? {:b 2} (state/gets :map))))

(deftest run-a-flow
  (is (match? {:value 1
               :map   {:a 1 :b 2}}
              (second ((:test (meta #'my-flow)))))))
