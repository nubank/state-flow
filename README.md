# StateFlow

[![Clojars Project](https://img.shields.io/clojars/v/nubank/state-flow.svg)](https://clojars.org/nubank/state-flow)

StateFlow is a testing framework designed to support the composition and reuse of individual test steps.

## Flows

A flow is a sequence of steps to be executed with some state as a
reference. Each step can be any of a primitive (described below), a
vector of bindings (described below), or a nested flow. Flows can be
`def`'d to vars, and nested arbitrarily deeply.

We define a flow with the `flow` macro:

```clojure
(flow <description> <flow/bindings/primitive>*)
```

Once defined, you can run it with `(state-flow.core/run! (flow ...) <initial-state>)`.

You can think flows and the steps within them as functions of the state, e.g.

```clojure
(fn [<state>] [<return-value>, <possibly-updated-state>])
```

Each step is executed in sequence, passing the state to the next step. The return value from running the flow is the return value of the last step that was run.

If you are using StateFlow for integration testing, the initial state is usually a representation of your service components,
a system using [Stuart Sierra's Component](https://github.com/stuartsierra/component) library or other similar facility. You can also run the same flow with different initial states, e.g.

```clojure
(def a-flow (flow ...))

(state-flow.core/run! flow <one-initial-state>)
(state-flow.core/run! flow <another-initial-state>)
```

### Primitives

Primitives are the fundamental building blocks of flows. Each one is
a function (wrapped in a Record in order to support internals, but you
can just think of them as functions) of state.

Below we list the main primitives and a model for the sort of function
each represents. Their names are derived from [Haskell's State
Monad](https://wiki.haskell.org/State_Monad), which you should read
about if you want to understand StateFlow's internals, but you should
not need in order to use StateFlow.

* Return current state

```clojure
(state-flow.state/get)
;=> (fn [s] [s s])
```

* Return the application of a function to the current state

```clojure
(state-flow.state/gets f)
;=> (fn [s] [(f s) s])
```

* Reset a new state

```clojure
(state-flow.state/put new-s)
;=> (fn [s] [s new-s])
```

* Update the state by applying a function

```clojure
(state-flow.state/modify f)
;=> (fn [s] [s (f s)])
```

* Return an arbitrary value

```clojure
(state-flow.state/return v)
;=> (fn [s] [v s])
```

### Bindings

Bindings let you take advantage of the return values of flows to compose other flows and have the following syntax:

`[(<symbol> <flow/primitive>)+]`

They work pretty much like `let` bindings but the left symbol binds to the _return value_ of the flow on the right.
It's also possible to bind directly to values (i.e. Clojure's `let`) within the same vector using the `:let` keyword:

```clojure
[(<symbol> <flow/primitive>)
 :let [<symbol> <non-flow expression>]]
 ```

### Flow Example

Suppose our system state is made out of a map with `{:value <value>}`. We can make a flow that just
fetches the value bound to `:value`.

```clojure
(def get-value (flow "get-value" (state/gets :value)))
(state-flow/run! get-value {:value 4})
; => [4 {:value 4}]
```

Primitives have the same underlying structure as flows and can be passed directly to `run!`:

```clojure
(def get-value (state/gets :value))
(state-flow/run! get-value {:value 4})
; => [4 {:value 4}]
```

We can use `state/modify` to modify the state. Here's a primitive that increments the value:

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

We use the `defflow` and `match?` macros to build `clojure.test` tests
out of flows.

`defflow` defines a test (using `deftest`) that when
run, will execute the flow with the parameters that we set.

`match?` is a flow that will make a `clojure.test` assertion and the [`nubank/matcher-combinators`](https://github.com/nubank/matcher-combinators/) library
for the actual checking and failure messages. `match?` asks for a string description, a value (or a flow returning a value) and a matcher-combinators matcher (or value to be checked against). Not passing a matcher defaults to `matchers/embeds` behaviour.

Here are some very simple examples of tests defined using `defflow`:

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

We use `verify` to write midje tests with StateFlow. `verify` is a function that of three arguments: a description, a value or step, and another value or midje checker. It
produces a step that, when executed, verifies that the second argument matches the third argument. It replicates the functionality of a `fact` from midje.
In fact, if a simple value is passed as second argument, what it does is simply call `fact` internally when the flow is executed.

`verify` returns a step that will make the check and return something. If the second argument is a value, it will return this argument. If the second argument is itself a step, it will return the last return value of the step that was passed. This makes it possible to use the result of verify on a later part of the flow execution if that is desired.

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
