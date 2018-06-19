(ns state-flow.helpers.core-test
  (:require [nu.monads.postman.helpers :as helpers]
            [cats.data :as d]
            [midje.sweet :refer :all]
            [nu.monads.state :as state]))

(facts "on with-resources"
  (fact "fetches value and increments 1"
    (state/run (helpers/with-resource :value #(+ 1 %)) {:value 3}) => (d/pair 4 {:value 3}))
  (fact "first argument not callable, assertion error"
    (helpers/with-resource 1 #(+ 1 %)) => (throws AssertionError))
  (fact "second argument not callable, assertion error"
    (helpers/with-resource :value 1) => (throws AssertionError)))
