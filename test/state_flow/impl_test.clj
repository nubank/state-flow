(ns state-flow.impl-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [cats.core :as m]
            [cats.monad.exception :as e]
            [matcher-combinators.test :refer [match?]]
            [state-flow.impl :as impl]
            [state-flow.state :as state]))

(deftest state-operations
  (testing "all operation constructors return states"
    (is (state/state? (impl/get-state)))
    (is (state/state? (impl/get-state inc)))
    (is (state/state? (impl/swap-state inc)))
    (is (state/state? (impl/return 37)))
    (is (state/state? (impl/reset-state {:count 0})))
    (is (state/state? (impl/wrap-fn (constantly "hello")))))

  (testing "operations return correct values when run"
    (is (= [2 2] (state/run (impl/get-state) 2)))
    (is (= [3 2] (state/run (impl/get-state inc) 2)))
    (is (= [2 3] (state/run (impl/swap-state inc) 2)))
    (is (= [37 2] (state/run (impl/return 37) 2)))
    (is (= [2 3] (state/run (impl/reset-state 3) 2)))
    (is (= ["hello" 2] (state/run (impl/wrap-fn (constantly "hello")) 2)))))

(deftest get-state
  (testing "supports single function or varargs"
    (is (= [{:count 1} {:count 0}]
           (state/run (impl/get-state #(update % :count inc)) {:count 0})
           (state/run (impl/get-state update :count inc) {:count 0})))))

(deftest swap-state
  (testing "supports single function or varargs"
    (is (= [{:count 0} {:count 1}]
           (state/run (impl/swap-state #(update % :count inc)) {:count 0})
           (state/run (impl/swap-state update :count inc) {:count 0})))))

(deftest get-and-reset-state
  (let [increment-state (m/mlet [x (impl/get-state)
                                 _ (impl/reset-state (inc x))]
                                (m/return x))]
    (testing "modify state with get and put"
      (is (= [2 3]
             (state/run increment-state 2))))))

(deftest exception-handling
  (let [double-state (impl/swap-state * 2)]
    (testing "state with an exception returns a failure as the left value"
      (let [[res state] (state/run (m/>> double-state
                                         double-state
                                         (impl/swap-state (fn [s] (throw (Exception. "My exception"))))
                                         double-state) 2)]
        (is (e/failure? res))
        (is (= 8 state))))

    (testing "also handles exceptions with fmap"
      (let [[res state] (state/run
                          (m/fmap inc (m/>> double-state
                                            double-state
                                            (impl/swap-state (fn [s] (throw (Exception. "My exception"))))
                                            double-state)) 2)]
        (is (e/failure? res))
        (is (= 8 state)))

      (let [[res state] (state/run
                          (m/>> double-state
                                double-state
                                (impl/swap-state (fn [s] (throw (Exception. "My exception"))))
                                double-state) 2)]
        (is (e/failure? res))
        (is (= 8 state)))

      (let [[res state] (state/run
                          (m/>> (m/fmap (fn [s] (throw (Exception. "My exception")))
                                        (m/>> double-state
                                              double-state))
                                double-state) 2)]
        (is (e/failure? res))
        (is (= 8 state)))))

  (testing "exceptions in primitives are returned as the result"
    (is (e/failure? (first (state/run (impl/get-state #(/ 2 %)) 0))))
    (is (e/failure? (first (state/run (impl/swap-state #(/ 2 %)) 0))))))
