(ns state-flow.helpers.core
  (:require [cats.core :as m]
            [cljdev.core :as cljdev]
            [com.stuartsierra.component :as component]
            [common-datomic.db :as ddb]
            [cats.monad.state :as state]
            [nu.monads.state :as nu.state]
            [schema.core :as s]))

(s/defschema Flow (s/pred state/state?))

;;Resource fetchers
(def ^:private get-system   :system)
(def ^:private get-datomic  (comp :datomic :system))
(def ^:private get-db       (comp ddb/db :datomic :system))
(def get-http     (comp :http :system))
(def ^:private get-consumer (comp :consumer :system))
(def ^:private get-producer (comp :producer :system))
(def ^:private get-servlet  (comp :servlet :system))

(defn with-resource
  [resource-fetcher user-fn]
  (assert (ifn? resource-fetcher) "First argument must be callable")
  (assert (ifn? user-fn) "Second argument must be callable")
  (m/mlet [resource (state/gets resource-fetcher)]
    (m/return (user-fn resource))))

(defn with-db
  "State monad that returns the result of executing db-fn with db as argument"
  [db-fn]
  (with-resource get-db db-fn))

(defn with-datomic
  "State monad that returns the result of executing datomic-fn with datomic as argument"
  [datomic-fn]
  (with-resource get-datomic datomic-fn))

(defn with-http
  [http-fn]
  (with-resource get-http http-fn))

(defn with-producer
  [kafka-fn]
  (with-resource get-producer kafka-fn))

(defn with-consumer
  [kafka-fn]
  (with-resource get-consumer kafka-fn))

(defn with-servlet
  [servlet-fn]
  (with-resource get-servlet servlet-fn))

(def update-system-dependencies
  (nu.state/swap (fn [world] (update-in world [:system] component/update-system (keys (:system world)) identity))))

(defn system-swap
  "Input is a step that makes changes to the system map. Ensures dependencies are updated"
  [swap-step]
  (m/>> swap-step update-system-dependencies))

(defn update-component
  [component-key update-fn]
  (system-swap (nu.state/swap #(update-in % [:system component-key] update-fn))))

(def ftap (partial m/fmap cljdev/tap))
(defn functor-pprint
  [form]
  `(ftap ~form))
