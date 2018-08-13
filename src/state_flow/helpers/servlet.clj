(ns state-flow.helpers.servlet
  (:require [common-io.interceptors.wire :as wire]
            [common-io.test-helpers :as th]
            [common-test.http :as http]))

(defn req
  "Experimental"
  [servlet method content-type uri body]
  (th/with-debug-error-logging
    (let [request-content-type (get th/content-headers content-type)]
      (assert (not (nil? request-content-type)) (str "Content type '" content-type "' not recognized"))
      (let [request-headers               (http/headers request-content-type)
            {:keys [headers status body] :as response-map} (http/raw-req! servlet method uri (http/parse-data body content-type) request-headers)
            accept                        (get request-headers "Accept" request-content-type)
            response-type                 (wire/header->content-type (get headers "Content-Type" accept))]
        (merge response-map {:body (th/output-stream->data body response-type)})))))
