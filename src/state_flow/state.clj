(ns state-flow.state
  "Internal use. Use functions from state-flow.core instead."
  (:refer-clojure :exclude [eval get])
  (:require [cats.monad.state :as state]
            [state-flow.impl :as impl]))

(def ^:deprecated get
  "DEPRECATED. Use state-flow.core/get-state instead"
  impl/get-state)

(def ^:deprecated gets
  "DEPRECATED. Use state-flow.core/get-state instead"
  impl/get-state)

(def ^:deprecated put
  "DEPRECATED. Use state-flow.core/reset-state instead"
  impl/reset-state)

(def ^:deprecated modify
  "DEPRECATED. Use state-flow.core/swap-state instead"
  impl/swap-state)

(def ^:deprecated return
  "DEPRECATED. Use state-flow.core/return instead"
  impl/return)

(def ^:deprecated swap
  "DEPRECATED: use state-flow.core/swap-state instead"
  impl/swap-state)

(def ^:deprecated wrap-fn
  "DEPRECATED: use state-flow.core/wrap-fn"
  impl/wrap-fn)

(def state? state/state?)
(def run state/run)
(def eval state/eval)
(def exec state/exec)
