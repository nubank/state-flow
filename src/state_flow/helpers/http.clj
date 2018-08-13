(ns state-flow.helpers.http
  (:require [common-http-client.components.mock-http :as mock-http]
            [nu.monads.state :as state]
            [state-flow.helpers.core :as helpers]))

(defn ^:deprecated make-request
  [req-fn]
  (assert (fn? req-fn) "First argument must be a function")
  (state/wrap-fn req-fn))

(defn add-responses
  [responses]
  (helpers/with-http #(mock-http/add-responses! % responses)))

(def get-responses (helpers/with-http #(mock-http/get-responses %)))

(def clear-requests! (helpers/with-http #(mock-http/clear-requests! %)))
