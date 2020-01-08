(ns state-flow.core
  (:refer-clojure :exclude [run!])
  (:require [cats.context :as ctx]
            [cats.core :as m]
            [cats.monad.exception :as e]
            [state-flow.state :as state]
            [taoensso.timbre :as log]))

(defn push-description
  [description-log new-description]
  (if (nil? description-log)
    [[new-description]]
    (conj description-log (conj (last description-log) new-description))))

(defn pop-description
  [description-log]
  (conj description-log (pop (last description-log))))

(defn update-meta [s k & args]
  (with-meta s (apply update (meta s) k args)))

(defn push-meta
  [description]
  (state/modify
   (fn [s]
     (update-meta s :description push-description description))))

(def pop-meta
  (state/modify
   (fn [s]
     (update-meta s :description pop-description))))

(defn description->string
  [description]
  (clojure.string/join " -> " description))

(defn get-description
  []
  (m/mlet [desc-list (state/gets #(-> % meta :description last))]
    (m/return (description->string desc-list))))

(defn string-expr? [x]
  (or (string? x)
      (and (sequential? x)
           (or (= (first x) 'str)
               (= (first x) 'clojure.core/str)))))

(defmacro flow
  "Defines a flow"
  {:style/indent :defn}
  [description & flows]
  (when-not (string-expr? description)
     (throw (IllegalArgumentException. "The first argument to flow must be a description string")))
  (let [flows' (or flows
                   `[(m/return nil)])]
    `(m/do-let
      (push-meta ~description)
      [ret# (m/do-let ~@flows')]
      pop-meta
      (m/return ret#))))

(defn run
  [flow initial-state]
  (assert (state/state? flow) "First argument must be a flow")
  (assert (map? initial-state) "Initial state must be a map")
  (state/run flow initial-state))

(defn run!
  "Like run, but prints a log and throws an error when the flow fails with an exception"
  [flow initial-state]
  (let [result (run flow initial-state)]
    (when (e/failure? (first result))
      (let [description (->> result second meta :description last
                             description->string)
            message (str "Flow " "\"" description "\"" " failed with exception")]
        (log/info (m/extract (first result)) message)
        (throw (ex-info message {}))))
    result))

(defn run*
  "Run a flow with specified parameters

  Receives optional parameter maps
  `init`, a function with no arguments that returns the initial state.
  `cleanup`, function receiving the final state to perform cleanup if necessary
  `runner`, function that will receive a flow and an initial state and execute the flow"
  [{:keys [init cleanup runner]
    :or   {init    (constantly {})
           cleanup identity
           runner  run!}}
   flow]
  (let [initial-state              (init)
        [_ final-state :as result] (runner flow initial-state)]
    (cleanup final-state)
    result))

(defn as-step-fn
  "Transform a flow step into a state transition function"
  [flow]
  (fn [s] (state/exec flow s)))
