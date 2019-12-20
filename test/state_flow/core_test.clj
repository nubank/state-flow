(ns state-flow.core-test
  (:require [cats.core :as m]
            [cats.monad.state :as state]
            [matcher-combinators.midje :refer [match]]
            [midje.sweet :refer :all]
            [state-flow.core :as state-flow]
            [state-flow.midje :as midje]
            [state-flow.state :as sf.state]))

(defn double-key [k]
  (state/swap (fn [w] (update w k #(* 2 %)))))

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
    (sf.state/modify #(assoc % :value doubled))
    (midje/verify "value is doubled"
                  (state/gets #(-> % :value)) doubled)))

(def bogus-flow
  (state-flow/flow "root"
    (state-flow/flow "child1" increment-two-value)
    (state-flow/flow "child2" bogus increment-two-value)))

(def empty-flow
  (state-flow/flow "empty"))

(fact "on push-meta"
  (state/exec (m/>> (#'state-flow/push-meta "mydesc")
                    (#'state-flow/push-meta "mydesc2")) {}) => {:meta {:description [["mydesc"]
                                                                                     ["mydesc" "mydesc2"]]}})

(facts "on run flow"
  (fact "run flow of single step"
    (state/exec (state-flow/flow "single step" increment-two-value) {:value 0}) => {:meta  {:description [["single step"]
                                                                                                          []]}
                                                                                    :value 2})
  (fact "flow with two steps"
    (second (state-flow/run (state-flow/flow "two step flow"
                              (state-flow/flow "first step" increment-two-value)
                              (state-flow/flow "second step" increment-two-value))
              {:value 0})) => {:meta                                         {:description [["two step flow"]
                                                                                            ["two step flow" "first step"]
                                                                                            ["two step flow"]
                                                                                            ["two step flow" "second step"]
                                                                                            ["two step flow"]
                                                                                            []]} :value 4})

  (fact "empty flow"
    (state-flow/run empty-flow {}) => irrelevant)

  (fact "flow without description fails at macro-expansion time"
    (macroexpand `(state-flow/flow
                    (sf.state/return {})))
    => (throws IllegalArgumentException))

  (fact "flow with a `(str ..)` expr for the description is fine"
    (macroexpand `(state-flow/flow (str "foo") [original (state/gets :value)
                                                :let [doubled (* 2 original)]]
                    (sf.state/modify #(assoc % :value doubled))))
    => list?)

  (fact "but flows with an expression that resolves to a string also aren't valid,
        due to resolution limitations at macro-expansion time"
    (let [my-desc "trolololo"]
      (macroexpand `(state-flow/flow ~'my-desc [original (state/gets :value)
                                                :let [doubled (* 2 original)]]
                      (sf.state/modify #(assoc % :value doubled)))))
    => (throws IllegalArgumentException))

  (fact "nested-flow-with exception, returns exception and state before exception"
    (let [[left right] (state-flow/run bogus-flow {:value 0})]
      @left => (throws Exception "My exception")
      right => {:meta  {:description [["root"]
                                      ["root" "child1"]
                                      ["root"]
                                      ["root" "child2"]]}
                :value 2}))

  (fact "flow allows do-let style binding"
    (second (state-flow/run flow-with-bindings {:value 2}))
    => (match {:value 4}))

  (fact "run! throws exception"
    (state-flow/run! bogus-flow {:value 0}) => (throws Exception)))

(facts state-flow/run*

  (fact "flow with initializer"
    (second (state-flow/run* {:init (constantly {:value 0})} nested-flow))
    => (match {:value 4}))

  (fact "flow with cleanup"
    (-> (state-flow/run* {:init    (constantly {:value 0
                                                :atom  (atom 1)})
                          :cleanup #(reset! (:atom %) 0)}
                         nested-flow)
        second
        :atom
        deref)
    => 0)

  (fact "flow with custom runner"
    (second (state-flow/run* {:init   (constantly {:value 0})
                             :runner (fn [flow state]
                                       [nil (state/exec flow state)])}
             nested-flow))
    => (match {:value 4})))

(facts "on as-step-fn"
  (let [increment-two-step (state-flow/as-step-fn (state/swap #(+ 2 %)))]
    (increment-two-step 1) => 3))
