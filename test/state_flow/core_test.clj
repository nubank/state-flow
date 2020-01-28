(ns state-flow.core-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [matcher-combinators.test :refer [match?]]
            [state-flow.test-helpers :as test-helpers]
            [state-flow.core :as state-flow :refer [flow]]
            [state-flow.state :as state]
            [cats.monad.exception :as e]))

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

(deftest run-flow
  (testing "default initial state is an empty map"
    (is (= {}
           (second (state-flow/run (flow "just return initial state"))))))

  (testing "with single step"
    (is (= {:value 2}
           (second (state-flow/run (flow "single step" add-two) {:value 0})))))

  (testing "with two steps"
    (let [[l r] (state-flow/run (flow "flow"
                                  (flow "step 1" add-two)
                                  (flow "step 2" add-two))
                  {:value 0})]
      (is (= {:value 4} r))
      (is (= "flow" (state-flow/top-level-description r)))))

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
      (is (= {:value 2} right))))

  (testing "flow allows do-let style binding"
    (is (match?
         {:value 4}
         (second (state-flow/run flow-with-bindings {:value 2}))))))

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

  (testing "flow with cleanup and exception"
    (let [cleanup-runs (atom 0)]
      (is (thrown-with-msg? Exception #"root \(line \d+\) -> child2 \(line \d+\)"
                            (state-flow/run* {:init    (constantly {:value 0})
                                              :cleanup (fn [& _] (swap! cleanup-runs inc))}
                              bogus-flow)))
      (is (= 1 @cleanup-runs))))

  (testing "flow with cleanup and exception, but ignoring it instead"
    (let [result (state-flow/run* {:init     (constantly {:value 0})
                                   :on-error state-flow/ignore-error}
                   bogus-flow)]
      (is (e/exception? (first result)))
      (is (match? {:value 2} (second result)))))

  (testing "flow with custom runner"
    (is (match? {:value 4}
                (-> (state-flow/run* {:init   (constantly {:value 0})
                                      :runner (fn [flow state]
                                                [nil (state-flow/run flow state)])}
                      nested-flow)
                    second
                    second)))))

(deftest state-flow-run!
  (testing "default initial state is an empty map"
    (is (= {}
           (second (state-flow/run! (flow "just return initial state"))))))

  (testing "run! throws exception"
    (is (thrown-with-msg? Exception #"root \(line \d+\) -> child2 \(line \d+\)"
                          (test-helpers/run-flow bogus-flow {:value 0})))))

(deftest as-step-fn
  (let [add-two-fn (state-flow/as-step-fn (state/modify #(+ 2 %)))]
    (is (= 3 (add-two-fn 1)))))

(defmacro line-number-of-call-site []
  (let [m (meta &form)]
    `(:line ~m)))

(defn consecutive?
  "Returns true iff ns (minimum of 2) all increase by 1"
  [& ns]
  (and (>= (count ns) 2)
       (let [a (first ns)
             z (inc (last ns))]
         (and (< a z)
              (= (range a z) ns)))))

(deftest current-description
  (testing "top level flow"
    (is (re-matches #"level 1 \(line \d+\)"
                    (first (state-flow/run (flow "level 1" (state-flow/current-description)))))))

  (testing "nested flows"
    (is (re-matches #"level 1 \(line \d+\) -> level 2 \(line \d+\)"
                    (first (state-flow/run (flow "level 1"
                                             (flow "level 2"
                                               (state-flow/current-description)))))))

    ;; WARNING: this (admittedly brittle) test depends on the following 4 lines
    ;; staying in sequence. They can move up or down in this file together,
    ;; but re-order them at your own peril.
    (let [line-number-before-flow-invocation (line-number-of-call-site)
          [desc] (state-flow/run (flow "level 1"
                                   (flow "level 2"
                                     (flow "level 3"
                                       (state-flow/current-description)))))]
      (is (re-matches #"level 1 \(line \d+\) -> level 2 \(line \d+\) -> level 3 \(line \d+\)" desc))
      (testing "line numbers are correct"
        (let [[level-1-line
               level-2-line
               level-3-line]
              (->> desc
                   (re-find #"level 1 \(line (\d+)\) -> level 2 \(line (\d+)\) -> level 3 \(line (\d+)\)")
                   (drop 1)
                   (map #(Integer/parseInt %)))]
          (is (consecutive? line-number-before-flow-invocation
                            level-1-line
                            level-2-line
                            level-3-line))))))

  (testing "composition"
    (let [line-number-before-flow-invocation (line-number-of-call-site)
          level-3  (flow "level 3" (state-flow/current-description))
          level-2  (flow "level 2" level-3)
          level-1  (flow "level 1" level-2)
          [desc _] (state-flow/run level-1)]
      (is (re-matches #"level 1 \(line \d+\) -> level 2 \(line \d+\) -> level 3 \(line \d+\)"
                      desc))
      (testing "line numbers are correct, even when composed"
        (let [[level-1-line
               level-2-line
               level-3-line]
              (->> desc
                   (re-find #"level 1 \(line (\d+)\) -> level 2 \(line (\d+)\) -> level 3 \(line (\d+)\)")
                   (drop 1)
                   (map #(Integer/parseInt %)))]
          (is (consecutive? line-number-before-flow-invocation
                            level-3-line
                            level-2-line
                            level-1-line))))))

  (testing "after nested flows complete"
    (testing "within nested flows "
      (is (re-matches #"level 1 \(line \d+\)"
             (first (state-flow/run (flow "level 1"
                                      (flow "level 2")
                                      (state-flow/current-description))))))
      (is (re-matches #"level 1 \(line \d+\) -> level 2 \(line \d+\)"
             (first (state-flow/run (flow "level 1"
                                      (flow "level 2"
                                        (flow "level 3")
                                        (state-flow/current-description)))))))
      (is (re-matches #"level 1 \(line \d+\)"
             (first (state-flow/run (flow "level 1"
                                      (flow "level 2"
                                        (flow "level 3"))
                                      (state-flow/current-description)))))))))

(deftest top-level-description
  (let [tld (fn [flow] (->> (state-flow/run flow)
                            last
                            state-flow/top-level-description))]
    (is (= "level 1"
           (tld (flow "level 1"))
           (tld (flow "level 1"
                  (flow "level 2")))
           (tld (flow "level 1"
                  (flow "level 2")))
           (tld (flow "level 1"
                  (flow "level 2"
                    (flow "level 3"))
                  (flow "level 2 again"
                    (flow "level 3 again"))))))))

(deftest illegal-flow-args
  (testing "produce friendly failure messages"
    (is (re-find #"Expected a flow.*got.*identity"
                 (->> (state-flow/run (flow "flow" identity))
                      first
                      :failure
                      .getMessage)))
    (is (re-find #"Expected a flow.*got.*identity"
                 (->> (state-flow/run
                        (flow "flow"
                          [x identity]
                          (state/gets)))
                      first
                      :failure
                      .getMessage)))))
