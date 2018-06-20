(defproject state-flow "0.1.2-SNAPSHOT"
  :description "Postman-like integration testing with composable flows"
  :url "https://github.com/nubank/state-flow"
  :license {:name "Proprietary"}

  :plugins [[lein-midje "3.2.1"]
            [lein-cloverage "1.0.10"]
            [lein-vanity "0.2.0"]
            [s3-wagon-private "1.3.1"]
            [lein-ancient "0.6.15"]
            [changelog-check "0.1.0"]]

  :repositories  [["nu-maven" {:url "s3p://nu-maven/releases/"}]
                  ["central" {:url "http://repo1.maven.org/maven2/" :snapshots false}]
                  ["clojars" {:url "https://clojars.org/repo/"}]]

  :deploy-repositories [["releases" {:url "s3p://nu-maven/releases/" :no-auth true}]]

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [funcool/cats "2.2.1"]

                 [common-core "11.17.0"]
                 [common-io   "43.17.0"]
                 [common-datomic "5.22.0"]
                 [common-kafka "8.2.0"]
                 [common-http-client "7.13.6"]
                 [common-test "12.14.0"]
                 [nu-algebraic-data-types "0.2.1"]]

  :exclusions   [log4j]

  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["config"]
                   :dependencies [[ns-tracker "0.3.1"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [midje "1.9.1"]
                                  [org.clojure/java.classpath "0.3.0"]]}}

  :aliases {"coverage" ["cloverage" "-s" "coverage"]
            "loc"      ["vanity"]}

  :repl-options  {:init-ns user}

  :min-lein-version "2.4.2"

  :resource-paths ["resources"]
  :test-paths ["test/"])
