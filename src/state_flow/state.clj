(ns state-flow.state
  "Internal use. Use functions from state-flow.core instead."
  (:refer-clojure :exclude [eval get])
  (:require [cats.monad.state :as state]
            [state-flow.core :as state-flow]))

(def ^:deprecated get
  "DEPRECATED. Use state-flow.core/get-state instead"
  state-flow/get-state)

(def ^:deprecated gets
  "DEPRECATED. Use state-flow.core/get-state instead"
  state-flow/get-state)

(def ^:deprecated put
  "DEPRECATED. Use state-flow.core/reset-state instead"
  state-flow/reset-state)

(def ^:deprecated modify
  "DEPRECATED. Use state-flow.core/swap-state instead"
  state-flow/swap-state)

(def ^:deprecated return
  "DEPRECATED. Use state-flow.core/return instead"
  state-flow/return)

(def ^:deprecated swap
  "DEPRECATED: use state-flow.core/swap-state instead"
  state-flow/swap-state)

(def ^:deprecated wrap-fn
  "DEPRECATED: use state-flow.core/wrap-fn"
  state-flow/wrap-fn)

(def ^:deprecated state?
  "DEPRECATED: use state-flow.core/flow? instead"
  state/state?)

(def ^:deprecated run
  "DEPRECATED. Use state-flow.core/run instead"
  state/run)

(def ^:deprecated eval
  "DEPRECATED. Use (first (state-flow.core/run flow init-state)) or cats.monad.state/eval"
  state/eval)

(def ^:deprecated exec
  "DEPRECATED. Use (second (state-flow.core/run flow init-state)) or cats.monad.state/exec"
  state/exec)
