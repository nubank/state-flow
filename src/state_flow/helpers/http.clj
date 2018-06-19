(ns state-flow.helpers.http
  (:require [common-http-client.components.mock-http :as mock-http]
            [state-flow.helpers.core :as helpers]
            [nu.monads.state :as state]))

(defn make-request
  [req-fn http-responses]
  (assert (fn? req-fn) "First argument must be a function")
  (state/wrap-fn req-fn))

(defn add-responses
  [responses]
  (helpers/with-http #(mock-http/add-responses! % responses)))

