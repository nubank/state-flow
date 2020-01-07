# StateFlow

[![Clojars Project](https://img.shields.io/clojars/v/nubank/state-flow.svg)](https://clojars.org/nubank/state-flow)

An integration testing framework for building and composing test flows with support for clojure.test and midje

StateFlow provides a compositional approach to implementing integration tests. The goal is to reduce coupling between test steps in order to support reuse and composition of flows.

## The flow macro

Defining a flow is done with the `flow` macro, which expects a description and a variable number of steps that can be other flows, bindings or primitives.

Flow macro syntax:
```clojure
(flow <description> <flow/bindings/primitive>*)
```

The flow macro defines a sequence of steps to be executed having some state as a reference.
It can be thought about as a function mapping some state to a pair of `(fn [<state>] [<return-value>, <possibly-updated-state>])`

Once defined, you can run it with `(state-flow.core/run! (flow ...) <initial-state>)`.
Each step will be executed in sequence, passing the state to the next step and the result will be a pair `[<return-value>, <final-state>]`.
The return value of running the flow is the return value of the last step that was run.

If you are using the library for integration testing, the initial state is usually a representation of your service components,
a system using [Stuart Sierra's Component](https://github.com/stuartsierra/component) library or other similar facility. You can also run the same flow with different initial states without any problem.

### Primitives

Primitives are the fundamental building blocks of flows and are
enough to build any kind of flow. Each one returns a function of the
state. These functions are wrapped in Records in order to support
Protocols, but you can just think of them as functions.

Below we list the main primitives and a model for the sort of function
each represents. The names of the primatives are derived from
https://wiki.haskell.org/State_Monad, which you should read if you
want to understand how StateFlow works, but you should not need to
read in order to use StateFlow.

* Returning current state

```clojure
state-flow.state/get
;=> (fn [s] [s s])
```

* Returning the application of a function on the current state

```clojure
(state-flow.state/gets f)
;=> (fn [s] [(f s) s])
```

* Resetting a new state

```clojure
(state-flow.state/put new-s)
;=> (fn [s] [s new-s])
```

* Updating the state by applying a function

```clojure
(state-flow.state/modify f)
;=> (fn [s] [s (f s)])
```
* Returning an arbitrary value

```clojure
(state-flow.state/return v)
;=> (fn [s] [v s])
```

### Bindings

Bindings take advantage of the return values of flows to compose other flows and have the following syntax:

`[(<symbol> <flow/primitive>)+]`

They work pretty much like `let` bindings but the left symbol binds to the _return value_ of the flow on the right.

### Flow Example

Suppose our system state is made out of a simple map with `{:value <value>}`. We can make a flow that just
fetches the value bound to `:value`.

```clojure
(def get-value (state/gets :value))
(state-flow/run! get-value {:value 4})
; => [4 {:value 4}]
```

We can use `state/modify` to modify the state. Here's a flow that increments the value:

```clojure
(def inc-value (state/modify #(update % :value inc)))
(state-flow/run! inc-value {:value 4})
; => [{:value 4} {:value 5}]
```

Bindings enable us to compose simple flows into more complex flows.
If, instead of returning the value, we wanted to return the value
multiplied by two, we could do it like this:

```clojure
(def double-value
  (flow "get double value"
    [value get-value]
    (state/return (* value 2))))
(state-flow/run! double-value {:value 4})
; => [8 {:value 4}]
```

Or we could increment the value first and then return it doubled:

```clojure
(def inc-and-double-value
  (flow "increment and double value"
    inc-value
    [value get-value]
    (state/return (* value 2))))
(state-flow/run! inc-and-double-value {:value 4})
; => [10 {:value 5}]
```

## Clojure.test Support

The way we can use flows to make `clojure.test` tests is by using `match?`.
`match?` is a flow that will make a `clojure.test` assertion and the [`nubank/matcher-combinators`](https://github.com/nubank/matcher-combinators/) library
for the actual checking and failure messages. `match?` asks for a string description, a value (or a flow returning a value) and a matcher-combinators matcher (or value to be checked against). Not passing a matcher defaults to `matchers/embeds` behaviour.

The assertions should be wrapped in a `defflow`. `defflow` will define a test (using `deftest`)
that when run, will execute the flow with the parameters that we set. Here are some very simple examples
of tests defined using `defflow`:

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

## Midje Support

The way to write midje tests with StateFlow is by using `verify`.
`verify` is a function that takes three arguments: a description, a value or step and another value or midje checker
and produces a step that when executed, verifies that the second argument matches the third argument. It replicates the functionality of a `fact` from midje.
In fact, if a simple value is passed as second argument, what it does is simply call `fact` internally when the flow is executed.

Verify returns a step that will make the check and return something. If the second argument is a value, it will return this argument. If the second argument is itself a step, it will return the last return value of the step that was passed. This makes it possible to use the result of verify on a later part of the flow execution if that is desired.

Say we have a step for making a POST request that stores data in datomic (`store-data-request`),
and we also have a step that fetches this data from db (`fetch-data`). We want to check that after we make the POST, the data is persisted:

```clojure
(:require
  [state-flow.core :refer [flow]]
  [state-flow.midje :refer [verify]])

(defn stores-data-in-db
  [data]
  (flow "save data"
    (store-data-request data)
    [saved-data (fetch-data)]
    (verify "data is stored in db"
      saved-data
      expected-data)))
```
