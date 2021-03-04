(ns hooks.state-flow
  (:require [clj-kondo.hooks-api :as api]))

(defn ^:private defflow-bindings [nodes]
  (->> nodes
       (filter api/vector-node?)
       (map (fn [node]
              (let [[sym val] (:children node)]
                [sym val])))
       flatten
       vec))

(defn ^:private defflow-flows [nodes]
  (filter (complement api/vector-node?) nodes))

(defn defflow [{:keys [:node]}]
  (let [[name & flows] (rest (:children node))
        new-node (api/list-node
                  (list
                   (with-meta (api/token-node 'defn) (meta name))
                   (with-meta (api/token-node (api/sexpr name)) (meta name))
                   (api/vector-node [])
                   (api/list-node (list* (api/token-node 'let)
                                         (api/vector-node (defflow-bindings flows))
                                         (defflow-flows flows)))))]
    {:node new-node}))
