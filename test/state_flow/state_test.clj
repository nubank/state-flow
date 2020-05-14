(ns state-flow.state-test
  (:require [cats.core :as m]
            [cats.monad.exception :as e]
            [clojure.test :as t :refer [deftest is testing]]
            [state-flow.core :as state-flow]
            [state-flow.state :as state]))

(deftest primitives
  (testing "primitives returns correct values"
    (is (= [2 2] (state/run (state/get) 2)))
    (is (= [3 2] (state/run (state/gets inc) 2)))
    (is (= [2 3] (state/run (state/modify inc) 2)))
    (is (= [37 2] (state/run (state/return 37) 2)))
    (is (= [2 3] (state/run (state/put 3) 2)))
    (is (= ["hello" 2] (state/run (state/wrap-fn (constantly "hello")) 2))))

  (testing "all primitives are states"
    (is (state/state? (state/get)))
    (is (state/state? (state/gets inc)))
    (is (state/state? (state/modify inc)))
    (is (state/state? (state/return 37)))
    (is (state/state? (state/put {:count 0})))
    (is (state/state? (state/wrap-fn (constantly "hello"))))))

(deftest exception-handling
  (let [double-state (state/modify * 2)]
    (testing "state with an exception returns a failure as the left value"
      (let [[res state] (state/run (m/>> double-state
                                         double-state
                                         (state/modify (fn [s] (throw (Exception. "My exception"))))
                                         double-state) 2)]
        (is (e/failure? res))
        (is (= 8 state))))

    (testing "also handles exceptions with fmap"
      (let [[res state] (state/run
                          (m/fmap inc (m/>> double-state
                                            double-state
                                            (state/modify (fn [s] (throw (Exception. "My exception"))))
                                            double-state)) 2)]
        (is (e/failure? res))
        (is (= 8 state)))

      (let [[res state] (state/run
                          (m/>> double-state
                                double-state
                                (state/modify (fn [s] (throw (Exception. "My exception"))))
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
    (is (e/failure? (first (state/run (state/gets #(/ 2 %)) 0))))
    (is (e/failure? (first (state/run (state/modify #(/ 2 %)) 0))))))

(deftest get-and-put
  (let [increment-state (m/mlet [x (state/get)
                                 _ (state/put (inc x))]
                                (m/return x))]
    (testing "modify state with get and put"
      (is (= [2 3]
             (state/run increment-state 2))))))

(deftest modify
  (testing "supports single function or varargs"
    (is (= [{:count 0} {:count 1}]
           (state/run (state/modify #(update % :count inc)) {:count 0})
           (state/run (state/modify update :count inc) {:count 0})))))

(deftest gets
  (testing "supports single function or varargs"
    (is (= [{:count 1} {:count 0}]
           (state/run (state/gets #(update % :count inc)) {:count 0})
           (state/run (state/gets update :count inc) {:count 0})))))

(defn custom-runner [flow state]
  (state-flow/run flow state))

(deftest runner
  (testing "returns state-flow/run using state-flow/run"
    (is (identical? state-flow/run
                    (first (state-flow/run (state/runner))))))
  (testing "defaults to state-flow/run using state-flow/run*"
    (is (identical? state-flow/run
                    (first (state-flow/run* {} (state/runner))))))
  (testing "returns custom runner when providing custom runner to state-flow/run*"
    (is (identical? custom-runner
                    (first (state-flow/run* {:runner custom-runner}
                             (state/runner)))))))
