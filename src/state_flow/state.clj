(ns state-flow.state
  (:refer-clojure :exclude [eval get when])
  (:require [cats.monad.state :as state]
            [cats.protocols :as p]
            [cats.util :as util]
            [state-flow.protocols :as sp]))

(defn throw-illegal-argument
  {:no-doc true :internal true}
  [^String text]
  (throw (IllegalArgumentException. text)))

;; CONTEXT STUFF HERE

(defprotocol Contextual
  "Abstraction that establishes a concrete type as a member of a context.

  A great example is the Maybe monad type Just. It implements
  this abstraction to establish that Just is part of
  the Maybe monad."
  (-get-context [_] "Get the context associated with the type."))

(extend-protocol sp/Contextual
  java.lang.Object
  (-get-context [_] nil))

(extend-protocol sp/Contextual
  nil
  (-get-context [_] nil))

(defn infer
  "Given an optional value infer its context. If context is already set, it
  is returned as is without any inference operation."
  {:no-doc true}
  [v]
  (if-let [context (sp/-get-context v)]
    context
    (throw-illegal-argument
     (str "No context is set and it can not be automatically "
          "resolved from provided value"))))

;; END CONTEXT STUFF

;; FUNCTOR STUFF
(defn fmap
  ^{:doc "Creates a flow that returns the application of f to the return of flow"
    :arglists '([f flow])}
  [f fv]
  (let [ctx (infer fv)]
    (sp/-fmap ctx f fv)))

;; MONAD STUFF
(defn bind
  "Given a monadic value `mv` and a function `f`,
  apply `f` to the unwrapped value of `mv`.

      (bind (either/right 1) (fn [v]
                               (return (inc v))))
      ;; => #<Right [2]>

  For convenience, you may prefer to use the `mlet` macro,
  which provides a beautiful, `let`-like syntax for
  composing operations with the `bind` function."
  [mv f]
  (let [ctx (infer mv)]
    (sp/-mbind ctx mv f)))

(defn mreturn
  "This is a monad version of `pure` and works
  identically to it."
  [ctx v]
  (sp/-mreturn ctx v))

(defn join
  "Remove one level of monadic structure.
  This is the same as `(bind mv identity)`."
  [mv]
  (bind mv identity))

(defmacro mlet
  "Monad composition macro that works like Clojure's
     `let`. This facilitates much easier composition of
     monadic computations.

     Let's see an example to understand how it works.
     This code uses bind to compose a few operations:

         (bind (just 1)
               (fn [a]
                 (bind (just (inc a))
                         (fn [b]
                           (return (* b 2))))))
         ;=> #<Just [4]>

     Now see how this code can be made clearer
     by using the mlet macro:

         (mlet [a (just 1)
                b (just (inc a))]
           (return (* b 2)))
         ;=> #<Just [4]>
     "
  [bindings & body]
  (when-not (and (vector? bindings)
                 (not-empty bindings)
                 (even? (count bindings)))
    (throw (IllegalArgumentException. "bindings has to be a vector with even number of elements.")))
  (->> (reverse (partition 2 bindings))
       (reduce (fn [acc [l r]] `(bind ~r (fn [~l] ~acc)))
               `(do ~@body))))

(defmacro do-let
  "Haskell-inspired monadic do notation
   it allows one to drop the _ when  we don't need the extracted value

  Basically,
  (do-let
    a
    b
    [c d
     e f]
    x
    y)

  Translates into:

  (mlet
    [_ a
     _ b
     c d
     e f
     _ x]
    y)
  "
  [& forms]
  (assert (not (empty? forms)) "do-let must have at least one argument")
  (assert (not (vector? (last forms))) "Last argument of do-let must not be a vector")
  (if (= 1 (count forms))
    `(do (assert (not (satisfies? sp/Monad ~(first forms))) "Single argument of do-let must implement Monad protocol")
         ~(first forms))
    `(mlet ~(vec (reduce (fn [acc form]
                           (cond (vector? form) (into acc form)
                                 :else          (into acc ['_ form])))
                         []
                         (butlast forms)))
       ~(last forms))))

;; APPLICATIVE STUFF
(defn pure
  "Given any value `v`, return it wrapped in
  the default/effect-free context.

  This is a multi-arity function that with arity `pure/1`
  uses the dynamic scope to resolve the current
  context. With `pure/2`, you can force a specific context
  value.

  Example:

      (with-context either/context
        (pure 1))
      ;; => #<Right [1]>

      (pure either/context 1)
      ;; => #<Right [1]>
  "
  [ctx v]
  (sp/-pure ctx v))

(defn fapply
  "Given a function wrapped in a monadic context `af`,
  and a value wrapped in a monadic context `av`,
  apply the unwrapped function to the unwrapped value
  and return the result, wrapped in the same context as `av`.

  This function is variadic, so it can be used like
  a Haskell-style left-associative fapply."
  [af & avs]
  {:pre [(seq avs)]}
  (let [ctx (infer af)]
    (reduce (partial sp/-fapply ctx) af avs)))

(defn sequence
  "Given a collection of monadic values, collect
  their values in a seq returned in the monadic context.

      (require '[cats.context :as ctx]
               '[cats.monad.maybe :as maybe]
               '[cats.core :as m])

      (sequence [(maybe/just 2) (maybe/just 3)])
      ;; => #<Just [[2, 3]]>

      (sequence [(maybe/nothing) (maybe/just 3)])
      ;; => #<Nothing>

      (ctx/with-context maybe/context
        (sequence []))
      ;; => #<Just [()]>
  "
  [context mvs]
  (if (empty? mvs)
    (mreturn context ())
    (reduce (fn [mvs mv]
              (mlet [v mv
                     vs mvs]
                    (mreturn context (cons v vs))))
            (mreturn context ())
            (reverse mvs))))

(defmacro for
  "Syntactic wrapper for (sequence (for [,,,] mv)).

      (require '[cats.core :as m]
               '[cats.monad.maybe :as maybe])

      (m/for [x [2 3]] (maybe/just x))
      ;; => #<Just [[2, 3]]>

  See cats.core/sequence
  See clojure.core/for"
  [seq-exprs body-expr]
  `(sequence (clojure.core/for ~seq-exprs ~body-expr)))

(declare short-circuiting-context)

(defn- result-or-err [f & args]
  (try
    (apply f args)
    (catch Throwable e e)))

(defn error-catching-state [mfn]
  (state/state
   (fn [s]
     (try
       (let [[v s'] (mfn s)]
         [v s'])
       (catch Throwable e
         [e s])))
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
           (if (instance? Throwable v)
             [v s']
             [(result-or-err f v) s'])))))

    p/Monad
    (-mreturn [_ v]
      (error-catching-state #(vector v %)))

    (-mbind [_ self f]
      (error-catching-state
       (fn [s]
         (let [[v s'] ((p/-extract self) s)]
           (if (instance? Throwable v)
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
  (mreturn short-circuiting-context v))

(defn invoke
  "Creates a flow that invokes a function of no arguments and returns the
  result. Used to invoke side effects e.g.

     (state-flow.core/invoke #(Thread/sleep 1000))"
  [my-fn]
  (error-catching-state (fn [s] [(my-fn) s])))

(defn when
  "Given an expression `e` and a flow, if the expression is logical true, return the flow. Otherwise, return nil in a monadic context."
  [e flow]
  (if e
    flow
    (return nil)))

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
