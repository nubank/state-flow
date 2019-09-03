(defproject nubank/state-flow "2.0.1"
  :description "Postman-like integration testing with composable flows"
  :url "https://github.com/nubank/state-flow"
  :license {:name "MIT"}

  :plugins [[lein-midje "3.2.1"]
            [lein-cloverage "1.0.10"]
            [lein-vanity "0.2.0"]
            [s3-wagon-private "1.3.1"]
            [lein-ancient "0.6.15"]
            [changelog-check "0.1.0"]]

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.taoensso/timbre "4.10.0"]
                 [com.stuartsierra/component "0.4.0"]
                 [funcool/cats "2.3.2"]
                 [nubank/matcher-combinators "1.2.1"]]

  :exclusions   [log4j]

  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["config"]
                   :dependencies [[ns-tracker "0.4.0"]
                                  [org.clojure/tools.namespace "0.3.1"]
                                  [midje "1.9.9"]
                                  [org.clojure/java.classpath "0.3.0"]]}}

  :aliases {"coverage" ["cloverage" "-s" "coverage"]
            "loc"      ["vanity"]}

  :repl-options  {:init-ns user}

  :min-lein-version "2.4.2"

  :resource-paths ["resources"]
  :test-paths ["test/"])
