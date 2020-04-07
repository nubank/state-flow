(ns state-flow.assertions.matcher-combinators
  (:require [cats.core :as m]
            [matcher-combinators.standalone :as matcher-combinators]
            [matcher-combinators.test] ;; to register clojure.test assert-expr for `match?`
            [state-flow.core :as core]
            [state-flow.probe :as probe]
            [state-flow.state :as state]))

(defn ^:private match-probe
  "Internal use only.

  Returns the right value returned by probe/probe.

  args:
  - step is a step (monad) which should produce the actual value
  - matcher is a matcher-combinators matcher object
  - params are passed directly to probe "
  [step matcher params]
  (probe/probe step
               #(matcher-combinators/match? matcher %)
               params))

(defmacro match?
  "Builds a state-flow assertion using matcher-combinators.

  `expected` can be a literal value or a matcher-combinators matcher
  `actual` can be a literal value, a primitive step, or a flow
  `params` are optional keyword-style args, supporting:

    :times-to-try optional, default 1
    :sleep-time   optional, millis to wait between tries, default 200

  Given (= :times-to-try 1), match? will evaluate `actual` just once.

  Given (> :times-to-try 1), match? will use `state-flow-probe/probe` to
  retry up to :times-to-try times, waiting :sleep-time between each try,
  and stopping when `actual` produces a value that matches `expected`.

  Returns a map (in the left value) of:

    :match/expected     - the expected value
    :match/actual       - the actual value (potentially after probing)
    :match/result       - :match or :mismatch
    :probe/times-to-try - number of times to try         ;; when (> times-to-try 1)
    :probe/sleep-time   - time to sleep between each try ;; when (> times-to-try 1)
    :probe/results      - the results of each try        ;; when (> times-to-try 1)"
  [expected actual & [{:keys [times-to-try
                              sleep-time]
                       :as   params}]]
   ;; description is here to support the
   ;; deprecated cljtest/match? fn.  Undecided
   ;; whether we want to make it part of the API.
   ;; caller-meta is definitely not part of the API.
  (let [params* (merge {:description  "match?"
                        :caller-meta  (meta &form)
                        :times-to-try 1
                        :sleep-time   probe/default-sleep-time}
                       params)]
    (core/flow*
     {:description (:description params*)
      :caller-meta (:caller-meta params*)}
      ;; Nesting m/do-let inside a call the function core/flow* is
      ;; a bit ugly, but it supports getting the correct line number
      ;; information from core/current-description.
     `(m/do-let
       [flow-desc# (core/current-description)
        probe-res# (#'match-probe (state/ensure-step ~actual) ~expected ~params*)
        :let [actual# (-> probe-res# last :value)
              report# (cond-> (assoc (matcher-combinators/match ~expected actual#)
                                     :match/expected ~expected
                                     :match/actual   actual#)
                        (> (count probe-res#) 1)
                        (assoc :probe/results      probe-res#
                               :probe/sleep-time   ~(:sleep-time params*)
                               :probe/times-to-try ~(:times-to-try params*)))]]
       ;; TODO: (dchelimsky, 2020-02-11) we plan to decouple
       ;; assertions from reporting in a future release. Remove this
       ;; next line when that happens.
       (state/wrap-fn #(~'clojure.test/testing flow-desc# (~'clojure.test/is (~'match? ~expected actual#))))
       (state/return report#)))))

(defmacro report->actual
  "Returns the actual value from a match report returned by `match?`.

  `report-or-match` can be a report (map) or a match? expression, e.g. either
  of these will produce 3

    (flow \"example\"
      [report (match? 2 3)
       actual (report->actual report)]
      (state/return actual))

    (flow \"example\"
      [actual (report->actual (match? 2 3))]
      (state/return actual)) "
  [report-or-match]
  `(m/do-let
    [report# (state/ensure-step ~report-or-match)]
    (state/return (:match/actual report#))))
