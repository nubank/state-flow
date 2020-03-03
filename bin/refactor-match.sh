#!/bin/sh
#_(
   STATE_FLOW_ROOT=$(dirname $(dirname $(realpath $0)))
   DEPS="
   {:deps {rewrite-clj       {:mvn/version \"0.6.1\"}
           nubank/state-flow {:local/root \"$STATE_FLOW_ROOT\"}}}
   "
   exec clojure -Sdeps "$DEPS" "$0" "$@"
)

(require '[clojure.string :as str]
         '[rewrite-clj.zip :as z]
         '[state-flow.refactoring-tools.refactor-match :as refactor-match])

(defn opt-value [opts opt-name]
 (->> opts (drop-while (complement #(= opt-name %))) (drop 1) first))

(defn -main
  [& path-and-opts]
  (let [path (first path-and-opts)
        opts (rest path-and-opts)]
    (if (or (nil? path) (contains? (set path-and-opts) "--help"))
      (println
       (str/join "\n"
                 ["Usage"
                  ""
                  "bin/refactor-match.sh <path> [--rewrite] [--wrap-in-flow] [--force-probe-params] [--sym-before <sym-before>] [--sym-after <sym-after>]"
                  ""]))
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
        (if (:rewrite arg-map)
          (refactor-match/refactor! arg-map)
          (println (refactor-match/refactor! arg-map)))))))

  (apply -main *command-line-args*)
