(ns state-flow.labs.cljtest
  (:require [state-flow.core :as core]
            [state-flow.state :as state]
            [clojure.test :as ctest]))

(defmacro testing [desc & body]
  "state-flow's equivalent to clojure test's `testing`"
  `(core/flow ~desc
              [full-desc# (core/get-description)]
              (state/wrap-fn #(do ~(with-meta `(ctest/testing ~desc ~@body)
                                     (meta &form))))))
