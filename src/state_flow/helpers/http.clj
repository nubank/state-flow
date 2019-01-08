(ns state-flow.helpers.http
  (:require [cats.core :as m]
            [common-http-client.components.mock-http :as mock-http]
            [common-http-client.postman.helpers :as postman.helpers]
            [state-flow.core :as core]
            [state-flow.helpers.core :as helpers]))

(defn ^:private delta-requests
  [before after]
  (reduce (fn [deltas url]
            (assoc deltas url (drop (count (get before url)) (get after url))))
          {}
          (keys after)))

(defn ^:deprecated make-request
  "Use helpers.servlet/request instead"
  [req-fn]
  (assert (fn? req-fn) "First argument must be a function")
  (core/wrap-fn req-fn))

(defn add-responses
  [responses]
  (helpers/with-http #(mock-http/add-responses! % responses)))

(def get-responses (helpers/with-http #(mock-http/get-responses %)))

(def clear-requests! (helpers/with-http #(mock-http/clear-requests! %)))
(def clear-responses! (helpers/with-http #(mock-http/clear-responses! %)))

(def get-requests (helpers/with-http #(mock-http/get-requests %)))

(defn with-responses
  "execute flow with added `responses` and restores previous responses afterward
   flow returns delta of requests made and the return value of executing `flow`

  Usage within a flow:
  ```
  [{:keys [requests response]}
   (with-responses {:bookmark1 {:status 200 :body {}}
                    :bookmark2 {:status 200 :body {}}}
                   (flow ...))]
  ```
  "
  [responses flow]
  (m/do-let
   [old-responses get-responses
    old-requests  get-requests]
   (add-responses responses)
   [ret      flow
    requests get-requests]
   clear-responses!
   (add-responses old-responses)
   (m/return {:requests (delta-requests old-requests requests)
              :response ret})))
