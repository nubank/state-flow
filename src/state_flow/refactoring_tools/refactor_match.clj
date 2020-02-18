(ns state-flow.refactoring-tools.refactor-match
  "This ns contains tools to refactor from match? in the cljtest
  ns to the new version in the assertions.matcher-combinators ns."
  (:require [clojure.java.io :as io]
            [rewrite-clj.zip :as z]
            [rewrite-clj.node :as n]
            [state-flow.probe :as probe]))

(defn probe-params [orig]
  (let [orig (and orig (z/sexpr orig))]
    (n/coerce
     (merge {:times-to-try probe/default-times-to-try
             :sleep-time   probe/default-sleep-time}
            orig))))

(defn refactor-match-expr
  "If there is an exception, printlns the expression so you can
  find and handle it manually."
  [{:keys [wrap-in-flow
           force-probe-params
           sym-after]}
   zloc]
  (try
    (let [match-sym  (-> zloc z/down)
          desc       (-> match-sym z/right)
          actual     (-> desc z/right)
          expected   (-> actual z/right)
          params     (-> expected z/right)
          refactored (cond-> (z/edit-> zloc
                                       z/down
                                       (z/replace sym-after)
                                       z/right
                                       z/remove
                                       z/right
                                       (z/replace (z/node expected))
                                       z/right
                                       (z/replace (z/node actual)))
                       (and force-probe-params params)
                       (z/edit-> z/down
                                 z/rightmost
                                 (z/replace (probe-params params)))
                       (and force-probe-params (not params))
                       (z/edit-> (z/append-child (probe-params params))))]
      (if wrap-in-flow
        (z/edit-> zloc
                  (z/replace
                   (-> (z/of-string "(flow)")
                       (z/append-child (z/node desc))
                       (z/append-child (n/newlines 1))
                       (z/append-child (z/node refactored))
                       (z/node))))
        refactored))
    (catch Exception e
      (println "Error processing " (z/string zloc))
      (println e)
      zloc)))

(defn match-expr? [match-sym node]
  (when (= :list (z/tag node))
    (when-let [op (z/down node)]
      (= match-sym (z/sexpr op)))))

(defn ^:private refactor-match-exprs* [{:keys [sym-before] :as opts} zloc]
  (loop [zloc zloc]
    (let [updated (try
                    (z/postwalk zloc
                               (partial match-expr? sym-before)
                               (partial refactor-match-expr opts))
                    (catch Exception _ zloc))]
      (if (z/rightmost? updated)
        updated
        (recur (z/right updated))))))

(defn refactor-match-exprs
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

  With :wrap-in-flow set to true, returns e.g.

    (flow <description> (match? <expected> <actual>))
    ;; or
    (flow <description> (match? <expected> <actual> <params>))

  Supported keys:
  - :str                 this or path are required   - string source for the refactoring
  - :path                this or str are required    - path to source for refactoring
  - :rewrite             optional (default false)    - rewrites refactored code to the same path
  - :sym-before          optional (default `match?`) - symbol to look for for match? expressions
                           - use this key if you've got a qualified symbol
  - :sym-after           optional (default `match?`) - symbol to replace :sym-before
  - :wrap-in-flow        optional (default false)    - set to true to wrap in a flow with the description
                                                       from the source match? expression
  - :force-probe-params  optional (default false)    - makes probe params explicit (strongly recommended)

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
      - if :sym-after is qualified, then use `:as <alias>`

  WARNING: the old version of match? probes implicitly when `actual` is a step. The new
  version requires an explicit `{:times-to-try <value gt 1>}` to trigger polling, so
  leaving out :force-probe-params may result in tests failing because they need probe."
  [{:keys [path
           str
           sym-before
           sym-after
           rewrite
           wrap-in-flow
           force-probe-params]
    :as   opts}]
  (let [opts*    (merge {:sym-before 'match?
                         :sym-after  'match?}
                        opts)
        z-before (or (and path (z/of-file path))
                     (and str (z/of-string str)))
        z-after  (z/root-string (refactor-match-exprs* opts* z-before))]
    (if (and path rewrite)
      (spit (io/file path) z-after)
      z-after)))

(defn old-require? [zloc]
  (and (= :require (-> zloc z/leftmost z/sexpr))
       (= :vector (-> zloc z/tag))
       (= 'state-flow.cljtest (-> zloc z/down z/sexpr))))

(defn refactor-require** [zloc]
  (z/edit-> zloc
            z/down
            (z/replace (z/node (z/of-string "state-flow.assertions.matcher-combinators")))))

(defn refactor-require* [zloc]
  (loop [zloc zloc]
    (let [updated (try
                    (z/postwalk zloc old-require? refactor-require**)
                    (catch Exception _ zloc))]
      (if (z/rightmost? updated)
        updated
        (recur (z/right updated))))))

(defn refactor-require
  "Given a map with :path to a file or a string :str, returns
  a string with all of the match? expressions refactored as follows:

  Given an ns declaration with this in require:

    [state-flow.cljtest :refer [match?]]

  Refactor it to

    [state-flow.assertions.matcher-combinators :refer [match?]]"
  [{:keys [path
           str
           rewrite]}]
  (let [z-before (or (and path (z/of-file path))
                     (and str (z/of-string str)))
        z-after  (z/root-string (refactor-require* z-before))]
    (if (and path rewrite)
      (spit (io/file path) z-after)
      z-after)))

(comment
  (refactor-match-exprs {:path "test/state_flow/cljtest_test.clj"
                         :sym-before 'cljtest/match?
                         :sym-after 'assertions.matcher-combinators/match?
                         :wrap-in-flow true
                         :rewrite true}))
