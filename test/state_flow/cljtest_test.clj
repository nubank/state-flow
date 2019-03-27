(ns state-flow.cljtest-test
  (:require [cats.core :as m]
            [cats.data :as d]
            [cats.monad.state :as state]
            [clojure.test :as ctest]
            [matcher-combinators.matchers :as matchers]
            [matcher-combinators.midje :refer [match]]
            [midje.sweet :refer :all]
            [state-flow.cljtest :as cljtest :refer [deftest]]
            [state-flow.core :as state-flow :refer [flow]]
            [state-flow.state :as sf.state]))

(def increment-two
  (m/mlet [world (sf.state/get)]
    (m/return (+ 2 (-> world :value)))))

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
                  :meta {:description []}})))

  (fact "works with matcher combinators equals"
    (let [val {:value {:a 2 :b 5}}]
      (state-flow/run (cljtest/match? "contains with monadic left value" (state/gets :value) (matchers/equals {:a 2 :b 5})) val)
      => (d/pair {:a 2 :b 5}
                 {:value {:a 2 :b 5}
                  :meta {:description []}})))

  (fact "works for failure cases"
    (let [val {:value {:a 2 :b 5}}]
      (state-flow/run (cljtest/match? "contains with monadic left value" (state/gets :value) (matchers/equals {:a 1 :b 5})) val)
      => (d/pair {:a 2 :b 5}
                 {:value {:a 2 :b 5}
                  :meta {:description []}})))

  (fact "works with matcher combinators in any order"
    (let [val {:value [1 2 3]}]
      (state-flow/run (cljtest/match? "contains with monadic left value" (state/gets :value) (matchers/in-any-order [1 3 2])) val)
      => (d/pair [1 2 3]
                 {:value [1 2 3]
                  :meta {:description []}}))))

(def initial-test-state {::initialized-state true})

(defn initialize-tests-state [] initial-test-state)

(deftest test-with-success {}
  (flow "Flow declaration here"
    (cljtest/match? "a" 1 1)
    (state/swap #(assoc % :yo 1))))

(deftest test-with-runtime-success
  {:initializer-fn (constantly {:value 1})}
  (flow "Flow declaration here"
    (cljtest/match? "a" increment-two 3)
    (state/swap #(assoc % :yo 1))))


(deftest test-with-failure {}
  (flow "Flow declaration here"
    (cljtest/match? "a" 1 2)
    (state/swap #(assoc % :yo 1))))

(facts state-flow/deftest
  (fact "contains proper metadata"
    (meta #'test-with-success)
    => (match {:column              number?
               :file                string?
               :line                number?
               :name                'test-with-success
               ::cljtest/test       true
               ::cljtest/initialize fn?
               ::cljtest/cleanup    fn?})

    (meta #'test-with-failure)
    => (match {:column              number?
               :file                string?
               :line                number?
               :name                'test-with-failure
               ::cljtest/test       true
               ::cljtest/initialize fn?
               ::cljtest/cleanup    fn?}))

  (fact "state functions are properly populated"
    ((::cljtest/initialize (meta #'test-with-success)))
    => {}

    ((::cljtest/cleanup (meta #'test-with-success)) {})
    => {}))

(facts state-flow/ns->tests
  (fact "only lists flows vars defined in namespace"
    (cljtest/ns->tests 'state-flow.cljtest-test)
    => [#'test-with-success
        #'test-with-runtime-success
        #'test-with-failure]))

(facts state-flow/run-test
  (fact "returns result and runs state functions"
    (second (test-with-success {}))
    => {:meta {:description []} :yo 1})

  (fact "returns result and runs state functions with failure"
    (second (test-with-failure {}))
    => {:meta {:description []} :yo 1})

  (fact "returns result and runs state functions (at runtime)"
    (second (test-with-runtime-success {:value 1}))
    => {:meta {:description []} :yo 1 :value 1}))
