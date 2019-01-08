(ns state-flow.helpers.http-test
  (:require [com.stuartsierra.component :as component]
            [common-core.misc :refer [read-json write-json]]
            [common-core.protocols.http-client :as h-pro]
            [common-core.test-helpers :as th]
            [common-http-client.components.http.config :as config]
            [common-http-client.components.mock-http :as mock-http]
            [common-http-client.components.routes :as rt-com]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [io.pedestal.interceptor :as i]
            [matcher-combinators.midje :refer [match]]
            [midje.sweet :refer :all]
            [state-flow.core :as core]
            [state-flow.helpers.core :as helpers]
            [state-flow.helpers.http :as http]))

(def config-json {:environment "dev"
                  :services    {:accounts "http://accounts"}
                  :bookmarks   {:remote "http://remote/:id"
                                :rem    "http://rem/:id"
                                :bar    "htto://otherremote/:foo"
                                :a-url  "http://a-url"}})

(def dummy-config (th/dummy-config "accounts" (read-json (write-json config-json))))

(def interceptor (with-meta (i/interceptor {:enter identity}) {:nu/auth-checked true}))

(defroutes routes [[["/local/named/route" ^:interceptors [interceptor] {:get [:local identity]}]
                    ["/local/:id/route" ^:interceptors [interceptor] {:get [:loc identity]}]]])

(defn create-mock-http
  ([]
   (create-mock-http nil))
  ([status-handler]
   (create-mock-http status-handler {}))
  ([status-handler bookmarks-settings]
   (-> (component/system-map
         :config dummy-config
         :routes (component/using (rt-com/new-routes #'routes {:bookmarks-settings bookmarks-settings}) [:config])
         :http (component/using (if status-handler
                                  (mock-http/new-mock-http config/json-defaults status-handler)
                                  (mock-http/new-mock-http)) [:routes]))
       component/start
       :http)))

(facts "on with-responses"
  (let [com       (create-mock-http)
        world     {:system {:http com}}
        responses {"url1" {:status 200 :body {}} "url2" {:status 200 :body {}}}
        run       (core/run! (http/with-responses responses http/get-responses) world)]
    (fact "no requests made"
      (first (core/run! (http/with-responses responses http/get-responses) world)) => {:response responses
                                                                                       :requests {}})
    (fact "we make a request"
      (let [flow-with-request (http/with-responses responses
                                (core/flow ""
                                  (helpers/with-http #(h-pro/req! % {:url "url1"}))
                                  http/get-responses))]
        (first (core/run! flow-with-request world))
        => (match {:response responses
                   :requests {"url1" [map?]}})))))
