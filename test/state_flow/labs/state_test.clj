(ns state-flow.labs.state-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [matcher-combinators.test]
            [state-flow.api :as flow]
            [state-flow.core :as state-flow]
            [state-flow.labs.state :as labs.state]))

(defn put2 [w] (assoc w :value 2))
(defn put3 [w] (assoc w :value 3))

(defn wrap-with-redefs [f]
  (with-redefs [put2 put3]
    (f)))

(defn called-it-callback [f]
  (print "called it")
  (f))

(deftest wrap-with-test
  (testing "wrapper is called"
    (is (= "called it"
           (with-out-str
             (state-flow/run
              (labs.state/wrap-with
               (fn [f] (print "called it") (f))
               (flow/swap-state put2))
              {})))))
  (testing "flow runs successfully"
    (is (match? [{} {:value 2}]
                (flow/run
                 (labs.state/wrap-with
                  (fn [f] (f))
                  (flow/swap-state put2))
                 {})))))

(deftest with-redefs-test
  (is (match?
       [{} {:value 4}]
       (state-flow/run
        (labs.state/with-redefs [put2 put3]
          (flow/swap-state put2)
          (flow/swap-state update :value inc))
        {}))))
