(ns state-flow.probe
  (:require [midje.sweet :as midje]
            [cats.core :as m]
            [state-flow.state :as state]))

;;
;; Helper functions for testing
;;

(def default-sleep-time 200)
(def default-times-to-try 5)

(defn ^:private retry
  "Tries at most n times, returns a vector with true and first element that succeeded
  or false and result of the first try"
  [times pred? lazy-seq]
  (let [remaining (drop-while (complement pred?) (take times lazy-seq))]
    (if (empty? remaining)
      [false (first lazy-seq)]
      [true  (first remaining)])))

(defn probe
  "evaluates state repeatedly with check-fn until check-fn succeeds or we try too many times"
  ([state check-fn]
   (probe state check-fn {:sleep-time default-sleep-time :times-to-try default-times-to-try}))
  ([state check-fn {:keys [sleep-time times-to-try]
                    :or   {sleep-time   default-sleep-time
                           times-to-try default-times-to-try}}]
   (m/mlet [world (state/get)
            :let [runs   (repeatedly #(do (Thread/sleep sleep-time) (state/eval state world)))
                  result (retry times-to-try check-fn runs)]]
     (state/return result))))
