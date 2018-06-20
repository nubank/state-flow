# StateFlow

A redesign of Postman using a state monad in the backend for building and composing flows.

The StateFlow library aim to provide an alternative, more functional way in which one could implement postman-like integration tests. The main advantage of this approach is to reduce coupling between test steps and allow for more reusability and composability of flows.

For an in-depth explanation of the internals, check https://wiki.nubank.com.br/index.php/Busquem_Conhecimento_-_tech_talks#Monadic_Postman (it might be a bit outdated).

## Do I need to understand monads?

No. But if you want to, you can read https://github.com/nubank/nu-algebraic-data-types/blob/master/README.md, which also points to some other interesting references.

## Using flow

`flow` expects a description a first parameter and a variable number of state monads as remaining parameters. The return value of a call to `flow` is also a state monad, so it is possible to use flows within flows (within flows within flows...).

A state monad is just a record containing a function from a state (a map containing the components) to a pair `[<return-value>, <updated-state>]`. Think about it as a state transition function ~on steroids~ that has a return value).

One of the main advantages of using a state monad for building the flows is to take advantage of the return value to avoid using the world as a global mutable cache of intermediate results. Another advantage is to provide us with great tools to write and compose general flows into more complicated flows.

95% of the time there is no need to create a state monad from scratch, since you can use the functions on `state-flow.helpers` for that. But if you do want to, you can check https://github.com/nubank/nu-algebraic-data-types#state-monad and the helpers for examples. It's actually pretty easy.

Example:
```clojure
(ns postman.example
  (:require [state-flow.core :refer [flow]
            [state-flow.helpers.http :as helpers.http]
            [state-flow.helpers.kafka :as helpers.kafka))

(flow "Make a request and consume a message"
  (helpers.http/make-request my-post-request-fn
  (helpers.http/consume {:message my-payload :topic :my-topic}))
```

### Using the return values

All state monads have a return value. To take advantage of that, you can use a let-like syntax inside the flow to lexically bind the return value of a state monad to a variable within the flow.

```clojure
(ns postman.example
  (:require [state-flow.core :refer [flow]
            [state-flow.helpers.http :as helpers.http]
            [state-flow.helpers.kafka :as helpers.kafka))

(flow "Make a get request and consume a message with the return of the request as payload"
  [my-entity (helpers.http/make-request get-entity-req-fn)]
  (helpers.http/consume {:message my-entity :topic :my-topic}))
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
  (helpers.http/consume {:message my-payload :topic :my-topic}))
```

This way we can easily combine flows one with the other. The return value of a flow is the return value of the last argument passed to `flow`.

### Testing with verify

`verify` is a function that takes three arguments: a description, a value or state monad and another value or checker
and produces a state monad that when executed, verifies that the second argument matches the third argument. It replicates the functionality of a `fact` from midje.
In fact, if a simple value is passed as second argument, what it does is simply call `fact` internally when the flow is executed.

However, if we pass a state monad as second argument, it will try to evaluate the state monad several times until its return value matches the third argument or
there is a timeout. This can be useful for avoiding asynchronous problems, when we need to wait for the state to become consistent.

The return value of the verify monad is the second argument if it is a value, or the last return value of the state monad. This makes it possible to use the result
of a verify on a later part of the flow execution if that is desired.

Say we have a function for making a POST request that stores data in datomic (`store-data-request`),
and we also have a funtion that fetches this data from db (`fetch-data`). We want to check that after we make the POST, the data is saved.

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

### Running the flow

A `flow` doesn't do anything a by itself, it just defines a function from initial state to a return value and a final state. Therefore, we need to do something to run it. To do that, we use `state-flow.core/run!` with an initial value (usually the initialized components).

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

## Real-life example

You can check https://github.com/nubank/purgatory/blob/master/postman/postman/agreement.clj for real life examples of StateFlow flows.
