(defproject nubank/state-flow "5.17.0"
  :description "Integration testing with composable flows"
  :url "https://github.com/nubank/state-flow"
  :license {:name "MIT"}

  :repositories [["publish" {:url "https://clojars.org/repo"
                             :username :env/clojars_username
                             :password :env/clojars_passwd
                             :sign-releases false}]]

  :plugins [[lein-project-version "0.1.0"]
            [lein-midje "3.2.2"]
            [lein-cloverage "1.2.4"]
            [lein-vanity "0.2.0"]
            [s3-wagon-private "1.3.5"]
            [lein-ancient "0.7.0"]
            [lein-cljfmt "0.9.2"]
            [lein-nsorg "0.3.0"]
            [changelog-check "0.1.0"]]

  :dependencies [[org.clojure/clojure "1.11.4"]
                 [com.taoensso/timbre "6.5.0"]
                 [funcool/cats "2.4.2"]
                 [nubank/matcher-combinators "3.9.1"]]

  :exclusions   [log4j]

  :cljfmt {:indents {mlet  [[:block 1]]
                     facts [[:block 1]]
                     fact  [[:block 1]]
                     flow  [[:block 1]]}}

  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["dev"]
                   :dependencies [[ns-tracker "1.0.0"]
                                  [org.clojure/tools.namespace "1.5.0"]
                                  [midje "1.10.10"]
                                  [org.clojure/java.classpath "1.1.0"]
                                  [rewrite-clj "1.1.48"]]}}

  :aliases {"coverage" ["cloverage" "-s" "coverage"]
            "lint"     ["do" ["cljfmt" "check"] ["nsorg"]]
            "lint-fix" ["do" ["cljfmt" "fix"] ["nsorg" "--replace"]]
            "loc"      ["vanity"]}

  :repl-options  {:init-ns user}

  :min-lein-version "2.4.2"

  :resource-paths ["resources"]
  :test-paths ["test/"])
