(ns state-flow.state
  (:refer-clojure :exclude [eval get])
  (:require [cats.context :as ctx :refer [*context*]]
            [cats.core :as m]
            [cats.data :as d]
            [cats.monad.exception :as e]
            [cats.monad.state :as state]
            [cats.protocols :as p]
            [cats.util :as util]))

(declare error-context)

(def error-context
  "Same as state monad context, but short circuits if error happens, place error in return value"
  (reify
    p/Context

    p/Extract
    (-extract [mv] (p/-extract mv))

    p/Functor
    (-fmap [_ f fv]
      (state/state (fn [s]
                     (let [mp ((e/wrap (p/-extract fv)) s)]
                       (cond
                         (e/failure? mp)
                         (d/pair mp s)

                         (e/failure? (first @mp))
                         @mp

                         :default
                         (let [[v ns] @mp]
                           [(f v) ns]))))
                   error-context))

    p/Monad
    (-mreturn [_ v]
      (state/state (partial vector v) error-context))

    (-mbind [_ self f]
      (state/state (fn [s]
                     (let [mp ((e/wrap (p/-extract self)) s)]
                       (if (e/failure? mp)
                         [mp s]
                         (if (e/failure? (first @mp))
                           @mp
                           (let [new-pair ((e/wrap (p/-extract (f (first @mp)))) (second @mp))]
                             (if (e/success? new-pair)
                               @new-pair
                               [new-pair (second @mp)]))))))
                   error-context))

    state/MonadState
    (-get-state [_]
      (state/state #(vector %1 %1) error-context))

    (-put-state [_ newstate]
      (state/state #(vector % newstate) error-context))

    (-swap-state [_ f]
      (state/state #(vector %1 (f %1)) error-context))

    p/Printable
    (-repr [_]
      "#<State-E>")))

(util/make-printable (type error-context))

(defn get
  "Returns the equivalent of (fn [state] [state, state])"
  []
  (state/get error-context))

(defn gets
  [f & args]
  "Returns the equivalent of (fn [state] [state, (apply f state args)])"
  (state/gets #(apply f % args) error-context))

(defn put
  "Returns the equivalent of (fn [state] [state, new-state])"
  [new-state]
  (state/put new-state error-context))

(defn modify
  "Returns the equivalent of (fn [state] [state, (apply swap! state f args)])"
  [f & args]
  (state/swap #(apply f % args) error-context))

(defn return
  "Returns the equivalent of (fn [state] [v, state])"
  [v]
  (m/return error-context v))

(defn ^:deprecated swap
  "DEPRECATED: use modify"
  [f]
  (modify f))

(defn wrap-fn
  "Wraps a (possibly side-effecting) function to a state monad"
  [my-fn]
  (state/state (fn [s]
                 [(my-fn) s])
               error-context))

(def state? state/state?)
(def run state/run)
(def eval state/eval)
(def exec state/exec)

(defn ensure-step
  "Internal use only.

  Given a state-flow step, returns value as/is, else wraps value in a state-flow step."
  [value]
  (if (state? value)
    value
    (return value)))
