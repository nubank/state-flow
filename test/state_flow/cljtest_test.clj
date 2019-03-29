(ns state-flow.cljtest-test
  (:require [cats.core :as m]
            [cats.data :as d]
            [cats.monad.state :as state]
            [clojure.test :as ctest]
            [matcher-combinators.matchers :as matchers]
            [midje.sweet :refer :all]
            [state-flow.cljtest :as cljtest :refer [defflow]]
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

(facts "defflow"
  (fact "defines flow with default parameters"
    (macroexpand-1 '(defflow my-flow (cljtest/match? 1 1)))
    => '(clojure.test/deftest
          my-flow
          (state-flow.core/run*
           {}
           (state-flow.core/flow (clojure.core/str my-flow) (cljtest/match? 1 1)))))
  (fact "defines flow with optional parameters"
    (macroexpand-1 '(defflow my-flow {:init (constantly {:value 1})} (cljtest/match? 1 1)))
      => '(clojure.test/deftest
            my-flow
            (state-flow.core/run*
             {:init (constantly {:value 1})}
             (state-flow.core/flow (clojure.core/str my-flow) (cljtest/match? 1 1))))))
