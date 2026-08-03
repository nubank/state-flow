(ns state-flow.walkthrough-test
  "Guards doc/walkthrough.repl against silent drift from the public API (#107)."
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.test :refer [deftest is testing]]
            [state-flow.api :as flow]))

(def walkthrough-path "doc/walkthrough.repl")

(deftest walkthrough-file-exists
  (is (.exists (io/file walkthrough-path))
      (str "expected " walkthrough-path " to exist")))

(deftest walkthrough-references-current-public-api
  (testing "symbols shown in the walkthrough still resolve on state-flow.api"
    (let [body (slurp walkthrough-path)
          required-snippets ["flow/swap-state"
                             "flow/get-state"
                             "match?"
                             "(flow \""
                             "(run"]]
      (doseq [snippet required-snippets]
        (is (string/includes? body snippet)
            (str "walkthrough should mention " (pr-str snippet))))
      (doseq [sym '[state-flow.api/swap-state
                    state-flow.api/get-state
                    state-flow.api/run
                    state-flow.api/match?
                    state-flow.api/flow]]
        (is (some? (resolve sym))
            (str "API var missing: " sym))))))
