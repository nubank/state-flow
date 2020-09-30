(ns state-flow.state
  (:refer-clojure :exclude [eval get])
  (:require [cats.core :as m]
            [cats.monad.exception :as e]
            [cats.monad.state :as state]
            [cats.protocols :as p]
            [cats.util :as util]
            [state-flow.assertions :as assertions]))

(declare short-circuiting-context)

(def ^:dynamic *fail-fast?* false)

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
           (cond (e/failure? v)
                 [v s']

                 (and *fail-fast?* (assertions/failure? v))
                 [v s']

                 :else
                 [(result-or-err f v) s'])))))

    p/Monad
    (-mreturn [_ v]
      (error-catching-state #(vector v %)))

    (-mbind [_ self f]
      (error-catching-state
       (fn [s]
         (let [[v s'] ((p/-extract self) s)]
           (cond (e/failure? v)
                 [v s']

                 (and *fail-fast?* (assertions/failure? v))
                 [v s']

                 :else
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
  "Creates a flow that returns the result of applying f (default identity)
  to state with any additional args."
  ([]
   (gets identity))
  ([f & args]
   (state/gets #(apply f % args) short-circuiting-context)))

(defn put
  "Creates a flow that replaces state with new-state. "
  [new-state]
  (state/put new-state short-circuiting-context))

(defn modify
  "Creates a flow that replaces state with the result of applying f to
  state with any additional args."
  [f & args]
  (state/swap #(apply f % args) short-circuiting-context))

(defn return
  "Creates a flow that returns v. Use this as the last
  step in a flow that you want to reuse in other flows, in
  order to clarify the return value, e.g.

    (def increment-count
      (flow \"increments :count and returns it\"
        (state/modify update :count inc)
        [new-count (state/gets :count)]
        (state-flow/return new-count)))"
  [v]
  (m/return short-circuiting-context v))

(defn invoke
  "Creates a flow that invokes a function of no arguments and returns the
  result. Used to invoke side effects e.g.

     (state-flow.core/invoke #(Thread/sleep 1000))"
  [my-fn]
  (error-catching-state (fn [s] [(my-fn) s])))

(def
  ^{:doc "Creates a flow that returns the application of f to the return of flow"
    :arglists '([f flow])}
  fmap
  m/fmap)

(defn ^:deprecated swap
  "DEPRECATED: use state-flow.state/modify instead."
  [f]
  (modify f))

(def ^{:deprecated true
       :doc "DEPRECATED: Use state-flow.state/invoke instead."}
  wrap-fn
  invoke)

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
