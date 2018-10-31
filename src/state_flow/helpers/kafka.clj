(ns state-flow.helpers.kafka
  (:require [cats.core :as m]
            [common-http-client.components.mock-http :as mock-http]
            [common-kafka.components.mock-consumer :as mock-consumer]
            [common-kafka.components.mock-producer :as mock-producer]
            [common-test.postman.core :refer [as-of]]
            [state-flow.helpers.core :as helpers]
            [nu.monads.state :as state]))

(defn consume-message
  ([message]
   (helpers/with-consumer #(mock-consumer/consume! % message)))
  ([message as-of-time]
   (helpers/with-consumer #(as-of as-of-time (mock-consumer/consume! % message)))))

(defn safe-consume-message
  [message]
  (helpers/with-consumer #(try (mock-consumer/consume! % message)
                               (catch Exception e nil))))

(defn get-produced-messages
  [topic]
  (helpers/with-producer #(mock-producer/get-produced-messages % topic)))

(defn get-consumed-messages
  [topic]
  (helpers/with-consumer #(mock-consumer/get-consumed-messages % topic)))

(defn clear-produced-messages
  []
  (helpers/with-producer #(mock-producer/clear-messages! %)))

(defn ^:deprecated last-message
  "Use helpers.kafka/last-produced-message bellow instead"
  [topic]
  (m/fmap (comp :message last) (get-produced-messages topic)))

(defn last-produced-message
  [topic]
  (m/fmap (comp :message last) (get-produced-messages topic)))

(defn last-consumed-message
  [topic]
  (m/fmap (comp :message last) (get-consumed-messages topic)))

(defn get-deadletters
  [topic]
  (m/fmap
   #(map :message %)
   (helpers/with-consumer #(mock-consumer/get-deadletter-messages % topic))))
