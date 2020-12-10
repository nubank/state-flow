(ns state-flow.assertions.report
  (:require
    [state-flow.core :as core]
    [state-flow.state :as state]
    [cats.core :as m]))

(defn push
  [assertion-report]
  (m/do-let
   [description-stack (state/gets core/description-stack)
    :let [report (assoc assertion-report :flow/description-stack description-stack)]]
   (core/modify-meta update-in [:test-report :assertions] (fnil conj []) report)))
