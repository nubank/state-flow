(ns state-flow.core-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [matcher-combinators.test :refer [match?]]
            [cats.core :as cats]
            [state-flow.test-helpers :as test-helpers]
            [state-flow.core :as state-flow :refer [flow]]
            [state-flow.state :as state]))

(def bogus (state/gets (fn [_] (throw (Exception. "My exception")))))

(def add-two (state/modify (fn [s] (update s :value + 2))))

(def nested-flow
  (flow "root"
    (flow "child 1" add-two)
    (flow "child 2" add-two)))

(def flow-with-bindings
  (flow "root"
    [original (state/gets :value)
     :let [doubled (* 2 original)]]
    (state/modify #(assoc % :value doubled))))

(def bogus-flow
  (flow "root"
    (flow "child1" add-two)
    (flow "child2" bogus add-two)))

(def empty-flow
  (flow "empty"))

(deftest push-meta
  (is (= {:meta {:description [["mydesc"]
                               ["mydesc" "mydesc2"]]}}
         (state/exec (cats/>> (#'state-flow/push-meta "mydesc")
                              (#'state-flow/push-meta "mydesc2")) {}))))

(deftest run-flow
  (testing "with single step"
    (is (= {:meta  {:description [["single step"]
                                  []]}
            :value 2}
           (second (state-flow/run (flow "single step" add-two) {:value 0})))))

  (testing "with two steps"
    (is (= {:meta {:description [["flow"]
                                 ["flow" "step 1"]
                                 ["flow"]
                                 ["flow" "step 2"]
                                 ["flow"]
                                 []]}
            :value 4}
           (second (state-flow/run (flow "flow"
                                     (flow "step 1" add-two)
                                     (flow "step 2" add-two))
                     {:value 0})))))

  (testing "empty flow runs without exception"
    (is (nil? (first (state-flow/run empty-flow {})))))

  (testing "flow without description fails at macro-expansion time"
    (is (re-find #"first argument .* must be .* description string"
                 (try
                   (macroexpand `(flow (state/return {})))
                   (catch clojure.lang.Compiler$CompilerException e
                     (.. e getCause getMessage))))))

  (testing "flow with a `(str ..)` expr for the description is fine"
    (is (macroexpand `(flow (str "foo") [original (state/gets :value)
                                                    :let [doubled (* 2 original)]]
                        (state/modify #(assoc % :value doubled))))))

  (testing "but flows with an expression that resolves to a string also aren't valid,
            due to resolution limitations at macro-expansion time"
    (is (re-find #"first argument .* must be .* description string"
                 (let [my-desc "trolololo"]
                   (try
                     (macroexpand `(flow ~'my-desc [original (state/gets :value)
                                                               :let [doubled (* 2 original)]]
                                     (state/modify #(assoc % :value doubled))))
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
    (is (thrown? Exception (test-helpers/run-flow bogus-flow {:value 0})))))

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
                (-> (state-flow/run* {:init   (constantly {:value 0})
                                      :runner (fn [flow state]
                                                [nil (state-flow/run flow state)])}
                      nested-flow)
                    second
                    second)))))

(deftest as-step-fn
  (let [add-two-fn (state-flow/as-step-fn (state/modify #(+ 2 %)))]
    (is (= 3 (add-two-fn 1)))))
