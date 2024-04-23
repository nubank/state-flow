(ns nubank.state-flow
  (:refer-clojure :exclude [with-redefs])
  (:require [clj-kondo.hooks-api :as hooks]))

(defn- normalize-mlet-binds
  [node]
  (vec
   (reduce
    (fn [acc [b v]]
      (if (and (= :let (hooks/sexpr b)) (hooks/vector-node? v))
        (concat acc (:children v))
        (concat acc [b v])))
    []
    (partition 2 (:children node)))))

(defn- do-let [forms]
  (let [new-bindings (vec (reduce (fn [acc i]
                                    (if (hooks/vector-node? i)
                                      (concat acc (normalize-mlet-binds i))
                                      (concat acc [(hooks/token-node '_) i]))) [] forms))]
    (hooks/list-node
     [(hooks/token-node 'let)
      (hooks/vector-node new-bindings)])))

(defn flow [{:keys [node]}]
  (let [forms (rest (:children node))]
    {:node (with-meta (do-let forms) (meta node))}))

(defn defflow [{:keys [node]}]
  (let [[test-name & body]     (rest (:children node))
        new-node (hooks/list-node
                  [(hooks/token-node 'def)
                   test-name
                   (do-let body)])]
    {:node (with-meta new-node (meta node))
     :defined-by 'state-flow.cljtest/defflow}))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn with-redefs
  "This transformation hook converts

   (state-flow.labs.state/with-redefs [bindings & flows])

   into

   (clojure.core/with-redefs bindings
     (state-flow.core/flow \"state-flow.labs.state/with-redefs\" flows))"
  [{:keys [node]}]
  (let [[bindings & flows] (rest (:children node))
        new-node (hooks/list-node
                  [(hooks/token-node 'clojure.core/with-redefs)
                   bindings
                   (hooks/list-node
                    (concat [(hooks/token-node 'state-flow.api/flow)
                             (hooks/string-node "state-flow.labs.state/with-redefs")]
                            flows))])]
    {:node (with-meta new-node (meta node))
     :defined-by 'state-flow.labs.state/with-redefs}))
