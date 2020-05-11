(ns state-flow.state
  (:refer-clojure :exclude [eval get])
  (:require [cats.core :as m]
            [cats.monad.exception :as e]
            [cats.monad.state :as state]
            [cats.protocols :as p]
            [cats.util :as util]))

(declare short-circuiting-context)

(defn- result-or-err [f & args]
  (let [result ((e/wrap (partial apply f)) args)]
    (if (e/failure? result)
      result
      @result)))

(defn error-catching-state [mfn]
  (state/state
   (fn [s]
     (let [new-pair ((e/wrap mfn) s)]
       (if (e/failure? new-pair)
         [new-pair s]
         @new-pair)))
   short-circuiting-context))

(def short-circuiting-context
  "Same as state monad context, but short circuits if error happens, place error in return value"
  (reify
    p/Context

    p/Functor
    (-fmap [_ f fv]
      (error-catching-state
       (fn [s]
         (let [[v s'] ((p/-extract fv) s)]
           (if (e/failure? v)
             [v s']
             [(result-or-err f v) s'])))))

    p/Monad
    (-mreturn [_ v]
      (error-catching-state #(vector v %)))

    (-mbind [_ self f]
      (error-catching-state
       (fn [s]
         (let [[v s'] ((p/-extract self) s)]
           (if (e/failure? v)
             [v s']
             ((p/-extract (f v)) s'))))))

    state/MonadState
    (-get-state [_]
      (error-catching-state #(vector %1 %1)))

    (-put-state [_ newstate]
      (error-catching-state #(vector % newstate)))

    (-swap-state [_ f]
      (error-catching-state #(vector %1 (f %1))))

    p/Printable
    (-repr [_]
      "#<State-E>")))

(util/make-printable (type short-circuiting-context))

(defn get
  "Creates a flow that returns the value of state. "
  []
  (state/get short-circuiting-context))

(defn gets
  "Creates a flow that returns the result of applying f to state
  with any additional args."
  [f & args]
  (state/gets #(apply f % args) short-circuiting-context))

(defn put
  "Creates a flow that replaces state with new-state. "
  [new-state]
  (state/put new-state short-circuiting-context))

(defn modify
  "Creates a flow that replaces state with the result of applying f to
  state with any additional args."
  [f & args]
  (state/swap #(apply f % args) short-circuiting-context))

(defn ^:deprecated return
  "DEPRECATED: use state-flow.core/return instead."
  [v]
  (m/return short-circuiting-context v))

(defn ^:deprecated swap
  "DEPRECATED: use state-flow.state/modify instead."
  [f]
  (modify f))

(defn ^:deprecated wrap-fn
  "DEPRECATED: Use state-flow.core/invoke instead."
  [my-fn]
  (error-catching-state (fn [s] [(my-fn) s])))

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
