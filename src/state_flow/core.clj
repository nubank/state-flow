(ns state-flow.core
  (:refer-clojure :exclude [run!])
  (:require [clojure.string :as str]
            [cats.core :as m]
            [cats.data :as d]
            [cats.monad.exception :as e]
            [state-flow.state :as state]
            [taoensso.timbre :as log]
            [clojure.pprint :as pp])
  (:import java.lang.Throwable))

;; From time to time we see the following error when trying to pretty-print
;; Failure records:
;;
;;   Unhandled java.lang.IllegalArgumentException
;;   Multiple methods in multimethod 'simple-dispatch' match dispatch
;;   value: class cats.monad.exception.Failure -> interface
;;   clojure.lang.IDeref and interface clojure.lang.IPersistentMap, and
;;   neither is preferred
;;
;; This prevents that from happening:
(defmethod pp/simple-dispatch cats.monad.exception.Failure [f]
  (pr f))

(defn modify-meta
  "Returns a monad that will apply vary-meta to the world.

  For internal use. Subject to change."
  [f & args]
  (state/modify (fn [s] (apply vary-meta s f args))))

(defn push-meta
  "Returns a flow that will modify the state metadata.

  For internal use. Subject to change."
  [description {:keys [line]}]
  (modify-meta
   (fn [m] (-> m
               (update :top-level-description #(or % description))
               (update :description-stack (fnil conj []) (str description
                                                              (when line
                                                                (format " (line %s)" line))))))))

(defn pop-meta []
  "Returns a flow that will modify the state metadata.

  For internal use. Subject to change."
  (modify-meta update :description-stack pop))

(defn ^:private format-description
  [strs]
  (str/join " -> " strs))

(defn ^:private description-stack [s]
  (-> s meta :description-stack))

(defn ^:private string-expr? [x]
  (or (string? x)
      (and (sequential? x)
           (or (= (first x) 'str)
               (= (first x) 'clojure.core/str)))))

(defn ^:private state->current-description [s]
  (-> (description-stack s)
      format-description))

(defn current-description
  "Returns a flow that returns the description as of the point of execution.

  For internal use. Subject to change."
  []
  (state/gets state->current-description))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API

(defn top-level-description
  "Returns the description passed to the top level flow (or the
  stringified symbol passed to defflow)."
  [s]
  (-> s meta :top-level-description))

(defn flow* [{:keys [description caller-meta]} & flows]
  (when-not (string-expr? description)
    (throw (IllegalArgumentException. "The first argument to flow must be a description string")))
  (let [flow-meta caller-meta
        flows'    (or flows `[(m/return nil)])]
    `(m/do-let
      (push-meta ~description ~flow-meta)
      [ret# (m/do-let ~@flows')]
      (pop-meta)
      (m/return ret#))))

(defmacro flow
  "Defines a flow"
  {:style/indent :defn}
  [description & flows]
  (apply flow* {:description description
                :caller-meta (meta &form)}
         flows))

(defn run
  "Given an initial-state (default {}), runs a flow and returns a pair of
  the result of the last step in the flow and the end state."
  ([flow]
   (run flow {}))
  ([flow initial-state]
   (assert (state/state? flow) "First argument must be a flow")
   (assert (map? initial-state) "Initial state must be a map")
   (let [pair (state/run flow initial-state)]
     (if-let [illegal-arg (some->> pair first :failure .getMessage (re-find #"cats.protocols\/Extract.*for (.*)$") last)]
       (d/pair (#'cats.monad.exception/->Failure
                (ex-info (format "Expected a flow, got %s" illegal-arg) {}))
               (second pair))
       pair))))

(defn log-and-throw-error!
  "Error handler that logs the error and throws an exception to notify the flow
  has failed."
  [pair]
  (let [description (state->current-description (second pair))
        message     (str "Flow " "\"" description "\"" " failed with exception")]
    (log/info (m/extract (first pair)) message)
    (throw (ex-info message {} (m/extract (first pair))))))

(defn ignore-error
  "No-op error handler that simply ignores the error."
  [pair]
  pair)

(defn- run-policy-on-error!
  "If flow fails with an exception, runs the supplied error policy"
  [pair on-error]
  (when (e/failure? (first pair))
    (on-error pair)))

(defn run*
  "Run a flow with specified parameters

  Receives optional parameter maps
  `init`, a function with no arguments that returns the initial state
  `cleanup`, function receiving the final state to perform cleanup if necessary
  `runner`, function that will receive a flow and an initial state and execute the flow
  `on-error`, funtion that, when a flow results in an error, will receive the final result pair. Defaults to `log-and-throw-error!`"
  [{:keys [init cleanup runner on-error]
    :or   {init     (constantly {})
           cleanup  identity
           runner   run
           on-error log-and-throw-error!}}
   flow]
  (let [initial-state (init)
        pair          (runner flow initial-state)]
    (try
      (cleanup (second pair))
      pair
      (finally
        (run-policy-on-error! pair on-error)))))

(defn ^:deprecated run!
  "DEPRECATED. Use `run*`"
  ([flow]
   (run! flow {}))
  ([flow initial-state]
   (run* {:init (constantly initial-state)} flow)))

(defn as-step-fn
  "Transform a flow step into a state transition function"
  [flow]
  (fn [s] (state/exec flow s)))
