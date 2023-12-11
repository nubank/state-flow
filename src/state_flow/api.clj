(ns state-flow.api
  "This namespace provides the user API, that is, the set of public functions that one can use for writing flows."
  (:refer-clojure :exclude [for when])
  (:require [cats.core :as m]
            [state-flow.assertions.matcher-combinators]
            [state-flow.cljtest]
            [state-flow.core]
            [state-flow.state]))

(def ^{:doc      "Creates a flow which is a composite of flows."
       :arglists '([description & flows])
       :macro    true}
  flow
  #'state-flow.core/flow)

(def ^{:arglists '([flow] [flow initial-state]),
       :doc      "Given an initial-state (default {}), runs a flow and returns a tuple of
       the result of the last step in the flow and the end state."}
  run
  #'state-flow.core/run)

(def ^{:arglists '([{:keys [init cleanup runner on-error fail-fast? before-flow-hook],
                     :or   {init             (constantly {}),
                            cleanup          identity,
                            runner           run,
                            fail-fast?       false,
                            before-flow-hook identity,
                            on-error         (comp throw-error! log-error (filter-stack-trace default-stack-trace-exclusions))}}
                    flow]),
       :doc      "Runs a flow with specified parameters. Use `run` unless you need
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
                                   (filter-stack-trace default-stack-trace-exclusions))`"}
  run*
  #'state-flow.core/run*)

(def ^{:deprecated true,
       :arglists   '([pair]),
       :doc        "DEPRECATED: Use (comp throw-error! log-error) instead. "}
  log-and-throw-error!
  #'state-flow.core/log-and-throw-error!)

(def ^{:arglists '([pair]),
       :doc      "No-op error handler that ignores the error."}
  ignore-error
  #'state-flow.core/ignore-error)

(def ^{:arglists '([pair]),
       :doc      "No-op error handler that ignores the error."}
  ignore-error
  #'state-flow.core/ignore-error)

(def ^{:arglists '([my-fn]),
       :doc      "Creates a flow that invokes a function of no arguments and returns the
       result. Used to invoke side effects e.g.

          (state-flow.core/invoke #(Thread/sleep 1000))"}
  invoke
  #'state-flow.state/invoke)

(def ^{:arglists '([v]),
       :doc      "Creates a flow that returns v. Use this as the last
       step in a flow that you want to reuse in other flows, in
       order to clarify the return value, e.g.

         (def increment-count
           (flow \"increments :count and returns it\"
             (state/modify update :count inc)
             [new-count (state/gets :count)]
             (state-flow/return new-count)))"}
  return
  #'state-flow.state/return)

(def ^{:doc      "Creates a flow that returns the application of f to the return of flow",
       :arglists '([f flow])}
  fmap
  #'state-flow.state/fmap)

(def ^{:arglists '([e flow]),
       :doc      "Given an expression `e` and a flow, if the expression is logical true, return the flow. Otherwise, return nil in a monadic context."}
  when
  #'state-flow.state/when)

(def ^{:arglists '([name & flows] [name parameters & flows]),
       :doc      "Creates a flow and binds it a Var named by name",
       :macro    true}
  defflow
  #'state-flow.cljtest/defflow)

(def ^{:arglists '([expected actual & [{:keys [times-to-try sleep-time], :as params}]]),
       :doc      "Builds a state-flow step which uses matcher-combinators to make an
       assertion.

       `expected` can be a literal value or a matcher-combinators matcher
       `actual` can be a literal value, a primitive step, or a flow
       `params` are optional keyword-style args, supporting:

         :times-to-try optional, default 1
         :sleep-time   optional, millis to wait between tries, default 200

       Given (= times-to-try 1), match? will evaluate `actual` just once.

       Given (> times-to-try 1), match? will use `state-flow-probe/probe` to
       retry up to :times-to-try times, waiting :sleep-time between each try,
       and stopping when `actual` produces a value that matches `expected`.

       NOTE: when (> times-to-try 1), `actual` must be a step or a flow.

       Returns a map (in the left value) with information about the success
       or failure of the match, the details of which are used internally by
       state-flow and subject to change.",
       :macro    true}
  match?
  #'state-flow.assertions.matcher-combinators/match?)

(def ^{:arglists '([] [f & args]),
       :doc      "Creates a flow that returns the result of applying f (default identity)\nto state with any additional args."}
  get-state
  #'state-flow.state/gets)

(def ^{:arglists '([f & args]),
       :doc      "Creates a flow that replaces state with the result of applying f to\nstate with any additional args."}
  swap-state
  #'state-flow.state/modify)

;; NOTE: this could be imported directly from cats.core, but we're defining
;; it here to keep the documentation in terms of state-flow rather than cats.
(defmacro for
  "Like clojure.core/for, but returns a flow which wraps a sequence of flows e.g.

     (flow \"even? returns true for even numbers\"
       (flow/for [x (filter even? (range 10))]
         (match? even? x)))

     ;; same as

   (flow \"even? returns true for even numbers\"
     (match? even? 0)
     (match? even? 2)
     (match? even? 4)
     (match? even? 6)
     (match? even? 8)) "
  [seq-exprs flow]
  `(m/for ~seq-exprs ~flow))
