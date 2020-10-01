(ns state-flow.core-test
  (:require [cats.monad.exception :as e]
            [clojure.test :as t :refer [deftest is testing]]
            [matcher-combinators.test :refer [match?]]
            [state-flow.core :as state-flow :refer [flow]]
            [state-flow.state :as state]
            [state-flow.test-helpers :as test-helpers :refer [this-line-number]]))

(def bogus (state/gets (fn [_] (throw (Exception. "My exception")))))

(def add-two (state/modify update :value + 2))

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

(deftest test-flow
  (testing "flow without description fails at macro-expansion time"
    (is (re-find #"first argument .* must be .* description string"
                 (try
                   (macroexpand `(flow (state/return {})))
                   (catch clojure.lang.Compiler$CompilerException e
                     (.. e getCause getMessage))))))

  (testing "flow with vector as last argument fails at macro-expansion time"
    (is (re-find #"last argument .* must be a flow/step"
                 (try
                   (macroexpand `(flow "" [x (state/get-state)]))
                   (catch clojure.lang.Compiler$CompilerException e
                     (.. e getCause getMessage)))))))

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

  (testing "flow with custom runner"
    (let [{:keys [l r]} (state-flow/run* {:init   (constantly {:count 0})
                                          :runner (fn [flow state]
                                                    (let [[l r] (state-flow/run flow state)]
                                                      {:l l :r r}))}
                                         (state/modify update :count inc))]
      (is (= {:count 0} l))
      (is (= {:count 1} r))))

  (testing "flow with cleanup"
    (is (zero?
         (-> (state-flow/run* {:init    (constantly {:value 0
                                                     :atom  (atom 1)})
                               :cleanup #(reset! (:atom %) 0)}
                              nested-flow)
             second
             :atom
             deref))))

  (testing "flow with exception and cleanup"
    (let [cleanup-runs   (atom 0)
          on-error-input (atom nil)]
      (is (state-flow/run* {:init     (constantly {:value 0})
                            :cleanup  (fn [& _] (swap! cleanup-runs inc))
                            :on-error (partial reset! on-error-input)}
                           bogus-flow))
      (is (= "My exception" (-> @on-error-input first .failure .getMessage)))
      (is (= 1 @cleanup-runs))))

  (testing "flow with exception in which cleanup ignores error"
    (let [result (state-flow/run* {:init     (constantly {:value 0})
                                   :on-error state-flow/ignore-error}
                                  bogus-flow)]
      (is (e/exception? (first result)))
      (is (match? {:value 2} (second result)))))

  (testing "flow with exception in cleanup throws exception"
    ;; TODO:(dchelimsky,2020-03-02) consider whether we should catch this
    ;; instead of letting it bubble out.
    (is (thrown-with-msg? Exception #"Oops"
                          (state-flow/run* {:cleanup (fn [_] (throw (ex-info "Oops" {})))}
                                           (state/get)))))

  (testing "flow with exception in runner throws exception"
    ;; TODO:(dchelimsky,2020-03-02) consider whether we should catch this
    ;; instead of letting it bubble out.
    (is (thrown-with-msg? Exception #"Oops"
                          (state-flow/run*
                           {:runner (constantly (throw (ex-info "Oops" {})))}
                           (state/get))))))

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
    (let [line-number-before-flow-invocation (this-line-number)
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
    (let [line-number-before-flow-invocation (this-line-number)
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

(defn custom-runner [flow state]
  (state-flow/run flow state))

(deftest runner
  (testing "returns state-flow/run using state-flow/run"
    (is (identical? state-flow/run
                    (first (state-flow/run (state-flow/runner))))))
  (testing "defaults to state-flow/run using state-flow/run*"
    (is (identical? state-flow/run
                    (first (state-flow/run* {} (state-flow/runner))))))
  (testing "returns custom runner when providing custom runner to state-flow/run*"
    (is (identical? custom-runner
                    (first (state-flow/run* {:runner custom-runner}
                                            (state-flow/runner)))))))

(deftest stack-trace-exclusions
  (testing "default: uses default-stack-trace-exceptions (on all but first frame)"
    (let [frames (->> (state-flow/run*
                       {:on-error (state-flow/filter-stack-trace
                                   state-flow/default-stack-trace-exclusions)}
                       (state-flow/flow "" (state/invoke (/ 1 0))))
                      first
                      :failure
                      .getStackTrace)]
      (is (empty? (->> (rest frames)
                       (map #(.getClassName %))
                       (filter #(some (fn [ex] (re-find ex %))
                                      state-flow/default-stack-trace-exclusions)))))))

  (testing "preserves the first frame even if it matches exclusions"
    (let [frames (->> (state-flow/run*
                       {:on-error (state-flow/filter-stack-trace [#"clojure.lang"])}
                       (state-flow/flow "" (state/invoke (/ 1 0))))
                      first
                      :failure
                      .getStackTrace)]
      (is (= "clojure.lang.Numbers" (.getClassName (first frames))))
      (is (empty? (->> (rest frames)
                       (map #(.getClassName %))
                       (filter #(re-find #"^clojure.lang" %))))))))
