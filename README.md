# StateFlow

[![Clojars Project](https://img.shields.io/clojars/v/nubank/state-flow.svg)](https://clojars.org/nubank/state-flow)

StateFlow is a testing framework designed to support the composition and reuse of individual test steps.

## Learning Materials
- [Tutorial](./samples/tutorial.clj)
- [Walkthrough](./doc/walkthrough.repl)

## Definitions

* A [*flow*](#flows) is a sequence of steps or bindings.
* A [*step*](#primitive-steps) is a primitive step or flow.
* A [*binding*](#bindings) is a vector of pairs of symbols and steps (or a :let with a vector of regular let-bindings)

## Flows

A flow is a sequence of steps or bindings to be executed with some state as a
reference. Use the `flow` macro to define a flow:

```clojure
(flow <description> <step/bindings>*)
```

Once defined, you can run it with `(state-flow.api/run* <options> (flow ...))`.

You can think flows and the steps within them as functions of the state, e.g.

```clojure
(fn [<state>] [<return-value>, <possibly-updated-state>])
```

Each step is executed in sequence, passing the state to the next step. The return value from running the flow is the return value of the last step that was run.

### Primitive steps

Primitive steps are the fundamental building blocks of flows.

* Return the application of a function f to the state.

```clojure
(state-flow.api/get-state f)
```

* Store the application of a function f to the state.

```clojure
(state-flow.api/swap-state f)
```

* Transform a value returned by a step

``` clojure
(state-flow.api/fmap xform <step-or-flow>)
```

* Return a value

```clojure
(state-flow.api/return v)
```

* Invoke a no-arg function and return its result

``` clojure
(state-flow.api/invoke no-arg-fn)
```

### Bindings

Bindings bind return values of steps to symbols you can use in other steps.

`[(<symbol> <step>)+]`

They are like `let` bindings but the symbol on the left binds to the _return value_ of the step on the right.

```clojure
[<symbol> <step-or-flow>]
 ```

You can also bind directly to values using the `:let` keyword:

```clojure
[:let [<symbol> <non-step expression>]]
 ```

You can bind any number of symbols in a single binding vector, e.g.

```clojure
[a     step-1
 b     step-2
 :let [c expression-1]
 d     step-3]
 ```

### Running Flows

If you are using StateFlow for integration testing, the initial state is usually a representation of your service components,
a system using [Stuart Sierra's Component](https://github.com/stuartsierra/component) library or other similar facility. You can also run the same flow with different initial states, e.g.

```clojure
(def a-flow (flow ...))

(defn build-initial-state [] { ... })
(state-flow.api/run* {:init build-initial-state} flow)

(state-flow.api/run* {:init (constantly {:service-system (atom nil))} flow)
```

### Composing Flows

Flows follow the Composite Pattern: a single flow has the same
interface as a collection of flows.

You can compose flows by nesting them in other flows:

``` clojure
(flow "do many things"
  (flow "do one thing" ,,,)
  (flow "do another thing" ,,,))
```

Use `state-flow.api/for` when you have a flow that you'd like to apply
to different inputs with the same outcome, e.g.

``` clojure
(flow "even? returns true for even numbers"
  (flow/for [x (filter even? (range 10))]
    (match? even? x)))
```

#### Failing Fast

By default, a flow continues to be evaluated even if an assertion fails. The `:fail-fast?` option to `state-flow.api/run*` can be used if you would like to stop evaluation after the first assertion failure.

```clojure
(state-flow.api/run* {:fail-fast? true}
  (flow "evaluation stops after `failing-flow-b`"
    flow-a
    failing-flow-b
    flow-c))
```

### Flow Example

Suppose our system state is made out of a map with `{:value <value>}`. We can make a flow that just
fetches the value bound to `:value`.

```clojure
(require '[state-flow.api :as flow :refer [flow]])
(def get-value (flow "get-value" (flow/get-state :value)))
(flow/run* {:init (constantly {:value 4})} get-value)
; => [4 {:value 4}]
```

Primitive steps have the same underlying structure as flows and can be passed directly to `run*`:

```clojure
(def get-value (flow/get-state :value))
(flow/run* {:init (constantly {:value 4})} get-value)
; => [4 {:value 4}]
```

We can use `state-flow.api/swap-state` to modify the state. Here's a primitive that increments the value:

```clojure
(def inc-value (flow/swap-state update :value inc))
(flow/run* {:init (constantly {:value 4})} inc-value)
; => [{:value 4} {:value 5}]
```

Bindings enable us to compose simple flows into more complex flows.
If, instead of returning the value, we wanted to return the value
multiplied by two, we could do it like this:

```clojure
(def double-value
  (flow "get double value"
    [value get-value]
    (flow/return (* value 2))))
(flow/run* {:init (constantly {:value 4})} double-value)
; => [8 {:value 4}]
```

Or we could increment the value first and then return it doubled:

```clojure
(def inc-and-double-value
  (flow "increment and double value"
    inc-value
    [value get-value]
    (flow/return (* value 2))))
(flow/run* {:init (constantly {:value 4})} inc-and-double-value)
; => [10 {:value 5}]
```

## clojure.test and matcher-combinators

We use the `defflow` and `match?` macros to build `clojure.test` tests
out of flows.

`state-flow.api/defflow` defines a test (using `deftest`) that
will execute the flow with the parameters that we set.

`state-flow.assertions.matcher-combinators/match?` produces a flow that will make an assertion, which
will be reported via clojure.test when used within a `defflow`. It
uses the
[`nubank/matcher-combinators`](https://github.com/nubank/matcher-combinators/)
library for the actual check and failure messages. `match?` asks for:

* the expected value, or a matcher-combinators matcher
  * if you supply a value, matcher-combintators will apply its defaults
* the actual value, or a step which will produce it
  * if you supply a value, `match?` will wrap it in `(state-flow.api/return <value>)`
* optional map of options with:
  * `:times-to-try` (default 1)
  * `:sleep-time`   (default 200)

Here are some very simple examples of tests defined using `defflow`:

```clojure
(defflow my-flow
  (match? 1 1)
  (match? {:a 1} {:a 1 :b 2}))
```

Wrap them in `flow`s to get descriptions when the expected and actual
values need some explanation:

```clojure
(deftest fruits-and-veggies
  (flow "surprise! Tomatoes are fruits!"
    (match? #{:tomato} (fruits #{:tomato :potato}))))
```

Or with custom parameters:

```clojure
(defflow my-flow {:init aux.init! :runner (comp run* s/with-fn-validation)}
  (match? 1 1))

```

```clojure
(defflow my-flow {:init (constantly {:value 1
                                     :map {:a 1 :b 2}})}
  [value (flow/get-state :value)]
  (match? 1 value)
  (flow "uses matcher-combinator embeds"
    (match? {:b 2} (flow/get-state :map)))
```

### `:times-to-try` and `:sleep-time`

By default, `match?` will evaluate `actual` only once. For tests with
asynchrony/concurrency concerns, you can direct `match?` to try up to
`:times-to-try` times, waiting `:sleep-time` between each try. It will
keep trying until it produces a value that matches the `expected`
expression, up to `:times-to-try`.

``` clojure
(defflow add-data
  (flow "try up to 5 times with 250 ms between each try (total 1000ms)"
    (produce-message-that-causes-database-update)
    (match? expected-data-in-database
            (fetch-data)
            {:times-to-try 5
             :sleep-time 250})))
```

### NOTE: about upgrading to state-flow-2.2.4

We introduced `state-flow.api/match?` in state-flow-2.2.4, and
deprecated `state-flow.cljtest/match?` in that release. The signature
for the old version was `(match? <description> <actual> <expected>)`.
We removed the description because it was quite common for the description
to add no context that wasn't already made clear by the expected and
actual values.

We also reversed the order of expected and actual in order to align
with the `match?` function in the matcher-combinators library and with
clojure.test's `(is (= expected actual))`.

We also added a script to help refactor this for you. Here's how
you use it:

``` shell
# if you don't already have the state-flow repo cloned
git clone https://github.com/nubank/state-flow.git
;; or
git clone git@github.com:nubank/state-flow.git
;; then
cd state-flow

# if you already have the state-flow repo cloned
cd state-flow
git co master
git pull

# the rest is the same either way
lein pom # needed for tools.deps to recognize this repo as a `:local/root` dependency
./bin/refactor-match.sh --help
;; now follow the instructions
```

Note that if you have a `defflow` defined in a different namespace, and it depends on `state-flow.api/defflow`, you may need to require it in that namespace.

## Midje Support

We use `verify` to write midje tests with StateFlow. `verify` is a function that of three arguments: a description, a value or step, and another value or midje checker. It
produces a step that, when executed, verifies that the second argument matches the third argument. It replicates the functionality of a `fact` from midje.
In fact, if a simple value is passed as second argument, what it does is simply call `fact` internally when the flow is executed.

`verify` returns a step that will make the check and return something. If the second argument is a value, it will return this argument. If the second argument is itself a step, it will return the last return value of the step that was passed. This makes it possible to use the result of verify on a later part of the flow execution if that is desired.

Say we have a step for making a POST request that stores data in datomic (`store-data-request`),
and we also have a step that fetches this data from db (`fetch-data`). We want to check that after we make the POST, the data is persisted:

```clojure
(:require
  [state-flow.api :refer [flow]]
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

## Writing Helpers

Test helpers specific to your domain can make state-flow tests more
readable and intention-revealing. When writing them, we recommend that
you start with state-flow functions in the `state-flow.api` namespace.
If, for example, you're testing a webapp, you might want a `request`
helper like this:

``` clojure
(defflow users
  (flow "fetch registered users"
    (http-helpers/request {:method :post
                           :uri "/users"
                           :body {:user/first-name "David"}})
    [users (http-helpers/request {:method :get
                                  :uri "/users"})]
    (match? ["David"]
            (map :user/first-name users)))
```

Presuming that you have an `:http-component` key in the initial state,
the `http-helpers/request` helper could be implemented something like this:

``` clojure
(ns http-helpers
  (:require [my-app.http :as http]
            [state-flow.api :as flow :refer [flow]]))

(defn request [req]
  (flow "make request"
    [http (flow/get-state :http-component)]
    (flow/return (http/request http req)))
```

This produces a step that can be used in a flow, as above.

### funcool.cats

`state-flow` is built on the `funcool.cats` library, which supports
monads in Clojure. `state-flow` exposes some, but not all, `cats`
functions as its own API. As mentioned above, we recommend that you
stick with `state-flow` functions as much as possible, however, if the
available functions do not suit your need for a helper, you can always
drop down to functions directly in the `cats` library.

## Tooling

### Emacs + cider

Add `"defflow"` to the list defined by `cider-test-defining-forms` to
enable commands like `cider-test-run-test` for flows defined with `defflow`.

See [https://docs.cider.mx/cider/testing/running_tests.html#_configuration](https://docs.cider.mx/cider/testing/running_tests.html#_configuration)
