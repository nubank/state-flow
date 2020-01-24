(ns state-flow.rewrite.match-to-expect
  (:require [clojure.java.io :as io]
            [rewrite-clj.zip :as z]))

(def cljtest-test-src
  (delay (z/of-file "test/state_flow/cljtest_test.clj")))

(def data "(state-flow/run (cljtest/match? \"DESC\" 'actual 'expected) {:initial :state})")

(def ^:dynamic *match?-symbol* 'match?)
(def ^:dynamic *expect-symbol* 'expect)

(defn match?->expect
  "Given a match? expression e.g.

    (match? <description> <actual> <expected>)
    ;; or
    (match? <description> <actual> <expected> <params>)

  Returns an expect expression e.g.

    (expect <expected> <actual>)
    ;; or
    (expect <expected> <actual> <params>)

  If there is an exception, printlns the expression so you can
  find and handle it manually."
  [expect-sym match-exp]
  (try
    (let [match-sym (-> match-exp z/down)
          actual (-> match-sym
                     z/right
                     z/right)
          expected (-> actual z/right)]
      (-> match-sym
          (z/replace expect-sym)
          z/right
          z/remove
          z/right
          (z/replace (z/node expected))
          z/right
          (z/replace (z/node actual))))
    (catch Exception e
      (println "Error processing " (z/string match-exp))
      match-exp)))

(defn match?-expr? [match?-sym node]
  (when (= :list (z/tag node))
    (when-let [op (z/down node)]
      (= match?-sym (z/value op)))))

(defn rewrite-match?->expect [match?-sym expect-sym path]
  (let [data (z/of-file path)]
    (spit (io/file path)
          (z/root-string
           (loop [data data]
             (let [updated (try
                             (z/prewalk data
                                        (partial match?-expr? match?-sym)
                                        (partial match?->expect expect-sym))
                             (catch Exception _ data))]
               (if (z/rightmost? updated)
                 updated
                 (recur (z/right updated)))))))))

(comment
  (rewrite-match?->expect 'cljtest/match?
                          'cljtest/expect
                          "test/state_flow/cljtest_test.clj")

  )
