(defproject nubank/state-flow "1.11.0"

  :description "Postman-like integration testing with composable flows"
  :url "https://github.com/nubank/state-flow"
  :license {:name "Proprietary"}

  :plugins [[lein-midje "3.2.1"]
            [lein-cloverage "1.0.10"]
            [lein-vanity "0.2.0"]
            [s3-wagon-private "1.3.1"]
            [lein-ancient "0.6.15"]
            [changelog-check "0.1.0"]]

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [com.stuartsierra/component "0.3.2"]
                 [funcool/cats "2.3.2"]]

  :exclusions   [log4j]

  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["config"]
                   :dependencies [[ns-tracker "0.3.1"]
                                  [nubank/matcher-combinators "0.3.4"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [midje "1.9.1"]
                                  [org.clojure/java.classpath "0.3.0"]]}}

  :aliases {"coverage" ["cloverage" "-s" "coverage"]
            "loc"      ["vanity"]}

  :repl-options  {:init-ns user}

  :min-lein-version "2.4.2"

  :resource-paths ["resources"]
  :test-paths ["test/"])
