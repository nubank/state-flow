(ns state-flow.labs.state-test
  (:require [clojure.string :as str]
            [clojure.test :as t :refer [deftest is testing]]
            [state-flow.labs.state :as labs.state]))

(defn- set-redefs-macro-feature-flag [v f]
  (let [old-v labs.state/*with-redefs-macro-enabled*
        _ (alter-var-root #'labs.state/*with-redefs-macro-enabled* (constantly v))
        result (f)]
    result))

(def with-redefs-macro-enabled (partial set-redefs-macro-feature-flag true))
(def with-redefs-macro-disabled (partial set-redefs-macro-feature-flag false))

(def flow-form `(labs.state/with-redefs [a 1, b 2] some-flow))

(deftest with-redefs-feature-flag-test
  (testing "if disabled, macroexpansion throws assertion warning user and giving instructions"
    (is (thrown? clojure.lang.Compiler$CompilerException
                 (with-redefs-macro-disabled #(macroexpand flow-form))))
    (is (try (with-redefs-macro-disabled #(macroexpand flow-form))
             (catch clojure.lang.Compiler$CompilerException ex
               (is (instance? java.lang.AssertionError (ex-cause ex)))
               (is (-> ex ex-cause ex-message
                       (str/includes? "`with-redefs` usage is not recommended. If you know what you're doing and really want to continue, set `*with-redefs-macro-enabled*` to true")))))))
  (testing "if enabled, macroexpansion works as expected"
    (is (seq? (with-redefs-macro-enabled #(macroexpand flow-form))))))
