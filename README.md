# StateFlow

[![Clojars Project](https://img.shields.io/clojars/v/nubank/state-flow.svg)](https://clojars.org/nubank/state-flow)

An integration testing framework for building and composing test flows with support for clojure.test and midje

The StateFlow library aim to provide a compositional way in which one could implement integration tests. The main advantage of this approach is to reduce coupling between test steps and allow for more reusability and composability of flows.

## The flow macro

Defining a flow is done by using the `flow` macro, which expects a description as first parameter and a variable number of steps that can be other flows, bindings or primitives.

Flow macro syntax:
```clojure
(flow <description> <flow/bindings/primitive>*)
```

The flow macro defines a sequence of steps to be executed having some state as a reference.
It can be thought about as a function mapping some state to a pair of values; a return value and a possibly changed state: `(fn [s] [ret s'])`

Once defined, you can run it with `(state-flow.core/run! (flow ...) <initial-state>)` and it will return a pair `[<return> <final-state>]`

### Primitives

The primitives are the fundamental building blocks of a flow.

#### gets

```clojure
(state/gets get-fn) => (fn [s] [(get-fn s) s])
```

```clojure
(def get-value (state/gets :value))
```
#### 


There are many ways you can think about primitives, but with those three you 

This is what a flow that saves something in the database, queries the database to get the entity back
and then saves an updated version to the database would look like:

```clojure
(ns example
 (:require [state-flow.core :as state-flow]))

(def my-flow
  (state-flow/flow "testing some stuff"
    (save-entity)
    [entity (fetch-entity)
     :let   [transformed-entity (transform entity)]]
    (update-entity transformed-entity)))
```

Flow definition and flow execution happen in different stages. To run a flow you can do:

```clojure
(state-flow/run! my-flow initial-state)
```

The initial state is usually a representation of your service components, a system using [Stuart Sierra's Component](https://github.com/stuartsierra/component) library or other similar facility. You can also run the same flow with different initial states without any problem.

## Flow steps

A step is essentially a `State` record containing a function from a state to a pair `[<return-value>, <possibly-updated-state>]`. Think about it as a state transition function ~on steroids~ that has a return value).

One of the main advantages of using this approach for building the state transition steps is to take advantage of the return value to compose new steps from simpler steps. Another advantage is that since the State record implements `Monad` and other protocols from [cats library](https://github.com/funcool/cats), we can use its utilities and macros that make creating and composing flow steps a lot easier.

* Returning current state

```clojure
(state-flow.state/get)
;=> (State. (fn [s] [s s]))
```

* Returning a function application on the current state

```clojure
(state-flow.state/gets f)
;=> (State. (fn [s] [(f s) s]))
```

* Inserting a new state

```clojure
(state-flow.state/put new-s)
;=> (State. (fn [s] [s new-s]))
```

* Changing the state by applying a function transforming it

```clojure
(state-flow.state/swap f)
;=> (State. (fn [s] [s (f s)]))
```

* Wraps a function into a state monad (useful for delaying side-effects that should happen only when a flow is running)

```clojure
(state-flow.state/wrap-fn f)
;=> (State. (fn [s] [(f) s]))
```
### clojure.test

* `match?` macro now expands to a clojure.test `testing` and uses `meta` variables from the position it has been written, therefore giving correct line number when a test fails.
* Optional parameters for setting initial-state, cleanup and flow runner

Usage will look something like this:

```clojure
(defflow my-flow
  (match? "simple test" 1 1)
  (match? "embeds" {:a 1 :b 2} {:a 1}))
```
Or with custom parameters:

```clojure
(defflow my-flow {:init aux.init! :runner (comp run! s/with-fn-validation)}
  (match? "simple test" 1 1)
  (match? "simple test 2" 2 2))
```

```clojure
(defflow my-flow {:init (constantly {:value 1
                                     :map {:a 1 :b 2}})}
  [value (state/gets :value)]
  (match? "value is correct" value 1)
  (match? "embeds" (state/gets :map) {:b 2}))
```

example flow migration: https://github.com/nubank/arnaldo/pull/169/files
Testing with `match?` uses `clojure.test` and `matcher-combinators` library as a backend internally. The syntax is similar to midje's, though: `match?` asks for a string description, a value (or step returning a value) and a matcher-combinators matcher (or value to be checked against). Not passing a matcher defaults to `matchers/embeds` behaviour.

Usage:
```clojure
(:require [state-flow.core :refer [flow match?]]
              [matcher-combinators.matchers :as matchers]])

(flow "my flow"

  ;;embeds
  (match? "my first test" {:a 2 :b 3 :c 4} {:a 2 :b 3})

  ;;exact match
  (match? "my second test" {:a 2 :b 3 :c 4} (matchers/equals {:a 2 :b 3 :c 4})

 ;; in any order
 (match? "my third test" [1 2 3] (matchers/in-any-order [1 3 2]))

  ;; with flow
 (match? "my fourth test"
   (kafka/last-message :my-topic)
   {:payload "payload"}))
```

In the backend, the first test will define the following test, for instance:

```clojure
(deftest my-flow->my-first-test
  (is (match? {:a 2 :b 3} {:a 2 :b 3 :c 4})))
```

### Midje

`verify` is a function that takes three arguments: a description, a value or step and another value or midje checker
and produces a step that when executed, verifies that the second argument matches the third argument. It replicates the functionality of a `fact` from midje.
In fact, if a simple value is passed as second argument, what it does is simply call `fact` internally when the flow is executed.

Verify returns a step that will make the check and return something. If the second argument is a value, it will return this argument. If the second argument is itself a step, it will return the last return value of the step that was passed. This makes it possible to use the result of verify on a later part of the flow execution if that is desired.

Say we have a step for making a POST request that stores data in datomic (`store-data-request`),
and we also have a step that fetches this data from db (`fetch-data`). We want to check that after we make the POST, the data is persisted:

```clojure
(:require
  [state-flow.core :refer [flow verify]])

(defn stores-data-in-db
  [data]
  (flow "save data"
    (store-data-request data)
    [saved-data (fetch-data)]
    (verify "data is stored in db"
      saved-data
      expected-data)))
```

