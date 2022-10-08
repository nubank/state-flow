(ns state-flow.presentation
  (:require [state-flow.assertions.matcher-combinators :refer [match?]]
            [state-flow.api :as api :refer [flow]]))

"""
The primitive steps

(api/get-state getter) => (fn [s] [(getter s) s])

(api/swap-state setter) => (fn [s] [s (setter s)])

(api/return value) => (fn [s] [value s])
"""

"""
Runner
"""
(def get-value (api/get-state :value))
(api/run get-value {:value 4})
; => [4 {:value 4}]

(def inc-value (api/swap-state #(update % :value inc)))
(api/run inc-value {:value 4})
; => [{:value 4} {:value 5}]

"""
The flow macro

(flow <description> <bindings/flow/primitive>+)
"""
(def my-first-flow
  (flow "my first flow"
    (flow "bla"
      inc-value)))

(api/run my-first-flow {:value 4})
; => [{:value 4} {:value 5}]

(def inc-two
  (flow "inc 2 times"
    inc-value
    inc-value))
(api/run inc-two {:value 4})
; => [{:value 5} {:value 6}]

"""
Bindings
"""
(def with-bindings
  (flow "get double value"
    inc-value
    [value get-value
     :let [value2 (* 2 value)]]
    (api/return value2)))
(api/run with-bindings {:value 4})
; => [10 {:value 5}]

"""
Tests

(match? <description> <value/flow> <matcher>)
"""
(def with-assertions
  (flow "with assertions"
    inc-value
    [value get-value]
    (match? 5 value)

    inc-value
    [world (api/get-state identity)]
    (match? 7 get-value)))
(api/run with-assertions {:value 4})

"""
Asynchronous tests
"""
(def delayed-inc-value
  (api/swap-state (fn [world]
                    (future (do (Thread/sleep 200)
                                (swap! (:value world) inc)))
                    world)))

(def get-value-deref
  (api/get-state (comp deref :value)))

(def with-async-fail
  (flow "with async"
    delayed-inc-value
    [value get-value-deref]
    (match? 5 value)))

(api/run with-async-fail {:value (atom 4)})

(def with-async-success
  (flow "with async"
    delayed-inc-value
    (match? 5 get-value-deref {:times-to-try 10})))

(api/run with-async-success {:value (atom 4)})
;=> [5 {:value (atom 5)}]
