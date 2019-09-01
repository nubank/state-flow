(ns state-flow.presentation
  (:require [state-flow.core :as state-flow :refer [flow]]
            [state-flow.cljtest :refer [match?]]
            [state-flow.state :as state]
            [cats.core :as m]))

"""
The primitive steps

(state/gets getter) => (fn [s] [(getter s) s])

(state/swap setter) => (fn [s] [s (setter s)])

(m/return value) => (fn [s] [value s])
"""

"""
Runner
"""
(def get-value (state/gets :value))
(state-flow/run! get-value {:value 4})
; => [4 {:value 4}]

(def inc-value (state/swap #(update-in % [:value] inc)))
(state-flow/run! inc-value {:value 4})
; => [{:value 4} {:value 5}]

"""
The flow macro

(flow <description> <bindings/flow/primitive>+)
"""
(def my-first-flow
  (flow "my first flow"
    (flow "bla"
      inc-value)))

(state-flow/run! my-first-flow {:value 4})
; => [{:value 4 :meta {:description ["my first flow"]}} {:value 5 :meta {:description []}}]

(def inc-two
  (flow "inc 2 times"
    inc-value
    inc-value))
(state-flow/run! inc-two {:value 4})
; => [{:value 5 :meta {:description ["inc 2 times"]}} {:value 6 :meta {:description []}}]

"""
Bindings
"""
(def with-bindings
  (flow "get double value"
    inc-value
    [value get-value
     :let [value2 (* 2 value)]]
    (m/return value2)))
(state-flow/run! with-bindings {:value 4})
; => [8 {:value 4 :meta {:description []}}]

"""
Tests

(match? <description> <value/flow> <matcher>)
"""
(def with-assertions
  (flow "with assertions"
    inc-value
    [value get-value]
    (match? "is 5" value 4)

    inc-value
    [world (state/gets identity)]
    (match? "is 6" world {:value 6})))
(state-flow/run! with-assertions {:value 4})

"""
Asynchronous tests
"""
(def delayed-inc-value
  (state/swap (fn [world]
                (future (do (Thread/sleep 200)
                            (swap! (:value world) inc)))
                world)))

(def get-value-deref
  (state/gets (comp deref :value)))

(def with-async-fail
  (flow "with async"
    delayed-inc-value
    [value get-value-deref]
    (match? "is 5" value 5)))

(state-flow/run! with-async-fail {:value (atom 4)})

(def with-async-success
  (flow "with async"
    delayed-inc-value
    (match? "is 5" get-value-deref 5)))

(state-flow/run! with-async-success {:value (atom 4)})
;=> [5 {:value (atom 5)}]


