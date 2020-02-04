(ns state-flow.probe
  (:require [cats.core :as m]
            [state-flow.state :as state]))

;;
;; Helper functions for testing
;;

(def default-sleep-time 200)
(def default-times-to-try 5)

(defn ^:private check
  "Applies check-fn to return value of a flow, returns the check result and the original value in a map"
  [flow check-fn]
  (m/mlet [value flow]
    (state/return {:check-result (check-fn value)
                   :value        value})))

(defn ^:private with-delay
  "Adds a delay when the flow is run"
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

(defn ^:private probe*
  "evaluates state repeatedly with check-fn until check-fn succeeds or we try too many times"
  ([flow check-fn]
   (probe* flow check-fn {}))
  ([flow check-fn {:keys [sleep-time times-to-try]
                   :or   {sleep-time   default-sleep-time
                          times-to-try default-times-to-try}}]
   (sequence-while :check-result (repeat times-to-try (with-delay (check flow check-fn) sleep-time)))))

(defn ^:private probe-return
  [{:keys [check-result value]}]
  [(boolean check-result) value])

(defn probe
  "evaluates state repeatedly with check-fn until check-fn succeeds or we try too many times"
  ([state check-fn]
   (probe state check-fn {}))
  ([flow check-fn {:keys [sleep-time times-to-try]
                   :or   {sleep-time   default-sleep-time
                          times-to-try default-times-to-try}}]
   (m/fmap (comp probe-return last) (probe* flow check-fn {:sleep-time sleep-time :times-to-try times-to-try}))))
