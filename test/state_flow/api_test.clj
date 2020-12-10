(ns state-flow.api-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [state-flow.api :as flow]
            [state-flow.state :as state]))

(deftest test-for
  (is (= [1 2 3]
         (state/eval
          (flow/for [x [1 2 3]]
            (flow/return x))
          {}))))
