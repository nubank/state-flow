(ns state-flow.core
  (:refer-clojure :exclude [run!])
  (:require [cats.core :as m]
            [cats.monad.exception :as e]
            [clj-commons.format.exceptions :as exceptions]
            [clojure.pprint :as pp]
            [clojure.string :as string]
            [state-flow.internals.description :as description]
            [state-flow.state :as state]))

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
  [description {:keys [line ns file call-site-meta]}]
  (let [meta-map (cond-> {:description description
                          :ns          ns}
                   call-site-meta (assoc :call-site-meta call-site-meta)
                   line (assoc :line line)
                   file (assoc :file file))]
    (modify-meta
     (fn [m] (-> m
                 (update :top-level-description #(or % description))
                 (update :description-stack (fnil conj []) meta-map))))))

(defn pop-meta
  "Returns a flow that will modify the state metadata.

  For internal use. Subject to change."
  []
  (modify-meta update :description-stack pop))

;;
;; Begin description utils
;;

(defn description->file
  [{:keys [file]}]
  (when file (last (string/split file #"/"))))

(defn- format-single-description
  [{:keys [line description file] :as m}]
  (let [filename (description->file m)]
    (str description
         (when line
           (if filename
             (format " (%s:%s)" filename line)
             ;; TODO: we can probably pull filename info from previous stack entries
             (format " (line %s)" line))))))

(defn- remove-non-terminal-call-site-meta
  "non-terminal call-site meta is usually redudant with the meta-data provided
  by `flow` forms.

  It is thus mostly useful at the end of the call-stack, as a way to get more
  precise line information for issues that arise after the last `flow` form."
  [stack]
  (let [call-site-meta?      #(contains? % :call-site-meta)
        last-call-site-metas (->> stack
                                  reverse
                                  (take-while call-site-meta?)
                                  reverse)
        filtered-stack        (-> (remove call-site-meta? stack)
                                  (concat last-call-site-metas))]
    ;; `into` to preserve the `stack` sequence type
    (into (empty stack) filtered-stack)))

(defn format-description
  [stack]
  (->> stack
       remove-non-terminal-call-site-meta
       (map format-single-description)
       (string/join " -> ")))

(defn description-stack
  "Returns the list of descriptions in the current stack.

  Example:
  (description-stack s)
  ;; =>
  [{:description \"my test\"
    :file \"my-test-file.clj\"
    :line 42}]"
  [s]
  (-> s meta :description-stack))

(defn- string-expr? [x]
  (or (string? x)
      (and (sequential? x)
           (contains? #{'str `str 'format `format} (first x)))))

(defn- state->current-description [s]
  (-> (description-stack s)
      format-description))

(defn current-description
  "Returns a flow that returns the description as of the point of execution.

  For internal use. Subject to change."
  []
  (state/gets state->current-description))

;;
;; End description utils
;;

(def fail-fast?
  "Should the flow stop after the first failing assertion?

  For internal use. Subject to change."
  (state/gets (comp :fail-fast? meta)))

(defn- clarify-illegal-arg [pair]
  (if-let [illegal-arg (some->> pair first :failure .getMessage (re-find #"cats.protocols\/Extract.*for (.*)$") last)]
    [(#'cats.monad.exception/->Failure
      (ex-info (format "Expected a flow, got %s" illegal-arg) {}))
     (second pair)]
    pair))

(defn apply-before-flow-hook
  []
  (m/do-let
   [hook (state/gets (comp :before-flow-hook meta))]
   (state/modify (or hook identity))))

(defn- push-abbr-meta [flow]
  `(push-meta ~(description/abbr-sexpr flow)
              ~(assoc (meta flow)
                      :call-site-meta true)))

(defn- flow-expr? [expr]
  (and (coll? expr)
       (or (= 'flow (first expr))
           (= `flow (first expr)))))

(defn annotate-with-line-meta [flows]
  (let [annotated-flows (reduce (fn [acc flow]
                                  (if (flow-expr? flow)
                                    (conj acc flow) ;; `flow`s push their own meta data
                                    (into [] (concat acc [(push-abbr-meta flow) flow `(pop-meta)]))))
                                []
                                flows)]
    ;; to preserve the return value, exclude terminal pop-meta's
    (if (= `(pop-meta) (last annotated-flows))
      (butlast annotated-flows)
      annotated-flows)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API

(defn flow*
  "For use in macros that create flows. Not private (appropriate for
  helper libraries, for example), but not intended for use directly in
  flows.

  Creates a flow which is a composite of flows. The calling macro should
  provide (meta &form) as `:caller-meta` in order to support accurate line
  number reporting."
  [{:keys [description caller-meta]} & flows]
  (when-not (string-expr? description)
    (throw (IllegalArgumentException. "The first argument to flow must be a description string")))
  (when (vector? (last flows))
    (throw (ex-info "The last argument to flow must be a flow/step, not a binding vector." {})))
  (let [flow-meta       caller-meta
        annotated-flows (annotate-with-line-meta
                         (or flows `[(m/return nil)]))
        pop-line-meta   (if (flow-expr? (last annotated-flows))
                          '()
                          `((pop-meta)))]
    `(m/do-let
      (push-meta ~description ~flow-meta)
      (apply-before-flow-hook)
      [ret# (m/do-let ~@annotated-flows)]
      ~@pop-line-meta
      (pop-meta)
      (m/return ret#))))

(defmacro flow
  "Creates a flow which is a composite of flows."
  {:style/indent :defn}
  [description & flows]
  (apply flow* {:description description
                :caller-meta (assoc (meta &form)
                                    :file *file*
                                    :ns (str *ns*))}
         flows))

(defn top-level-description
  "Returns the description passed to the top level flow (or the
  stringified symbol passed to defflow)."
  [s]
  (-> s meta :top-level-description))

(defn ^:deprecated as-step-fn
  "DEPRECATED with no replacement."
  [flow]
  (fn [s] (state/exec flow s)))

(defn runner
  "Creates a flow that returns the runner (function). Useful for
  helpers that need to access the runner."
  []
  (state/gets (comp :runner meta)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Error handlers

(defn ignore-error
  "No-op error handler that ignores the error."
  [pair]
  pair)

(defn log-error
  "Error handler that logs error to clojure.core/*out* and returns pair."
  [pair]
  (let [throwable   (m/extract (first pair))
        description (state->current-description (second pair))
        message     (str "Flow " "\"" description "\"" " failed with exception")]
    (println (str message "\n" (exceptions/format-exception throwable)))
    pair))

(defn throw-error!
  "Error handler that throws the error."
  [pair]
  (let [description (state->current-description (second pair))
        message     (str "Flow " "\"" description "\"" " failed with exception")
        exception   (ex-info message {} (m/extract (first pair)))]
    (doto exception
      (.setStackTrace (into-array (take 3 (into [] (.getStackTrace exception))))))
    (throw exception)))

(defn ^:deprecated log-and-throw-error!
  "DEPRECATED: Use (comp throw-error! log-error) instead. "
  [pair]
  (-> pair log-error throw-error!))

(def default-stack-trace-exclusions
  [#"^nrepl\."
   #"^cats\."
   #"^java\.lang\.reflect"
   #"^java\.lang\.Thread"
   #"^clojure\.main\$repl"
   #"^clojure\.lang"])

(defn- filter-stack-trace*
  "Given a seq of exclusions (regexen) and a StackTraceElement array,
  returns a new StackTraceElement array which excludes all elements
  whose class names match any of the exclusions."
  [exclusions stack-trace]
  (let [frames (into [] stack-trace)]
    (->> (into [(first frames)]
               (remove
                (fn [frame]
                  (some #(re-find % (.getClassName frame)) exclusions))
                (rest frames)))
         into-array)))

(defn deep-stack-trace-filter! [ex exclusions]
  (doto ex
    (.setStackTrace
     (filter-stack-trace* exclusions (.getStackTrace ex))))
  (when-let [cause (.getCause ex)]
    (recur cause exclusions)))

(defn filter-stack-trace
  "Returns an error handler which, if the first element in the pair is
  a failure, returns the pair with the failure's stack-trace
  filtered, else returns the pair as/is.

  exclusions (default default-stack-trace-exclusions) is a sequence of
  regular expressions used to match class names in stack trace frames.
  Matching frames are excluded."
  ([]
   (filter-stack-trace default-stack-trace-exclusions))
  ([exclusions]
   (fn [pair]
     (if-let [failure (some->> pair first :failure)]
       (do (deep-stack-trace-filter! failure exclusions)
           [(#'cats.monad.exception/->Failure failure)
            (second pair)])
       pair))))

(defn- unwrap-assertion-failure-value [pair]
  (let [assertion-result? (comp not nil? :probe/sleep-time)
        exception-data    (and (vector? pair)
                               (-> pair first e/exception?)
                               (-> pair first m/extract ex-data))]
    (if (assertion-result? exception-data)
      [exception-data (second pair)]
      pair)))

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
   (clarify-illegal-arg (state/run flow
                                   (vary-meta initial-state update :runner (fnil identity run))))))

(defn run*
  "Runs a flow with specified parameters. Use `run` unless you need
  the customizations `run*` supports.

  Supported keys in the first argument are:

    `:fail-fast?`       optional, default `false`, when set to `true`, the flow stops running after the first failing assertion
    `:init`             optional, default (constantly {}), function of no arguments that returns the initial state
    `:cleanup`          optional, default `identity`, function of the final state used to perform cleanup, if necessary
    `:runner`           optional, default `run`, function of a flow and an initial state which will execute the flow
    `:before-flow-hook` optional, default `identity`, function from state to new-state that is applied before excuting a flow, after flow description is updated.
    `:on-error`         optional, function of the final result pair to be invoked when the first value in the pair represents an error, default:
                        `(comp throw-error!
                              log-error
                              (filter-stack-trace default-stack-trace-exclusions))`"
  [{:keys [init cleanup runner on-error fail-fast? before-flow-hook]
    :or   {init                   (constantly {})
           cleanup                identity
           runner                 run
           fail-fast?             false
           before-flow-hook       identity
           on-error               (comp throw-error!
                                        log-error
                                        (filter-stack-trace default-stack-trace-exclusions))}}
   flow]
  (let [init-state+meta (vary-meta (init)
                                   assoc
                                   :runner runner
                                   :before-flow-hook before-flow-hook
                                   :fail-fast? fail-fast?
                                   :init init)
        pair            (-> flow
                            (runner init-state+meta)
                            clarify-illegal-arg
                            unwrap-assertion-failure-value)]
    (try
      (cleanup (second pair))
      pair
      (finally
        (when (e/failure? (first pair))
          (on-error pair))))))

(defn ^:deprecated run!
  "DEPRECATED. Use `run*`"
  ([flow]
   (run! flow {}))
  ([flow initial-state]
   (run* {:init (constantly initial-state)} flow)))
