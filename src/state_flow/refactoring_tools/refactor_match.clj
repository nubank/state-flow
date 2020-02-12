(ns state-flow.refactoring-tools.refactor-match
  "This ns contains tools to refactor from match? in the cljtest
  ns to the new version in the assertions.matcher-combinators ns."
  (:require [clojure.java.io :as io]
            [rewrite-clj.zip :as z]))

(defn refactor-match-expr
  "
  If there is an exception, printlns the expression so you can
  find and handle it manually."
  [{:keys [wrap-in-testing
           sym-after]}
   zloc]
  (try
    (let [match-sym  (-> zloc z/down)
          desc       (-> match-sym z/right)
          actual     (-> desc z/right)
          expected   (-> actual z/right)
          refactored (z/edit-> zloc
                               z/down
                               (z/replace sym-after)
                               z/right
                               z/remove
                               z/right
                               (z/replace (z/node expected))
                               z/right
                               (z/replace (z/node actual)))]
      (if wrap-in-testing
        (z/edit-> zloc
                  (z/replace
                   (-> (z/of-string "(testing)")
                       (z/append-child (z/node desc))
                       (z/append-child (z/node refactored))
                       (z/node))))
        refactored))
    (catch Exception e
      (println "Error processing " (z/string zloc))
      zloc)))

(defn match-expr? [match-sym node]
  (when (= :list (z/tag node))
    (when-let [op (z/down node)]
      (= match-sym (z/value op)))))

(defn ^:private refactor-all* [{:keys [sym-before] :as opts} data]
  (loop [data data]
    (let [updated (try
                    (z/prewalk data
                               (partial match-expr? sym-before)
                               (partial refactor-match-expr opts))
                    (catch Exception _ data))]
      (if (z/rightmost? updated)
        updated
        (recur (z/right updated))))))

(defn refactor-all
  "Given a map with :path to a file or a string :str, returns
  a string with all of the match? expressions refactored as follows:

  Given a match? expression e.g.

    (match? <description> <actual> <expected>)
    ;; or
    (match? <description> <actual> <expected> <params>)

  Returns an expect expression e.g.

    (match? <expected> <actual>)
    ;; or
    (match? <expected> <actual> <params>)

  With :wrap-in-testing set to true, returns e.g.

    (testing <description> (match? <expected> <actual>))
    ;; or
    (testing <description> (match? <expected> <actual> <params>))

  Supported keys:
  - :str             this or path are required   - string source for the refactoring
  - :path            this or str are required    - path to source for refactoring
  - :rewrite         optional (default false)    - rewrites refactored code to the same path
  - :sym-before      optional (default `match?`) - symbol to look for for match? expressions
                       - use this key if you've got a qualified symbol
  - :sym-after       optional (default `match?`) - symbol to replace :sym-before
  - :wrap-in-testing optional (default false)    - set to true to wrap in testing

  This is intended to help you in refactoring to the new match? function, however
  there are some things you'll need to do on your own:

  - before
    - ensure that the the `match?` expressions you wish to refactor use the same
      symbol (simple or qualified) before refactoring
  - after
    - reformat for whitespace (manually or w/ cljfmt)
  - before or after
    - update the ns declaration to include state-flow.assertions.matcher-combinators
      - if :sym-after is simple i.e. just `match?`, then `:refer [match?]`
      - if :sym-after is qualified, then use `:as <alias>`"
  [{:keys [path str
           rewrite
           wrap-in-testing
           sym-before
           sym-after]
    :as opts}]
  (let [z-before (or (and path (z/of-file path))
                     (and str (z/of-string str)))
        z-after  (z/root-string (refactor-all* opts z-before))]
    (if (and path rewrite)
      (spit (io/file path) z-after)
      z-after)))

(comment
  (refactor-all {:path "test/state_flow/cljtest_test.clj"
                 :sym-before 'cljtest/match?
                 :sym-after 'assertions.matcher-combinators/match?
                 :wrap-in-testing true
                 :rewrite true}))
