(ns state-flow.core
  (:refer-clojure :exclude [run!])
  (:require [clojure.string :as str]
            [cats.core :as m]
            [cats.data :as d]
            [cats.monad.exception :as e]
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
(defmethod clojure.pprint/simple-dispatch cats.monad.exception.Failure [f]
  (pr f))

(defn push-meta
  "Returns a flow that will modify the state metadata.

  For internal use. Subject to change."
  [description {:keys [line]}]
  (state/modify
   (fn [s]
     (-> s
         (vary-meta update :top-level-description #(or % description))
         (vary-meta update :description-stack (fnil conj []) (str description
                                                                  (when line
                                                                    (format " (line %s)" line))))))))

(def pop-meta
  "Returns a flow that will modify the state metadata.

  For internal use. Subject to change."
  (state/modify
   (fn [s]
     (vary-meta s update :description-stack pop))))

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

(defn current-description
  "Returns a flow that returns the description as of the point of execution.

  For internal use. Subject to change."
  []
  (m/mlet [desc-list (state/gets description-stack)]
          (m/return (format-description desc-list))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API

(defn top-level-description
  "Returns the description passed to the top level flow (or the
  stringified symbol passed to defflow)."
  [s]
  (-> s meta :top-level-description))

(def ^:private abbr-size 15)
(defn ellipsify [expr-str]
  (let [short-expr (subs expr-str 0 (- abbr-size 3))]
    (case (first expr-str)
      \( (str short-expr "...)")
      \[ (str short-expr "...]")
      (str short-expr "..."))))

(defn abbr-sexpr [expr]
  (let [expr-str   (str expr)
        short-expr (if (< abbr-size (count expr-str))
                     (ellipsify expr-str)
                     expr-str)]
    (str "`" short-expr "`")))

(defn annote-with-line-meta [flows]
  (when-let [subflow-lines (->> flows
                                (map (fn [f] `(push-meta ~(abbr-sexpr f)
                                                         ~(meta f))))
                                seq)]
    (interleave subflow-lines
                flows
                (repeat `pop-meta))))

(defmacro flow
  "Defines a flow"
  {:style/indent :defn}
  [description & flows]
  (when-not (string-expr? description)
     (throw (IllegalArgumentException. "The first argument to flow must be a description string")))
  (let [flow-meta     (meta &form)
        flows'        (or (annote-with-line-meta flows)
                          `[(m/return nil)])]
    `(m/do-let
      (push-meta ~description ~flow-meta)
      [ret# (m/do-let ~@flows')]
      pop-meta
      (m/return ret#))))

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

(defn run!
  "Like run, but prints a log and throws an error when the flow fails with an exception"
  ([flow]
   (run! flow {}))
  ([flow initial-state]
   (let [pair (run flow initial-state)]
     (when (e/failure? (first pair))
       (let [description (->> pair second description-stack format-description)
             message (str "Flow " "\"" description "\"" " failed with exception")]
         (log/info (m/extract (first pair)) message)
         (throw (ex-info message {}))))
     pair)))

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
