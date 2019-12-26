(ns state-flow.test-helpers
  (:require [cats.core :as m]
            [cats.data :as d]
            [cats.monad.state :as state]
            [state-flow.state :as sf.state]))

(def get-value (comp deref :value))
(def get-value-state (state/gets get-value))

(def increment-two
  (m/mlet [state (sf.state/get)]
    (m/return (+ 2 (-> state :value)))))

(defn delayed-increment-two
  [delay-ms]
  "Changes state in the future"
  (state/state (fn [state]
                 (future (do (Thread/sleep delay-ms)
                             (swap! (:value state) + 2)))
                 (d/pair nil state))))
