(ns state-flow.labs.cljtest
  (:require [clojure.test :as ctest]
            [state-flow.core :as core]
            [state-flow.state :as state]))

(defmacro testing
  "state-flow's equivalent to clojure test's `testing`"
  [desc & body]
  `(core/flow ~desc
     [full-desc# (core/current-description)]
     (state/wrap-fn #(do ~(with-meta `(ctest/testing ~desc ~@body)
                            (meta &form))))))
