(ns state-flow.assertions.matcher-combinators
  (:require [cats.core :as m]
            [matcher-combinators.core :as matcher-combinators]
            [state-flow.core :as core]
            [state-flow.probe :as probe]
            [state-flow.state :as state]))

(defn ^:private ensure-wrapped
  "Internal use only.

  Given a state-flow step, returns value as/is, else wraps value in a state-flow step."
  [value]
  (if (state/state? value)
    value
    (state/return value)))

(defn ^:private match-probe
  "Internal use only.

  Returns the right value returned by probe/probe."
  [state matcher params]
  (m/fmap second
          (probe/probe state
                       #(matcher-combinators/match? (matcher-combinators/match matcher %))
                       params)))

(defmacro match?
  "Builds a state-flow assertion using matcher-combinators.

  - expected can be a literal value or a matcher-combinators matcher
  - actual can be a literal value, a primitive, or a flow
  - params is an optional map supporting:
    - :times-to-try optional, default 1
    - :sleep-time   optional, default 200

  Given (> :times-to-try 1), match? will use `state-flow-probe/probe` to
  retry :times-to-try times with :sleep-time

  See `state-flow.probe/probe` for more info"
  ([expected actual]
   `(match? ~expected ~actual {:times-to-try 1}))
  ([expected actual {:keys [times-to-try]
                     :or {times-to-try 1}
                     :as params}]
   (core/flow*
    {:description "match?"
     :caller-meta (meta &form)}
    ;; Nesting m/do-let inside a call the function core/flow* is
    ;; a bit ugly, but it supports getting the correct line number
    ;; information from core/current-description.
    `(m/do-let
      [flow-desc# (core/current-description)
       actual#    (if (> (:times-to-try ~params) 1)
                    (#'match-probe (#'ensure-wrapped ~actual) ~expected ~params)
                    (#'ensure-wrapped ~actual))]
      (state/wrap-fn #(t/testing flow-desc# (t/is (~'match? ~expected actual#))))
      (state/return actual#)))))
