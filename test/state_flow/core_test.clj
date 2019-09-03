(ns state-flow.core-test
  (:require [cats.core :as m]
            [cats.data :as d]
            [cats.monad.state :as state]
            [midje.sweet :refer :all]
            [state-flow.test-helpers :as test-helpers]
            [state-flow.midje :as midje]
            [state-flow.core :as state-flow]
            [state-flow.state :as sf.state]))

(defn double-key [k]
  (state/swap (fn [w] (update w k #(* 2 %)))))

(facts state-flow/probe
  (fact "add two to state 1, result is 3, doesn't change world"
    (first (state-flow/run (state-flow/probe test-helpers/increment-two #(= % 3)) {:value 1})) => [true 3])

  (fact "add two with small delay"
    (let [world {:value (atom 0)}]
      (state-flow/run (test-helpers/delayed-increment-two 100) world) => (d/pair nil world)
      (state-flow/run (state-flow/probe test-helpers/get-value-state #(= 2 %)) world) => (d/pair [true 2] world)))

  (fact "add two with too much delay"
    (let [world {:value (atom 0)}]
      (state-flow/run (test-helpers/delayed-increment-two 4000) world) => (d/pair nil world)
      (state-flow/run (state-flow/probe test-helpers/get-value-state #(= 2 %)) world) => (d/pair [false 0] world))))


(def bogus (state/state (fn [s] (throw (Exception. "My exception")))))
(def increment-two-value
  (state/swap (fn [s] (update s :value #(+ 2 %)))))

(def nested-flow
  (state-flow/flow "root"
    (state-flow/flow "child1" increment-two-value)
    (state-flow/flow "child2" increment-two-value)
    (midje/verify "value incremented by 4"
      (state/gets #(-> % :value)) 4)))

(def flow-with-bindings
  (state-flow/flow "root"
    [original (state/gets :value)
     :let [doubled (* 2 original)]]
    (sf.state/swap #(assoc % :value doubled))
    (midje/verify "value is doubled"
      (state/gets #(-> % :value)) doubled)))

(def bogus-flow
  (state-flow/flow "root"
    (state-flow/flow "child1" increment-two-value)
    (state-flow/flow "child2" bogus increment-two-value)))

(def empty-flow
  (state-flow/flow "empty"))

(fact "on push-meta"
  (state/exec (m/>> (state-flow/push-meta "mydesc")
                    (state-flow/push-meta "mydesc2")) {}) => {:meta {:description ["mydesc"  "mydesc2"]}})

(facts "on run flow"
  (fact "run flow of single step"
    (state/exec (state-flow/flow "single step" increment-two-value) {:value 0}) => {:meta {:description []}
                                                                                    :value 2})
  (fact "flow with two steps"
    (second (state-flow/run (state-flow/flow "two step flow"
                              increment-two-value
                              increment-two-value) {:value 0})) => {:meta {:description []} :value 4})

  (fact "nested flow"
    (second (state-flow/run nested-flow {:value 0}))
    => {:meta {:description []}
        :value 4})

  (fact "empty flow"
    (second (state-flow/run empty-flow {:value 0}))
    => {:meta {:description []}
        :value 0})

  (fact "flow without description fails at macro-expansion time"
        (macroexpand `(state-flow/flow [original (state/gets :value)
                                        :let [doubled (* 2 original)]]
                                       (sf.state/swap #(assoc % :value doubled))))
        => (throws IllegalArgumentException))

  (fact "flow with a `(str ..)` expr for the description is fine"
      (macroexpand `(state-flow/flow (str "foo") [original (state/gets :value)
                                                  :let [doubled (* 2 original)]]
                                     (sf.state/swap #(assoc % :value doubled))))
        => list?)

  (fact "but flows with an expression that resolves to a string also aren't valid,
        due to resolution limitations at macro-expansion time"
        (let [my-desc "trolololo"]
          (macroexpand `(state-flow/flow ~'my-desc [original (state/gets :value)
                                                    :let [doubled (* 2 original)]]
                                         (sf.state/swap #(assoc % :value doubled)))))
        => (throws IllegalArgumentException))

  (fact "nested-flow-with exception, returns exception and state before exception"
    (let [[left right] (state-flow/run bogus-flow {:value 0})]
      @left => (throws Exception "My exception")
      right => {:meta {:description ["root" "child2"]}, :value 2}))

  (fact "flow allows do-let style binding"
    (second (state-flow/run flow-with-bindings {:value 2})) => {:meta {:description []} :value 4})

  (fact "run! throws exception"
    (state-flow/run! bogus-flow {:value 0}) => (throws Exception)))

(facts state-flow/run*

  (fact "flow with initializer"
    (second (state-flow/run* {:init (constantly {:value 0})} nested-flow))
    => {:meta  {:description []}
        :value 4})

  (fact "flow with cleanup"
    (-> (state-flow/run* {:init    (constantly {:value 0
                                                :atom  (atom 1)})
                          :cleanup #(reset! (:atom %) 0)}
          nested-flow)
        second
        :atom
        deref)
    => 0)

  (fact "flow with initializer"
    (state-flow/run* {:init   (constantly {:value 0})
                      :runner (fn [flow state]
                                [nil (state/exec flow state)])}
      nested-flow)
    => [nil {:meta  {:description []}
             :value 4}]))

(facts "on as-step-fn"
  (let [increment-two-step (state-flow/as-step-fn (state/swap #(+ 2 %)))]
    (increment-two-step 1) => 3))
