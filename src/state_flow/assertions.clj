(ns state-flow.assertions)

(defn failure?
  "Is the result of a flow an assertion failure?"
  [v]
  ;; currently only matcher-combinator assertions are supported
  (= :mismatch (:match/result v)))
