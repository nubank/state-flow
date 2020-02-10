(ns state-flow.midje
  (:require [cats.context :as ctx]
            [cats.core :as m]
            [midje.checking.core :refer [extended-=]]
            [midje.sweet :refer :all]
            [state-flow.core :as core]
            [state-flow.state :as state]
            [state-flow.probe :as probe]))

(defmacro add-desc-and-meta
  [[fname & rest] desc meta]
  (with-meta `(~fname {:midje/name ~desc} ~@rest) meta))

(defmacro verify-probe
  "Given a fact description, a state and a right-value,
  returns a State that runs left up to times-to-retry times every sleep-time ms until left-value equals right value."
  [desc state right-value metadata]
  `(ctx/with-context (ctx/infer ~state)
     (m/mlet [result# (probe/probe ~state #(extended-= % ~right-value))]
       (do (add-desc-and-meta (fact (:value (last result#)) => ~right-value) ~desc ~metadata)
           (m/return (:value (last result#)))))))

(defmacro verify
  "If left-value is a state, do fact probing. Otherwise, regular fact checking.
  Push and pop descriptions (same behaviour of flow)"
  [desc left-value right-value]
  (let [the-meta  (meta &form)
        fact-sexp `(fact ~left-value => ~right-value)]
    `(core/flow ~desc
       [full-desc# (core/current-description)]
       (if (state/state? ~left-value)
         (verify-probe full-desc# ~left-value ~right-value ~the-meta)
         (state/wrap-fn #(do (add-desc-and-meta ~fact-sexp full-desc# ~the-meta)
                             ~left-value))))))
