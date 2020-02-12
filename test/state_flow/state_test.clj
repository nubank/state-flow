(ns state-flow.state-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [cats.core :as m]
            [cats.data :as d]
            [cats.monad.exception :as e]
            [state-flow.state :as state]))

(deftest state
  (testing "primitives are constructable outside monad context"
    (state/get)
    (state/gets inc)
    (state/modify inc)
    (state/return 37)
    (state/put {:count 0}))
  (let [increment-state (m/mlet [x (state/get)
                                 _ (state/put (inc x))]
                          (m/return x))
        double-state    (state/modify #(* 2 %))]
    (testing "modify state with get and put"
      (is (= [2 3]
             (state/run increment-state 2))))
    (testing "modify state with modify"
      (is (= [2 4]
             (state/run double-state 2))))
    (testing "state with an exception"
      (let [[res state] (state/run (m/>> double-state
                                         double-state
                                         (state/modify (fn [s] (throw (Exception. "My exception"))))
                                         double-state) 2)]
        (is (e/failure? res))
        (is (= 8 state))))))
