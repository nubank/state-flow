(ns state-flow.core
  (:refer-clojure :exclude [run!])
  (:require [cats.context :as ctx]
            [cats.core :as m]
            [cats.data :as d]
            [cats.monad.exception :as e]
            [matcher-combinators.test]
            [midje.checking.core :refer [extended-=]]
            [midje.sweet :refer :all]
            [nu.monads.state :as nu.state]
            [cats.monad.state :as state]
            [clojure.test :refer :all]
            [nu.monads.state :as state]
            [taoensso.timbre :as log]))

(def sleep-time 10)
(def times-to-try 100)

(defn wrap-fn
  "Wraps a (possibly side-effecting) function to a state monad"
  [my-fn]
  (state/state (fn [s]
                 (d/pair (my-fn) s))
               nu.state/error-context))

(defn update-description
  [old new]
  (if (or (nil? old) (empty? old))
    [new]
    (conj old new)))

(defn push-meta
  [description]
  (nu.state/swap
   (fn [s]
     (update-in s [:meta :description] #(update-description % description)))))

(def pop-meta
  (nu.state/swap
   (fn [s]
     (update-in s [:meta :description] #(pop %)))))

(defn description->string
  [description]
  (clojure.string/join " -> " description))

(defn get-description
  []
  (m/mlet [desc-list (state/gets #(-> % :meta :description))]
    (m/return (description->string desc-list))))

(defmacro flow
  [description & flows]
  `(m/mlet [_#   (push-meta ~description)
            ret# (nu.state/do-let ~@flows)
            _#   pop-meta]
    (m/return ret#)))

(defn retry
  "Tries at most n times, returns a vector with true and first element that succeeded
  or false and result of the first try"
  [times pred? lazy-seq]
  (let [remaining (drop-while (complement pred?) (take times lazy-seq))]
    (if (empty? remaining)
      [false (first lazy-seq)]
      [true  (first remaining)])))

(defmacro add-desc-and-meta
  [[fname & rest] desc meta]
  (with-meta `(~fname {:midje/name ~desc} ~@rest) meta))

(defmacro probe-state
  "Given a fact description, a state and a right-value,
  returns a State that runs left up to times-to-retry times every sleep-time ms until left-value equals right value."
  [desc state right-value metadata]
  `(ctx/with-context (ctx/infer ~state)
     (m/mlet [world# (state/get)
              :let [runs#       (repeatedly #(do (Thread/sleep sleep-time) (state/eval ~state world#)))
                    [_# result#] (retry ~times-to-try #(extended-= % ~right-value) runs#)]]
       (do (add-desc-and-meta (fact result# => ~right-value) ~desc ~metadata)
           (m/return result#)))))

(defmacro verify
  "If left-value is a state, do fact probing. Otherwise, regular fact checking.
  Push and pop descriptions (same behaviour of flow)"
  [desc left-value right-value]
  (let [the-meta  (meta &form)
        fact-sexp `(fact ~left-value => ~right-value)]
    `(flow ~desc
       [full-desc# (get-description)]
       (if (state/state? ~left-value)
         (probe-state full-desc# ~left-value ~right-value ~the-meta)
         (wrap-fn #(do (add-desc-and-meta ~fact-sexp full-desc# ~the-meta)
                             ~left-value))))))

(defmacro test
  "Test using clojure.test as a backend. check-expr is an expression that evaluates to a truthy or falsey"
  [desc check-expr]
  (let [the-meta  (meta &form)
        test-name (symbol (clojure.string/replace desc " " "-"))
        test-expr `(deftest ~test-name
                     (is ~check-expr))]
    `(flow ~desc
       [full-desc# (get-description)]
         (state/wrap-fn #(do ~test-expr
                             ~check-expr)))))

(defn run
  [flow initial-state]
  (assert (state/state? flow) "First argument must be a State Monad")
  (assert (map? initial-state) "Initial state must be a map")
  (state/run flow initial-state))

(defn run!
  "Like run, but prints a log and throws error when flow fails with an exception"
  [flow initial-state]
  (let [result (run flow initial-state)]
    (when (e/failure? (first result))
      (let [description (->> result second :meta :description
                             description->string)
            message (str "Flow " "\"" description "\"" " failed with exception")]
        (log/info (m/extract (first result)) message)
        (throw (ex-info message {}))))
    result))

(defn as-step-fn
  "Transform a state monad into a postman step function"
  [m]
  (fn [world] (state/exec m world)))
