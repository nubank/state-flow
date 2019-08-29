(ns state-flow.core
  (:refer-clojure :exclude [run!])
  (:require [cats.context :as ctx]
            [cats.core :as m]
            [cats.monad.exception :as e]
            [midje.checking.core :refer [extended-=]]
            [midje.sweet :refer :all]
            [state-flow.state :as state]
            [taoensso.timbre :as log]))

(def sleep-time 10)
(def times-to-try 100)

(defn update-description
  [old new]
  (if (or (nil? old) (empty? old))
    [new]
    (conj old new)))

(defn push-meta
  [description]
  (state/swap
   (fn [s]
     (update-in s [:meta :description] #(update-description % description)))))

(def pop-meta
  (state/swap
   (fn [s]
     (update-in s [:meta :description] #(pop %)))))

(defn description->string
  [description]
  (clojure.string/join " -> " description))

(defn get-description
  []
  (m/mlet [desc-list (state/gets #(-> % :meta :description))]
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
    (throw (IllegalArgumentException. "The first argument of the flow must be a description string")))
  (let [flows' (or flows
                   '[(state/swap identity)])]
    `(m/do-let
       (push-meta ~description)
       [ret# (m/do-let ~@flows')]
       pop-meta
       (m/return ret#))))

(defn retry
  "Tries at most n times, returns a vector with true and first element that succeeded
  or false and result of the first try"
  [times pred? lazy-seq]
  (let [remaining (drop-while (complement pred?) (take times lazy-seq))]
    (if (empty? remaining)
      [false (first lazy-seq)]
      [true  (first remaining)])))

(defn probe
  "evaluates state repeatedly with check-fn until check-fn succeeds or we try too many times"
  ([state check-fn {:keys [sleep-time times-to-try]
                    :or {sleep-time sleep-time
                         times-to-try times-to-try}}]
   (m/mlet [world (state/get)
            :let [runs   (repeatedly #(do (Thread/sleep sleep-time) (state/eval state world)))
                  result (retry times-to-try #(check-fn %) runs)]]
     (m/return result)))
  ([state check-fn]
   (probe state check-fn {:sleep-time sleep-time :times-to-try times-to-try})))

(defmacro add-desc-and-meta
  [[fname & rest] desc meta]
  (with-meta `(~fname {:midje/name ~desc} ~@rest) meta))

(defmacro verify-probe
  "Given a fact description, a state and a right-value,
  returns a State that runs left up to times-to-retry times every sleep-time ms until left-value equals right value."
  [desc state right-value metadata]
  `(ctx/with-context (ctx/infer ~state)
     (m/mlet [[_# result#] (probe ~state #(extended-= % ~right-value))]
       (do (add-desc-and-meta (fact result# => ~right-value) ~desc ~metadata)
           (m/return result#)))))

(defmacro verify
  "If left-value is a state, do fact probing. Otherwise, regular fact checking.
  Push and pop descriptions (same behaviour of flow)"
  [desc left-value right-value]
  (let [the-meta  (meta &form)
        fact-sexp `(fact ~left-value => ~right-value)]
    `(flow ~desc
       [full-desc# (get-description)]
       (if (state/state? ~left-value)
         (verify-probe full-desc# ~left-value ~right-value ~the-meta)
         (state/wrap-fn #(do (add-desc-and-meta ~fact-sexp full-desc# ~the-meta)
                             ~left-value))))))

(defn run
  [flow initial-state]
  (assert (state/state? flow) "First argument must be a State Monad")
  (assert (map? initial-state) "Initial state must be a map")
  (state/run flow initial-state))

(defn run!
  "Like run, but prints a log and throws error when flow fails with an exception"
  [flow initial-state]
  (let [result (run flow initial-state)]
    (when (e/failure? (first result))
      (let [description (->> result second :meta :description
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
