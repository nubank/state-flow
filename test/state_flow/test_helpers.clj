(ns state-flow.test-helpers
  (:require [cats.core :as m]
            [cats.data :as d]
            [cats.monad.state :as state]
            [state-flow.state :as sf.state]))

(def get-value-state (state/gets get-value))

(def increment-two
  (m/mlet [world (sf.state/get)]
    (m/return (+ 2 (-> world :value)))))

(defn delayed-increment-two
  [delay-ms]
  "Changes world in the future"
  (state/state (fn [world]
                 (future (do (Thread/sleep delay-ms)
                             (swap! (:value world) + 2)))
                 (d/pair nil world))))

