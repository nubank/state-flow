(ns state-flow.helpers.http
  (:require [cats.core :as m]
            [common-http-client.components.mock-http :as mock-http]
            [cats.monad.state :as state]
            [state-flow.core :as core]
            [state-flow.helpers.core :as helpers]))

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

(defn get-requests
  [url]
  (helpers/with-http #(mock-http/get-requests % url)))

(defn with-responses
  "Experimental: expect breaking changes"
  [responses flow]
  (m/do-let
   [old-responses get-responses]
   (clear-requests!) ;also clears responses
   (add-responses responses)
   [ret flow]
   (add-responses old-responses)
   (m/return ret)))
