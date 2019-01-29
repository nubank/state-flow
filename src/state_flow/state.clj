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
                     (let [[v ns]  ((p/-extract fv) s)]
                       (d/pair (f v) ns)))
                   error-context))

    p/Monad
    (-mreturn [_ v]
      (state/state (partial d/pair v) error-context))


    (-mbind [_ self f]
      (state/state (fn [s]
                     (let [mp ((e/wrap (p/-extract self)) s)]
                       (if (e/failure? mp)
                         (d/pair mp s)
                         (if (e/failure? (.-fst @mp))
                           @mp
                           (let [new-pair ((e/wrap (p/-extract (f (.-fst @mp)))) (.-snd @mp))]
                             (if (e/success? new-pair)
                               @new-pair
                               (d/pair new-pair (.-snd @mp))))))))
                   error-context))

    state/MonadState
    (-get-state [_]
      (state/state #(d/pair %1 %1) error-context))

    (-put-state [_ newstate]
      (state/state #(d/pair % newstate) error-context))

    (-swap-state [_ f]
      (state/state #(d/pair %1 (f %1)) error-context))

    p/Printable
    (-repr [_]
      "#<State-E>")))

(util/make-printable (type error-context))

(defn get
  []
  (state/get error-context))

(defn put
  [s]
  (state/put s error-context))

(defn swap
  [f]
  (state/swap f error-context))

(defn wrap-fn
  "Wraps a (possibly side-effecting) function to a state monad"
  [my-fn]
  (state/state (fn [s]
                 (d/pair (my-fn) s))
               error-context))

(def state? state/state?)
(def run state/run)
(def eval state/eval)
(def exec state/exec)
(def gets state/gets)
