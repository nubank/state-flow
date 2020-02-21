(ns state-flow.state
  (:refer-clojure :exclude [eval get])
  (:require [cats.context :as ctx :refer [*context*]]
            [cats.core :as m]
            [cats.monad.exception :as e]
            [cats.monad.state :as state]
            [cats.protocols :as p]
            [cats.util :as util]))

(declare error-context)

(defn- result-or-err [f args]
  (let [result ((e/wrap (partial apply f)) args)]
    (if (e/failure? result)
      result
      @result)))

(defn- result-or-err-pair [f s]
  (let [new-pair ((e/wrap f) s)]
    (if (e/failure? new-pair)
      [new-pair s]
      @new-pair)))

(defrecord ErrorState [mfn]
  p/Contextual
  (-get-context [_] error-context)

  p/Extract
  (-extract [_] (partial result-or-err-pair mfn)))

(alter-meta! #'->ErrorState assoc :private true)
(alter-meta! #'map->ErrorState assoc :private true)

(defn error-state [f] (ErrorState. f))

(def error-context
  "Same as state monad context, but short circuits if error happens, place error in return value"
  (reify
    p/Context

    p/Functor
    (-fmap [self f fv]
      (error-state
       (fn [s]
         (let [[v s'] ((p/-extract fv) s)]
           (if (e/failure? v)
             [v s']
             [(result-or-err f v) s'])))))

    p/Monad
    (-mreturn [_ v]
      (error-state (partial vector v)))

    (-mbind [_ self f]
      (error-state
       (fn [s]
         (let [[v s'] ((p/-extract self) s)]
           (if (e/failure? v)
             [v s']
             ((p/-extract (f v)) s'))))))

    state/MonadState
    (-get-state [_]
      (error-state #(vector %1 %1)))

    (-put-state [_ newstate]
      (error-state #(vector % newstate)))

    (-swap-state [_ f]
      (error-state #(vector %1 (f %1))))

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

(defn state? [v]
  (instance? ErrorState v))
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
