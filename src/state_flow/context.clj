(ns state-flow.context
  "Internal use. "
  (:require [cats.monad.exception :as e]
            [cats.monad.state :as state]
            [cats.protocols :as p]
            [cats.util :as util]))

(declare short-circuiting-context)

(defn- result-or-err [f & args]
  (let [result ((e/wrap (partial apply f)) args)]
    (if (e/failure? result)
      result
      @result)))

(defn error-catching-state
  "Creates a new state monad with mfn wrapped in a
  try/catch which returns a Failure in the event of
  an error.

  Internal use. Don't call directly."
  [mfn]
  (state/state
   (fn [s]
     ;; wrap wraps mfn in a try in order to capture an error
     (let [result ((e/wrap mfn) s)]
       (if (e/failure? result)
         [result s]
         @result)))
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
