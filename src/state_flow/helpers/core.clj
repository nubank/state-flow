(ns state-flow.helpers.core
  (:require [cats.core :as m]
            [nu.monads.state :as state]
            [common-datomic.db :as ddb]))

;;Resource fetchers
(def ^:private get-datomic  (comp :datomic :system))
(def ^:private get-db       (comp ddb/db :datomic :system))
(def ^:private get-http     (comp :http :system))
(def ^:private get-consumer (comp :consumer :system))
(def ^:private get-producer (comp :producer :system))

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
