(ns state-flow.state-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [cats.core :as m]
            [cats.data :as d]
            [cats.monad.exception :as e]
            [state-flow.state :as state]))

(deftest primitives
  (testing "primitives are constructable outside monad context"
    (is (state/get))
    (is (state/gets inc))
    (is (state/modify inc))
    (is (state/return 37))
    (is (state/put {:count 0}))))

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
           (is (= 8 state))))))

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
