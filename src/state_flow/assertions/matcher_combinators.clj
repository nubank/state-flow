(ns state-flow.assertions.matcher-combinators
  (:require [cats.core :as m]
            [matcher-combinators.standalone :as matcher-combinators]
            [matcher-combinators.test] ;; to register clojure.test assert-expr for `match?`
            [state-flow.core :as state-flow]
            [state-flow.probe :as probe]))

(defn ^:private ensure-flow
  "Given a state-flow step, returns value as/is, else wraps value in a state-flow step."
  [value]
  (if (state-flow/flow? value)
    value
    (state-flow/return value)))

(defn ^:private match-probe
  "Returns the result of calling probe/probe with a function that
  uses matcher-combinators to match the actual value.

  args:
  - step is a monad which should produce the actual value
  - expected is any valid first argument to `matcher-combinators/match?`
  - params are passed directly to probe "
  [step expected params]
  (probe/probe step
               (fn [actual] (matcher-combinators/match? expected actual))
               params))

(defmacro match?
  "Builds a state-flow step which uses matcher-combinators to make an
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
  state-flow and subject to change."
  [expected actual & [{:keys [times-to-try
                              sleep-time]
                       :as   params}]]

   ;; description is here to support the
   ;; deprecated cljtest/match? fn.  Undecided
   ;; whether we want to make it part of the API.
   ;; caller-meta is definitely not part of the API.
  (let [caller-meta      (meta &form)
        params*          (merge {:description  "match?"
                                 :caller-meta  caller-meta
                                 :times-to-try 1
                                 :sleep-time   probe/default-sleep-time}
                                params)]
    (when (and ((fnil > 1) times-to-try 1)
               (not (state-flow/flow? (eval actual))))
      (throw (ex-info "actual must be a step or a flow when :times-to-try > 1"
                      {:times-to-try times-to-try
                       :actual (eval actual)})))
    (state-flow/flow*
     {:description (:description params*)
      :caller-meta (:caller-meta params*)}
     ;; Nesting m/do-let inside a call the function state-flow/flow* is
     ;; a bit ugly, but it supports getting the correct line number
     ;; information from state-flow/current-description.
     `(m/do-let
       [flow-desc# (state-flow/current-description)
        probe-res# (#'match-probe (#'ensure-flow ~actual) ~expected ~params*)
        :let [actual# (-> probe-res# last :value)
              report# (assoc (matcher-combinators/match ~expected actual#)
                             :match/expected     ~expected
                             :match/actual       actual#
                             :probe/results      probe-res#
                             :probe/sleep-time   ~(:sleep-time params*)
                             :probe/times-to-try ~(:times-to-try params*))]]
       ;; TODO: (dchelimsky, 2020-02-11) we plan to decouple
       ;; assertions from reporting in a future release. Remove this
       ;; next line when that happens.
       (state-flow/wrap-fn #(~'clojure.test/testing flow-desc# (~'clojure.test/is (~'match? ~expected actual#))))
       (state-flow/return report#)))))

(defn report->actual
  "Returns the actual value from the report returned by `match?`."
  [report]
  (:match/actual report))
