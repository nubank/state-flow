(ns state-flow.labs.cljtest-test
  (:require [midje.sweet :refer [facts fact =>]]
            [clojure.test :as ctest :refer [is]]
            [matcher-combinators.midje :refer [match]]
            [state-flow.labs.cljtest :as labs.cljtest]
            [cats.data :as d]
            [state-flow.core :as state-flow]
            [cats.monad.state :as state]))

(facts "on `testing`"
  (fact "works for failure cases"
        (let [val {:value {:a 2 :b 5}}
              [ret state]
              (state-flow/run (state-flow/flow "desc"
                                [v (state/gets :value)]
                                (labs.cljtest/testing "contains with monadic left value"
                                  (is (= {:a 1 :b 5} v))))
                val)]
          ret => false
          state => (match {:value {:a 2 :b 5}}))))
