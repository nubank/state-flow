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
  [step check-fn]
  (m/mlet [value step]
    (state/return {:check-result (check-fn value)
                   :value        value})))

(defn ^:private with-delay
  "Adds a delay before the step is run"
  [step delay]
  (m/>> (state/wrap-fn #(Thread/sleep delay)) step))

(defn ^:private sequence-while*
  "Like cats.core/sequence but with short circuiting when pred is satisfied by the return value of a step"
  [pred acc steps]
  (if (empty? steps)
    acc
    (m/mlet [result (first steps)
             results acc]
      (if (pred result)
        (state/return (conj results result))
        (sequence-while* pred (state/return (conj results result)) (rest steps))))))

(defn ^:private sequence-while
  [pred steps]
  (sequence-while* pred (state/return []) steps))

(defn probe
  "Internal use only. Evaluates step repeatedly with check-fn until check-fn succeeds or we try too many times"
  ([step check-fn]
   (probe step check-fn {}))
  ([step check-fn {:keys [sleep-time times-to-try]
                   :or   {sleep-time   default-sleep-time
                          times-to-try default-times-to-try}}]
   (sequence-while :check-result
                   (cons (check step check-fn)
                         (repeat (dec times-to-try) (with-delay (check step check-fn) sleep-time))))))
