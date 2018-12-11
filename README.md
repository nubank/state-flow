# StateFlow

A redesign of Postman using a state monad in the backend for building and composing flows.

The StateFlow library aim to provide an alternative, more compositional way in which one could implement postman-like integration tests. The main advantage of this approach is to reduce coupling between test steps and allow for more reusability and composability of flows.

For an in-depth explanation of the internals, check https://wiki.nubank.com.br/index.php/Busquem_Conhecimento_-_tech_talks#Monadic_Postman (it might be a bit outdated).

**Do I need to understand monads?**
No. But if you want to, you can read https://github.com/nubank/nu-algebraic-data-types/blob/master/README.md, which also points to some other interesting references.

## Using flow

`flow` expects a description a first parameter and a variable number of steps as remaining parameters. The return value of a call to `flow` is also a step, so it is possible to use flows within flows (within flows within flows...).

A step is just a state monad, which is a record containing a function from a state (a map containing the components) to a pair `[<return-value>, <updated-state>]`. Think about it as a state transition function ~on steroids~ that has a return value).

One of the main advantages of using a state monad for building the state transition steps is to take advantage of the return value to avoid using the world state as a global mutable cache of intermediate results. Another advantage is to provide us with great tools to write and compose general flows into more complicated flows.

95% of the time there is no need to create a step from scratch, since you can use `flow` and the functions on `state-flow.helpers` that create steps operating on the components. But if you do want to, you can check https://github.com/nubank/nu-algebraic-data-types#state-monad and the helpers for examples. It's actually pretty easy.

Example:
```clojure
(ns postman.example
  (:require [state-flow.core :refer [flow]
            [state-flow.helpers.http :as helpers.http]
            [state-flow.helpers.kafka :as helpers.kafka))

(flow "Make a request and consume a message"
  (helpers.http/make-request my-post-request-fn)
  (helpers.kafka/consume {:message my-payload :topic :my-topic}))
```

### Using the return values

All steps have a return value. To take advantage of that, you can use a let-like syntax inside the flow to lexically bind the return value of a step to a variable within the flow.

```clojure
(ns postman.example
  (:require [state-flow.core :refer [flow]
            [state-flow.helpers.http :as helpers.http]
            [state-flow.helpers.kafka :as helpers.kafka))

(flow "Make a get request and consume a message with the return of the request as payload"
  [my-entity (helpers.http/make-request get-entity-req-fn)]
  (helpers.kafka/consume {:message my-entity :topic :my-topic}))
```

You can also use `:let` inside a vector to perform some pure computation. This is similar to clojure `for` list comprehension. For instance:

```clojure
(ns postman.example
  (:require [state-flow.core :refer [flow]
            [state-flow.helpers.http :as helpers.http]
            [state-flow.helpers.kafka :as helpers.kafka))

(flow "Make a get request and consume a message with a payload built from the return value of the request"
  [my-entity (helpers.http/make-request get-entity-req-fn)
   :let [my-payload (transform-entity my-entity)]]
  (helpers.kafka/consume {:message my-payload :topic :my-topic}))
```

This way we can easily combine flows. The return value of a flow is the return value of the last step passed to `flow`.

### Testing with verify

`verify` is a function that takes three arguments: a description, a value or step and another value or midje checker
and produces a step that when executed, verifies that the second argument matches the third argument. It replicates the functionality of a `fact` from midje.
In fact, if a simple value is passed as second argument, what it does is simply call `fact` internally when the flow is executed.

If we pass a step as second argument, it will try to evaluate the step several times until its return value matches the third argument or there is a timeout. This can be useful for avoiding asynchronous problems, when we need to wait for the state to become consistent.

Verify returns a step that will make the check and return something. If the second argument is a value, it will return this argument. If the second argument is itself a step, it will return the last return value of the step that was passed. This makes it possible to use the result of verify on a later part of the flow execution if that is desired.

Say we have a function for making a POST request that stores data in datomic (`store-data-request`),
and we also have a funtion that fetches this data from db (`fetch-data`). We want to check that after we make the POST, the data is persisted:

```clojure
(:require [state-flow.core :refer [flow verify]]
          [state-flow.helpers.core :as helpers]
          [state-flow.helpers.http :as helpers.http])

(defn stores-data-in-db
  [data]
  (flow "save data"
    (helpers.http/make-request (fn [] (store-data-request data))
    [saved-data (helpers/with-db #(fetch-data %))]
    (verify "data is stored in db"
      saved-data
      expected-data)))
```

`GET` and `POST` requests can be done by simple function calls without requiring any components as dependency. Because of this, `make-request` requires that you pass a 0-arity function that will be called only when the flow is executed.

### Testing with match?

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

If the backend, the first test will define the following test, for instance:
```clojure
(deftest my-flow->my-first-test
  (is (match? {:a 2 :b 3} {:a 2 :b 3 :c 4}
```

### Running the flow

A `flow` doesn't do anything by itself, it just defines a function from initial state to a return value and a final state. Therefore, we need to do something to run it. To do that, we use `state-flow.core/run!` with an initial value (usually the initialized components).

```clojure
(def my-flow (flow "my flow" do-this do-that verify-this verify-that))
(run! my-flow {:system {...}})
```

A good approach is to define a custom `run!` function in `postman.aux.init` like this:

```clojure
(ns postman.aux.init
  (:refer-clojure :exclude [run!])
  (:require [my-service.components :as components]
            [state-flow.core :as state-flow]
            [schema.core :as s]))

(defn init! [world]
  (let [system (components/create-and-start-system!)]
    (assoc world :system system)))

(defn run!
  [flow]
  (s/with-fn-validation (state-flow/run! flow (init! {}))))
```
This way, one can have schema validation and also always initialize the system components with the default initializing function.

### Debugging a flow

You can use a `#nu/ftap` macro or the `state-flow.helpers.core/ftap` function to pretty print a State on a given moment during the flow in order to solve some problems easier.

E.g.:
```
(flow "create something new with a POST"
    (verify "the request is properly answered with a 202 status"
      #nu/ftap (aux.http/do-some-post-request! customer-id entity-wire)
      (match expected-body)))
```

This should pretty print the `aux.http/do-some-post-request!` http response entire `:body`, `:headers` and `:status`

## Real-life examples

You can check these services for real life examples:

### Purgatory

https://github.com/nubank/purgatory/blob/master/postman/postman/agreement.clj

### Arnaldo

https://github.com/nubank/arnaldo/blob/master/postman/postman/reissue_card_expiring_same_product.clj
