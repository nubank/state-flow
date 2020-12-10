(ns state-flow.assertions.report
  (:require
    [state-flow.core :as core]))

(defn push
  [assertion-report]
  (core/modify-meta update :test-report (fnil conj []) assertion-report))
