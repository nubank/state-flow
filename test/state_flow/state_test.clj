(ns state-flow.state-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [state-flow.state :as state]))

(deftest primitives
  (testing "all primitives are flows"
    (is (state/state? (state/get)))
    (is (state/state? (state/gets inc)))
    (is (state/state? (state/modify inc)))
    (is (state/state? (state/put {:count 0})))
    (is (state/state? (state/return 37)))
    (is (state/state? (state/invoke (constantly "hello")))))

  (testing "primitives returns correct values"
    (is (= [2 2] (state/run (state/get) 2)))
    (is (= [3 2] (state/run (state/gets inc) 2)))
    (is (= [2 3] (state/run (state/modify inc) 2)))
    (is (= [2 3] (state/run (state/put 3) 2)))
    (is (= [37 2] (state/run (state/return 37) 2)))
    (is (= ["hello" 2] (state/run (state/invoke (constantly "hello")) 2)))))

(deftest exception-handling
  (let [double-state (state/modify * 2)]
    (testing "state with an exception returns an exception as the left value"
      (let [[res state] (state/run (state/>> double-state
                                         double-state
                                         (state/modify (fn [s] (throw (Exception. "My exception"))))
                                         double-state) 2)]
        (is (instance? Exception res))
        (is (= 8 state))))

    (testing "also handles exceptions with fmap"
      (let [[res state] (state/run
                         (state/fmap inc (state/>> double-state
                                           double-state
                                           (state/modify (fn [s] (throw (Exception. "My exception"))))
                                           double-state)) 2)]
        (is (instance? Exception res))
        (is (= 8 state)))

      (let [[res state] (state/run
                         (state/>> double-state
                               double-state
                               (state/modify (fn [s] (throw (Exception. "My exception"))))
                               double-state) 2)]
        (is (instance? Exception res))
        (is (= 8 state)))

      (let [[res state] (state/run
                         (state/>> (state/fmap (fn [s] (throw (Exception. "My exception")))
                                       (state/>> double-state
                                             double-state))
                               double-state) 2)]
        (is (instance? Exception res))
        (is (= 8 state)))))

  (testing "exceptions in primitives are returned as the result"
    (is (instance? Exception (first (state/run (state/gets #(/ 2 %)) 0))))
    (is (instance? Exception (first (state/run (state/modify #(/ 2 %)) 0))))))

(deftest get-and-put
  (let [increment-state (state/mlet [x (state/get)
                                 _ (state/put (inc x))]
                          (state/return x))]
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

(deftest fmap
  (is (= 1
         (first (state/run
                 (state/fmap (comp inc :count) (state/get))
                 {:count 0})))))
