(ns state-flow.core
  (:refer-clojure :exclude [run!])
  (:require [cats.core :as m]
            [cats.monad.exception :as e]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [state-flow.state :as state]
            [taoensso.timbre :as log]))

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

(defn pop-meta
  "Returns a flow that will modify the state metadata.

  For internal use. Subject to change."
  []
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

(defn- clarify-illegal-arg [pair]
  (if-let [illegal-arg (some->> pair first :failure .getMessage (re-find #"cats.protocols\/Extract.*for (.*)$") last)]
    [(#'cats.monad.exception/->Failure
      (ex-info (format "Expected a flow, got %s" illegal-arg) {}))
     (second pair)]
    pair))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API

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

(defn top-level-description
  "Returns the description passed to the top level flow (or the
  stringified symbol passed to defflow)."
  [s]
  (-> s meta :top-level-description))

(defn as-step-fn
  "Transform a flow step into a state transition function"
  [flow]
  (fn [s] (state/exec flow s)))

(defn fmap
  "Returns a flow which applies f to return of flow."
  [f flow]
  (m/fmap f flow))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Error handlers

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Runners

(defn run
  "Given an initial-state (default {}), runs a flow and returns a tuple of
  the result of the last step in the flow and the end state."
  ([flow]
   (run flow {}))
  ([flow initial-state]
   (assert (state/state? flow) "First argument must be a flow")
   (assert (map? initial-state) "Initial state must be a map")
   (clarify-illegal-arg (state/run flow initial-state))))

(defn run*
  "Runs a flow with specified parameters. Use `run` unless you need
  the customizations `run*` supports.

  Supported keys in the first argument are:

    `init`     optional, default (constantly {}), function of no arguments that returns the initial state
    `cleanup`  optional, default identity, function of the final state used to perform cleanup, if necessary
    `runner`   optional, default `run`, function of a flow and an initial state which will execute the flow
    `on-error` optional, default `log-and-throw-error!`, function of the final result pair to be invoked
               when the first value in the pair represents an error"
  [{:keys [init cleanup runner on-error]
    :or   {init     (constantly {})
           cleanup  identity
           runner   run
           on-error log-and-throw-error!}
    :as opts}
   flow]
  (let [initial-state (init)
        pair          (runner flow initial-state)]
    (try
      (cleanup (second pair))
      (clarify-illegal-arg pair)
      (finally
        (when (e/failure? (first pair))
          (on-error pair))))))

(defn ^:deprecated run!
  "DEPRECATED. Use `run*`"
  ([flow]
   (run! flow {}))
  ([flow initial-state]
   (run* {:init (constantly initial-state)} flow)))
