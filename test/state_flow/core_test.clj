(ns state-flow.core-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [cats.core :as m]
            [cats.monad.state :as state]
            [state-flow.test-helpers :as th]
            [state-flow.core :as state-flow]
            [state-flow.state :as sf.state]))

(def bogus (state/state (fn [s] (throw (Exception. "My exception")))))
(def add-two
  (state/swap (fn [s] (update s :value + 2))))

(def nested-flow
  (state-flow/flow "root"
    (state-flow/flow "child1" add-two)
    (state-flow/flow "child2" add-two)))

(def flow-with-bindings
  (state-flow/flow "root"
    [original (state/gets :value)
     :let [doubled (* 2 original)]]
    (sf.state/modify #(assoc % :value doubled))))

(def bogus-flow
  (state-flow/flow "root"
    (state-flow/flow "child1" add-two)
    (state-flow/flow "child2" bogus add-two)))

(def empty-flow
  (state-flow/flow "empty"))

(deftest push-meta
  (is (= {:meta {:description [["mydesc"]
                               ["mydesc" "mydesc2"]]}}
         (state/exec (m/>> (#'state-flow/push-meta "mydesc")
                           (#'state-flow/push-meta "mydesc2")) {}))))

(deftest run-flow
  (testing "with single step"
    (is (= {:meta  {:description [["single step"]
                                  []]}
            :value 2}
           (state/exec (state-flow/flow "single step" add-two) {:value 0}))))
  (testing "with two steps"
    (is (= {:meta {:description [["two step flow"]
                                 ["two step flow" "first step"]
                                 ["two step flow"]
                                 ["two step flow" "second step"]
                                 ["two step flow"]
                                 []]}
            :value 4}
           (second (state-flow/run (state-flow/flow "two step flow"
                                     (state-flow/flow "first step" add-two)
                                     (state-flow/flow "second step" add-two))
                     {:value 0})))))

  (testing "empty flow runs without exception"
    (is (nil? (first (state-flow/run empty-flow {})))))

  (testing "flow without description fails at macro-expansion time"
    (is (re-find #"first argument .* must be .* description string"
                 (try
                   (macroexpand `(state-flow/flow (sf.state/return {})))
                   (catch clojure.lang.Compiler$CompilerException e
                     (.. e getCause getMessage))))))

  (testing "flow with a `(str ..)` expr for the description is fine"
    (is (macroexpand `(state-flow/flow (str "foo") [original (state/gets :value)
                                                    :let [doubled (* 2 original)]]
                        (sf.state/modify #(assoc % :value doubled))))))

  (testing "but flows with an expression that resolves to a string also aren't valid,
            due to resolution limitations at macro-expansion time"
    (is (re-find #"first argument .* must be .* description string"
                 (let [my-desc "trolololo"]
                   (try
                     (macroexpand `(state-flow/flow ~'my-desc [original (state/gets :value)
                                                               :let [doubled (* 2 original)]]
                                     (sf.state/modify #(assoc % :value doubled))))
                     (catch clojure.lang.Compiler$CompilerException e
                       (.. e getCause getMessage)))))))

  (testing "nested-flow-with exception, returns exception and state before exception"
    (let [[left right] (state-flow/run bogus-flow {:value 0})]
      (is (thrown-with-msg? Exception #"My exception" @left))
      (is (= {:meta  {:description [["root"]
                                    ["root" "child1"]
                                    ["root"]
                                    ["root" "child2"]]}
              :value 2}
             right))))

  (testing "flow allows do-let style binding"
    (is (match?
         {:value 4}
         (second (state-flow/run flow-with-bindings {:value 2})))))

  (testing "run! throws exception"
    (is (thrown? Exception (th/run-flow bogus-flow {:value 0})))))

(deftest state-flow-run*

  (testing "flow with initializer"
    (is (match? {:value 4}
                (second (state-flow/run* {:init (constantly {:value 0})} nested-flow)))))

  (testing "flow with cleanup"
    (is (zero?
         (-> (state-flow/run* {:init    (constantly {:value 0
                                                     :atom  (atom 1)})
                               :cleanup #(reset! (:atom %) 0)}
               nested-flow)
             second
             :atom
             deref))))

  (testing "flow with custom runner"
    (is (match? {:value 4}
                (second (state-flow/run* {:init   (constantly {:value 0})
                                          :runner (fn [flow state]
                                                    [nil (state/exec flow state)])}
                          nested-flow))))))

(deftest as-step-fn
  (let [add-two-step (state-flow/as-step-fn (state/swap #(+ 2 %)))]
    (is (= 3 (add-two-step 1)))))

(comment
  (t/run-tests))
