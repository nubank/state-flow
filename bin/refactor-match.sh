#!/bin/sh
#_(
   DEPS='
   {:deps {rewrite-clj {:mvn/version "0.6.1"}
           nubank/state-flow {:mvn/version "2.2.5"}}}
   '
   exec clojure -Sdeps "$DEPS" "$0" "$@"
)

(require '[rewrite-clj.zip :as z]
         '[state-flow.refactoring-tools.refactor-match :as refactor-match])

(defn opt-value [opts opt-name]
 (->> opts (drop-while (complement #(= opt-name %))) (drop 1) first))


(defn -main
 [path & opts]
 (let [arg-map {:path path
                :rewrite (contains? (set opts) "--rewrite")
                :wrap-in-flow (contains? (set opts) "--wrap-in-flow")
                :force-probe-params (contains? (set opts) "--force-probe-params")
                :sym-before (if-let [sym-before (opt-value opts "--sym-before")]
                              (symbol sym-before)
                              'match?)
                :sym-after (if-let [sym-after (opt-value opts "--sym-after")]
                             (symbol sym-after)
                             'match? )}]
  (println (str "Processing " path "..."))
  (prn arg-map)
  (refactor-match/refactor-require (select-keys arg-map [:path :rewrite]))
  (refactor-match/refactor-match-exprs arg-map)))

(apply -main *command-line-args*)
