(ns state-flow.labs.cljtest
  (:require [state-flow.core :as core]
            [state-flow.state :as state]
            [clojure.test :as ctest]
            matcher-combinators.clj-test))

(defmacro testing-with {:style/indent :defn}
  [desc bindings & body]
  "Just like `testing`, but with bindings that are only accessible during test"
  `(core/flow ~desc
     [full-desc# (core/current-description)]
     ~bindings
     (state/wrap-fn #(do ~(with-meta `(ctest/testing ~desc ~@body)
                            (meta &form))))))

(defmacro testing [desc & body]
  "state-flow's equivalent to clojure test's `testing`"
  `(testing-with ~desc [] ~@body))
