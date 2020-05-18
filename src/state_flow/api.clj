(ns state-flow.api
  (:require [state-flow.vendor.potemkin :refer [import-vars import-fn]]
            [state-flow.core]
            [state-flow.state]
            [state-flow.cljtest]
            [state-flow.assertions.matcher-combinators]))

(import-vars
 state-flow.core/flow
 state-flow.core/run
 state-flow.core/run*
 state-flow.core/log-and-throw-error!
 state-flow.core/ignore-error

 state-flow.state/invoke
 state-flow.state/return
 state-flow.state/fmap

 state-flow.cljtest/defflow

 state-flow.assertions.matcher-combinators/match?)

(import-fn state-flow.state/gets   get-state)
(import-fn state-flow.state/modify swap-state)
