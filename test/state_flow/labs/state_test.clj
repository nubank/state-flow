(ns state-flow.labs.state-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [matcher-combinators.test :refer [match?]]
            [state-flow.core :as state-flow]
            [state-flow.labs.state :as labs.state]
            [state-flow.state :as state]))

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
             (-> (labs.state/wrap-with
                  (fn [f] (print "called it") (f))
                  (state/modify put2))
                 (state-flow/run {}))))))
  (testing "flow runs successfully"
    (is (match? [{} {:value 2}]
                (-> (labs.state/wrap-with
                  (fn [f] (f))
                  (state/modify put2))
                 (state-flow/run {}))))))

(deftest with-redefs-test
  (is (match?
       [{} {:value 3}]
       (-> (labs.state/with-redefs [put2 put3]
             (state/modify put2))
           (state-flow/run {})))))
