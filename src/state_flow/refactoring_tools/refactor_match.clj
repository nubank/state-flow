(ns state-flow.refactoring-tools.refactor-match
  "This ns contains tools to refactor from match? in the cljtest
  ns to the new version in the assertions.matcher-combinators ns."
  (:require [clojure.java.io :as io]
            [rewrite-clj.node :as n]
            [rewrite-clj.zip :as z]
            [state-flow.probe :as probe]))

(defn probe-params [orig]
  (let [orig (and orig (z/sexpr orig))]
    (n/coerce
     (merge {:times-to-try probe/default-times-to-try
             :sleep-time   probe/default-sleep-time}
            orig))))

(defn replace-value [zloc]
  (if (= :fn (z/tag zloc))
    (z/sexpr zloc)
    (z/node zloc)))

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
                                       (z/replace (replace-value expected))
                                       z/right
                                       (z/replace (replace-value actual)))
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

(defn refactor-match-exprs
  "Given a map with a zipper :z, :path to a file or a string :str, returns
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
           z
           sym-before
           sym-after
           rewrite
           wrap-in-flow
           force-probe-params]
    :as   opts}]
  (let [opts*    (merge {:sym-before 'match?
                         :sym-after  'match?}
                        opts)
        z-before (or z
                     (and path (z/of-file path))
                     (and str (z/of-string str)))]
    (loop [zloc z-before]
      (let [updated (try
                      (z/postwalk zloc
                                  (partial match-expr? (:sym-before opts*))
                                  (partial refactor-match-expr opts*))
                      (catch Exception _ zloc))]
        (if (z/rightmost? updated)
          updated
          (recur (z/right updated)))))))

(defn refer? [zloc sym]
  (boolean
   (-> zloc
       z/down
       (z/find-value z/right :refer)
       z/right
       z/down
       (z/find-value z/right sym))))

(defn require-cljtest-refer-match?
  "Returns true if zloc represents a require vector with
    - state-flow.cljtest
    - :refer [match?] (not necessarily only match)"
  [zloc]
  (and (= :require (-> zloc z/leftmost z/sexpr))
       (= :vector (-> zloc z/tag))
       (= 'state-flow.cljtest (-> zloc z/down z/sexpr))
       (refer? zloc 'match?)))

(defn form-starting-with [zloc val]
  (-> zloc
      (z/find-value z/next val)
      (z/up)))

(defn require-form [zloc]
  (form-starting-with zloc :require))

(defn require-state-flow-core-form [zloc]
  (form-starting-with zloc 'state-flow.core))

(defn require-state-flow-core-refer-flow* [zloc]
  (let [require-state-flow-core-form* (-> zloc require-form require-state-flow-core-form)
        has-refer?                    (z/find-value require-state-flow-core-form* z/next :refer)]
    (cond (and require-state-flow-core-form* (refer? require-state-flow-core-form* 'flow))
          zloc
          (and require-state-flow-core-form* has-refer?)
          (z/edit-> zloc
                    require-state-flow-core-form
                    (z/find-value z/next :refer)
                    z/right
                    (z/append-child (z/node (z/of-string "flow"))))
          require-state-flow-core-form*
          (z/edit-> zloc
                    require-state-flow-core-form
                    (z/append-child (z/node (z/of-string ":refer")))
                    (z/append-child (z/node (z/of-string "[flow]"))))
          :else
          (z/edit-> zloc
                    (z/find-value z/next :require)
                    (z/up)
                    (z/append-child (n/newlines 1))
                    (z/append-child (z/node (z/of-string "[state-flow.core :refer [flow]]")))))))

(defn refactor-refer-match* [zloc]
  (cond-> zloc
    (not (refer? zloc 'defflow))
    (z/edit-> z/down
              (z/replace
               (z/node
                (z/of-string "state-flow.assertions.matcher-combinators"))))
    (refer? zloc 'defflow)
    (z/edit-> (z/insert-left
               (z/node
                (z/of-string "[state-flow.assertions.matcher-combinators :refer [match?]]")))
              (z/insert-left (n/newlines 1))
              z/down
              (z/find-value z/next 'match?)
              z/remove)))

(defn refactor-refer-match [opts zloc]
  (loop [zloc zloc]
    (let [updated (z/postwalk zloc require-cljtest-refer-match? refactor-refer-match*)]
      (if (z/rightmost? updated)
        updated
        (recur (z/right updated))))))

(defn require-state-flow-core-refer-flow [opts zloc]
  (loop [zloc zloc]
    (let [updated (z/postwalk zloc require-form require-state-flow-core-refer-flow*)]
      (if (z/rightmost? updated)
        updated
        (recur (z/right updated))))))

(defn refactor-ns-dec
  "Given a map with :path to a file or a string :str, returns
  a zipper with all of the ns declarations refactored as follows:

  Given an ns declaration with this in require:

    [state-flow.cljtest :refer [match?]]

  Refactor it to

    [state-flow.assertions.matcher-combinators :refer [match?]]

  If `:wrap-in-flow` is true, then ensures that this line is in
  the ns declaration

    [state-flow.core :refer [flow]]
  "
  [{:keys [z
           rewrite
           wrap-in-flow]
    :as opts}]
  (cond-> (z/edit->> z (refactor-refer-match opts))
    wrap-in-flow
    (z/edit->> (require-state-flow-core-refer-flow opts))))

(defn refactor!
  [{:keys [path
           str
           rewrite
           wrap-in-flow]
    :as   opts}]
  (let [z       (or (and path (z/of-file path))
                    (and str (z/of-string str)))
        with-ns (refactor-ns-dec (assoc opts :z z))
        with-match (refactor-match-exprs (assoc opts :z with-ns))
        result (z/root-string with-match)]
    (if (and path rewrite)
      (spit (io/file path) result)
      result)))
