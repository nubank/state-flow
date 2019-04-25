(defproject nubank/state-flow "2.0.0"

  :description "Postman-like integration testing with composable flows"
  :url "https://github.com/nubank/state-flow"
  :license {:name "MIT"}

  :plugins [[lein-midje "3.2.1"]
            [lein-cloverage "1.1.1"]
            [lein-vanity "0.2.0"]
            [s3-wagon-private "1.3.1" :upgrade false]
            [lein-ancient "0.6.15"]
            [changelog-check "0.1.0"]]

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [com.stuartsierra/component "0.4.0"]
                 [funcool/cats "2.3.2"]]

  :exclusions   [log4j]

  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["config"]
                   :dependencies [[ns-tracker "0.3.1"]
                                  [nubank/matcher-combinators "0.9.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [midje "1.9.8"]
                                  [org.clojure/java.classpath "0.3.0"]]}}

  :aliases {"coverage" ["cloverage" "-s" "coverage"]
            "loc"      ["vanity"]}

  :repl-options  {:init-ns user}

  :min-lein-version "2.4.2"

  :resource-paths ["resources"]
  :test-paths ["test/"])
