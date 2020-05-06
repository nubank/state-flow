(ns state-flow.state
  "Internal use. Use functions from state-flow.core instead."
  (:refer-clojure :exclude [eval get])
  (:require [cats.core :as m]
            [cats.monad.exception :as e]
            [cats.monad.state :as state]
            [cats.protocols :as p]
            [cats.util :as util]))

(declare error-context)

(defn- result-or-err [f & args]
  (let [result ((e/wrap (partial apply f)) args)]
    (if (e/failure? result)
      result
      @result)))

(defn error-state [mfn]
  (state/state
   (fn [s]
     (let [new-pair ((e/wrap mfn) s)]
       (if (e/failure? new-pair)
         [new-pair s]
         @new-pair)))
   error-context))

(def error-context
  "Same as state monad context, but short circuits if error happens, place error in return value"
  (reify
    p/Context

    p/Functor
    (-fmap [_ f fv]
      (error-state
       (fn [s]
         (let [[v s'] ((p/-extract fv) s)]
           (if (e/failure? v)
             [v s']
             [(result-or-err f v) s'])))))

    p/Monad
    (-mreturn [_ v]
      (error-state #(vector v %)))

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

(defn ^:deprecated get
  "DEPRECATED. Use state-flow.core/get-state instead"
  []
  (state/get error-context))

(defn ^:deprecated gets
  "DEPRECATED. Use state-flow.core/get-state instead"
  [f & args]
  (state/gets #(apply f % args) error-context))

(defn ^:deprecated put
  "DEPRECATED. Use state-flow.core/reset-state instead"
  [new-state]
  (state/put new-state error-context))

(defn ^:deprecated modify
  "DEPRECATED. Use state-flow.core/swap-state instead"
  [f & args]
  (state/swap #(apply f % args) error-context))

(defn ^:deprecated return
  "DEPRECATED. Use state-flow.core/return instead"
  [v]
  (m/return error-context v))

(defn ^:deprecated swap
  "DEPRECATED: use modify"
  [f]
  (modify f))

(defn wrap-fn
  "Wraps a (possibly side-effecting) function to a state monad"
  [my-fn]
  (error-state (fn [s] [(my-fn) s])))

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
