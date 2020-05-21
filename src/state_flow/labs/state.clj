(ns state-flow.labs.state
  "WARNING: This API is experimental and subject to changes."
  (:refer-clojure :exclude [with-redefs])
  (:require [cats.core :as m]
            state-flow.api
            [state-flow.core :as state-flow]
            [state-flow.state :as state]))

(defmacro wrap-with
  "WARNING: `wrap-with` usage is not recommended. Use only if you know what you're
  doing and you are sure you can't achieve the same result without it.

  Wraps the provided state-flow execution. `wrapper-fn` will be passed a
  function that will run the flow when called."
  [wrapper-fn flow]
  `(m/do-let
    [world#  (state/get)
     runner# (state-flow/runner)
     :let [[ret# state#] (~wrapper-fn (fn [] (runner# ~flow world#)))]]
    (state-flow.api/swap-state (constantly state#))
    (state/return ret#)))

(defmacro with-redefs
  "WARNING: `with-redefs` usage is not recommended. Use only if you know what you're
  doing and you are sure you can't achieve the same result without it.

  Wraps the provided state-flow execution with `clojure.core/with-redefs`
  macro, e.g.

    (defn now [] (java.util.Date.))
    (def flow-with-trapped-time
      (labs.state/with-redefs
        [now (constantly #inst \"2018-01-01\")]
        (flow \"a flow in 2018\"
              ...)))"
  [bindings flow]
  `(wrap-with
    (fn [f#] (clojure.core/with-redefs ~bindings (f#)))
    ~flow))
