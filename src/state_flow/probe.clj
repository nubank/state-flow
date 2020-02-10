(ns state-flow.probe
  (:require [cats.core :as m]
            [state-flow.state :as state]))

;;
;; Helper functions for testing
;;

(def default-sleep-time 200)
(def default-times-to-try 5)

(defn ^:private check
  "Applies check-fn to the return value of a step, returns the check result and the original value in a map"
  [flow check-fn]
  (m/mlet [value flow]
    (state/return {:check-result (check-fn value)
                   :value        value})))

(defn ^:private with-delay
  "Adds a delay before the step is run"
  [flow delay]
  (m/>> (state/wrap-fn #(Thread/sleep delay)) flow))

(defn ^:private sequence-while*
  "Like cats.core/sequence but with short circuiting when pred is satisfied by the return value of a flow"
  [pred acc flows]
  (if (empty? flows)
    acc
    (m/mlet [result (first flows)
             results acc]
      (if (pred result)
        (state/return (conj results result))
        (sequence-while* pred (state/return (conj results result)) (rest flows))))))

(defn ^:private sequence-while
  [pred flows]
  (sequence-while* pred (state/return []) flows))

(defn probe
  "Internal use only. Evaluates step repeatedly with check-fn until check-fn succeeds or we try too many times"
  ([flow check-fn]
   (probe flow check-fn {}))
  ([flow check-fn {:keys [sleep-time times-to-try]
                   :or   {sleep-time   default-sleep-time
                          times-to-try default-times-to-try}}]
   (sequence-while :check-result
                   (cons (check flow check-fn)
                         (repeat (dec times-to-try) (with-delay (check flow check-fn) sleep-time))))))
