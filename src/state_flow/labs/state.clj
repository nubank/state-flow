(ns state-flow.labs.state
  (:refer-clojure :exclude [with-redefs])
  (:require [cats.core :as m]
            [state-flow.state :as state]
            [state-flow.core :as state-flow]))

(def ^:dynamic *with-redefs-macro-enabled* nil)

(defmacro with-redefs
  [bindings flow]
  (assert *with-redefs-macro-enabled*
          "`with-redefs` usage is not recommended. If you know what you're doing and really want to continue, set `*with-redefs-macro-enabled*` to true")
  `(m/do-let
    [world#  (state/get)
     runner# (state-flow/runner)
     :let [[ret# state#] (clojure.core/with-redefs ~bindings
                           (runner# ~flow world#))]]
    (state/put state#)
    (state/return ret#)))
