(ns state-flow.helpers.core-test
  (:require [state-flow.helpers.core :as helpers]
            [cats.data :as d]
            [midje.sweet :refer :all]
            [nu.monads.state :as state]
            [matcher-combinators.midje :refer [match]]
            [com.stuartsierra.component :as component]))

(facts "on with-resources"
  (fact "fetches value and increments 1"
    (state/run (helpers/with-resource :value #(+ 1 %)) {:value 3}) => (d/pair 4 {:value 3}))
  (fact "first argument not callable, assertion error"
    (helpers/with-resource 1 #(+ 1 %)) => (throws AssertionError))
  (fact "second argument not callable, assertion error"
    (helpers/with-resource :value 1) => (throws AssertionError)))

(defrecord Http [responses]
  component/Lifecycle
  (start [this] this)
  (stop [this] this))

(defn new-http
  [responses]
  (map->Http {:responses responses}))

(def system-map
  (component/system-map :comp1 (component/using (new-http {:url 1}) [])
                        :comp2 (component/using (new-http {:url 2}) [:comp1])))

(facts "on system-swap"
  (let [swap-fn (fn [s] (assoc-in s [:system :comp1 :responses] {:url 3}))
        swap-fn-2 (fn [s] (assoc-in s [:system :comp2 :responses] {:url 4}))
        system (component/start system-map)]
    (fact "we change system and reinject dependencies"

      (state/exec (helpers/system-swap (state/swap swap-fn)) {:system system-map})
      => (match {:system {:comp1 {:responses {:url 3}}
                          :comp2 {:responses {:url 2}
                                  :comp1 {:responses {:url 3}}}}}))
    (fact "no change"
      (state/exec (helpers/system-swap (state/swap identity)) {:system system-map})
      => (match {:system system}))
    (fact "replace comp2"
      (state/exec (helpers/system-swap (state/swap swap-fn-2)) {:system system-map})
      => (match {:system {:comp1 {:responses {:url 1}}
                          :comp2 {:responses {:url 4}
                                  :comp1 {:responses {:url 1}}}}}))
    (fact "replace both"
      (state/exec (helpers/system-swap (state/swap (comp swap-fn-2 swap-fn))) {:system system-map})
      => (match {:system {:comp1 {:responses {:url 3}}
                          :comp2 {:responses {:url 4}
                                  :comp1 {:responses {:url 3}}}}}))))
