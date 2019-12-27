(ns state-flow.midje-test
  (:require [cats.core :as m]
            [cats.data :as d]
            [cats.monad.state :as state]
            [midje.sweet :refer :all]
            [state-flow.core :as state-flow]
            [state-flow.midje :as midje]
            [state-flow.state :as sf.state]
            [state-flow.test-helpers :as test-helpers]))

(facts "on verify"

  (fact "add two to state 1, result is 3, doesn't change world"
    (let [[ret state] (state-flow/run (midje/verify "description" test-helpers/add-two 3) {:value 1})]
      ret => 3
      state => {:value 1 :meta {:description [["description"] []]}}))

  (fact "works with non-state values"
    (state-flow/run (midje/verify "description" 3 3) {})
    => (d/pair 3 {:meta {:description [["description"] []]}}))

  (fact "add two with small delay"
    (def world {:value (atom 0)})
    (state-flow/run (test-helpers/delayed-add-two 100) world) => (d/pair nil world)
    (state-flow/run (midje/verify "description" test-helpers/get-value-state 2) world)
    => (d/pair 2 (merge world {:meta {:description [["description"] []]}})))

  (fact "add two with too much delay"
    (def world {:value (atom 0)})
    (state-flow/run (test-helpers/delayed-add-two 4000) world)
    (state-flow/run (midje/verify "description" test-helpers/get-value-state 0) world))

  (fact "extended equality works"
    (let [val {:a 2 :b 5}]
      (state/run (midje/verify-probe "contains with monadic left value"
                                     (sf.state/get) (contains {:a 2}) {}) val) => (d/pair val val)
      (state/run (midje/verify-probe "just with monadic left value"
                                     (sf.state/get) (just {:a 2 :b 5}) {}) val) => (d/pair val val))))
