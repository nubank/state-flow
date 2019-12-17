(ns state-flow.cljtest-test
  (:require [cats.core :as m]
            [cats.data :as d]
            [cats.monad.state :as state]
            [clojure.test :as ctest :refer [is]]
            [matcher-combinators.matchers :as matchers]
            [matcher-combinators.test]
            [midje.sweet :refer :all]
            [state-flow.cljtest :as cljtest :refer [defflow]]
            [state-flow.core :as state-flow :refer [flow]]
            [state-flow.state :as sf.state]))

(def increment-two
  (m/mlet [world (sf.state/get)]
    (m/return (+ 2 (-> world :value)))))

(def get-value (comp deref :value))
(def get-value-state (state/gets get-value))

(defn delayed-increment-two
  [delay-ms]
  "Changes world in the future"
  (state/state (fn [world]
                 (future (do (Thread/sleep delay-ms)
                             (swap! (:value world) + 2)))
                 (d/pair nil world))))

(facts "on match?"

  (fact "add two to state 1, result is 3, doesn't change world"
    (state-flow/run (cljtest/match? "test-1" increment-two 3) {:value 1}) => (d/pair 3 {:value 1 :meta {:description []}}))

  (fact "works with non-state values"
    (state-flow/run (cljtest/match? "test-2" 3 3) {}) => (d/pair 3 {:meta {:description []}}))

  (fact "works with matcher combinators (embeds by default)"
    (let [val {:value {:a 2 :b 5}}]
      (state-flow/run (cljtest/match? "contains with monadic left value" (state/gets :value) {:a 2}) val)
      => (d/pair {:a 2 :b 5}
                 {:value {:a 2 :b 5}
                  :meta  {:description []}})))

  (fact "works with matcher combinators equals"
    (let [val {:value {:a 2 :b 5}}]
      (state-flow/run (cljtest/match? "contains with monadic left value" (state/gets :value) (matchers/equals {:a 2 :b 5})) val)
      => (d/pair {:a 2 :b 5}
                 {:value {:a 2 :b 5}
                  :meta  {:description []}})))

  (fact "works for failure cases"
    (let [val {:value {:a 2 :b 5}}]
      (state-flow/run (cljtest/match? "contains with monadic left value" (state/gets :value) (matchers/equals {:a 1 :b 5})) val)
      => (d/pair {:a 2 :b 5}
                 {:value {:a 2 :b 5}
                  :meta  {:description []}})))

  (fact "add two with small delay"
    (let [world {:value (atom 0)}]
      (state-flow/run (delayed-increment-two 100) world) => (d/pair nil world)
      (first (state-flow/run (cljtest/match? "" get-value-state 2) world)) => 2))

  (fact "we can tweak timeout and times to try"
    (let [world {:value (atom 0)}]
      (state-flow/run (delayed-increment-two 100) world) => (d/pair nil world)
      (first (state-flow/run (cljtest/match? "" get-value-state 2 {:sleep-time   0
                                                                   :times-to-try 1}) world)) => 0))

  (fact "add two with too much delay (timeout)"
    (let [world {:value (atom 0)}]
      (state-flow/run (delayed-increment-two 4000) world) => (d/pair nil world)
      (first (state-flow/run (cljtest/match? "" get-value-state 2) world)) => 0))

  (fact "works with matcher combinators in any order"
    (let [val {:value [1 2 3]}]
      (state-flow/run (cljtest/match? "contains with monadic left value" (state/gets :value) (matchers/in-any-order [1 3 2])) val)
      => (d/pair [1 2 3]
                 {:value [1 2 3]
                  :meta  {:description []}}))))

(facts "on `testing`"
       (fact "works for failure cases"
             (let [val {:value {:a 2 :b 5}}]
               (state-flow/run (state-flow/flow "desc"
                                 [v (state/gets :value)]
                                 (cljtest/testing "contains with monadic left value"
                                   (is (= {:a 1 :b 5} v))))
                 val)
               => (d/pair false
                          {:value {:a 2 :b 5}
                           :meta  {:description []}}))))

(facts "defflow"
  (fact "defines flow with default parameters"
    (macroexpand-1 '(defflow my-flow (cljtest/match? "equals" 1 1)))
    => '(clojure.test/deftest
          my-flow
          (state-flow.core/run*
           {}
           (state-flow.core/flow "my-flow" (cljtest/match? "equals" 1 1)))))

  (fact "defines flow with optional parameters"
    (macroexpand-1 '(defflow my-flow {:init (constantly {:value 1})} (cljtest/match? "equals" 1 1)))
    => '(clojure.test/deftest
          my-flow
          (state-flow.core/run*
           {:init (constantly {:value 1})}
           (state-flow.core/flow "my-flow" (cljtest/match? "equals" 1 1)))))

  (fact "defines flow with binding and flow inside match?"
    (macroexpand-1 '(defflow my-flow {:init (constantly {:value 1
                                                         :map {:a 1 :b 2}})}
                      [value (state/gets :value)]
                      (cljtest/match? value 1)
                      (cljtest/match? (state/gets :map) {:b 2})))
    => '(clojure.test/deftest
          my-flow
          (state-flow.core/run*
           {:init (constantly {:map {:a 1 :b 2} :value 1})}
           (state-flow.core/flow
            "my-flow"
             [value (state/gets :value)]
             (cljtest/match? value 1)
             (cljtest/match? (state/gets :map) {:b 2}))))))

(defflow my-flow {:init (constantly {:value 1
                                     :map {:a 1 :b 2}})}
  [value (state/gets :value)]
  (cljtest/match? "" value 1)
  (cljtest/match? "" (state/gets :map) {:b 2}))

(facts "we can run a defined test"
  (second ((:test (meta #'my-flow)))) => {:value 1
                                          :map   {:a 1 :b 2}
                                          :meta  {:description []}})
