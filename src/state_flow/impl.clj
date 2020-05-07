(ns state-flow.impl
  "Internal use only. Use the functions in state-flow.core.

  This ns contains implementations of functions that moved and were renamed
  in order to avoid duplication of implementations and tests."
  (:require [cats.core :as m]
            [cats.monad.state]
            [state-flow.context :as context]))

(defn get-state
  "See doc string in state-flow.core"
  ([]
   (get-state identity))
  ([f & args]
   (cats.monad.state/gets #(apply f % args) context/short-circuiting-context)))

(defn reset-state
  "See doc string in state-flow.core"
  [new-state]
  (cats.monad.state/put new-state context/short-circuiting-context))

(defn swap-state
  "See doc string in state-flow.core"
  [f & args]
  (cats.monad.state/swap #(apply f % args) context/short-circuiting-context))

(defn return
  "See doc string in state-flow.core"
  [v]
  (m/return context/short-circuiting-context v))

(defn wrap-fn
  "Wraps a (possibly side-effecting) function to a state monad"
  [my-fn]
  (context/error-catching-state (fn [s] [(my-fn) s])))
