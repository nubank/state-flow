(ns state-flow.api
  (:refer-clojure :exclude [for when])
  (:require [cats.core :as m]
            [state-flow.assertions.matcher-combinators]
            [state-flow.cljtest]
            [state-flow.core]
            [state-flow.state]
            [state-flow.vendor.potemkin :refer [import-fn import-vars]]))

;; TODO: (dchelimsky,2020-05-18) Intellij / Cursive doesn't recognize the
;; vars imported below unless we declare them. If that is ever fixed, we
;; should remove this.
(declare flow
         run
         run*
         log-and-throw-error!
         ignore-error
         invoke
         return
         fmap
         defflow
         match?
         get-state
         swap-state
         when
         description-stack)

(import-vars
 state-flow.core/flow
 state-flow.core/run
 state-flow.core/run*
 state-flow.core/log-and-throw-error!
 state-flow.core/ignore-error
 state-flow.core/description-stack

 state-flow.state/invoke
 state-flow.state/return
 state-flow.state/fmap
 state-flow.state/when

 state-flow.cljtest/defflow

 state-flow.assertions.matcher-combinators/match?)

(import-fn state-flow.state/gets   get-state)
(import-fn state-flow.state/modify swap-state)

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
