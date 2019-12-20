(ns state-flow.probe-test
  (:require [cats.data :as d]
            [midje.sweet :refer :all]
            [state-flow.core :as state-flow]
            [state-flow.probe :as probe]
            [state-flow.test-helpers :as test-helpers]))

(facts probe/probe
  (fact "add two to state 1, result is 3, doesn't change world"
    (first (state-flow/run (probe/probe test-helpers/increment-two #(= % 3)) {:value 1})) => [true 3])

  (fact "add two with small delay"
    (let [world {:value (atom 0)}]
      (state-flow/run (test-helpers/delayed-increment-two 100) world) => (d/pair nil world)
      (state-flow/run (probe/probe test-helpers/get-value-state #(= 2 %)) world) => (d/pair [true 2] world)))

  (fact "add two with too much delay"
    (let [world {:value (atom 0)}]
      (state-flow/run (test-helpers/delayed-increment-two 4000) world) => (d/pair nil world)
      (state-flow/run (probe/probe test-helpers/get-value-state #(= 2 %)) world) => (d/pair [false 0] world))))


