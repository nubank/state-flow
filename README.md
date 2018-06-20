# StateFlow

A redesign of Postman using a state monad in the backend for building and composing flows.

The StateFlow library aim to provide an alternative, more functional way in which one could implement postman-like integration tests. The main advantage of this approach is to reduce coupling between test steps and allow for more reusability and composability of flows.

## Do I need to understand monads?

No. But if you want to, you can see the README from https://github.com/nubank/nu-algebraic-data-types, which also points to some other interesting references.

## Using flow

`flow` expects a description a first parameter and a variable number of state monads as remaining parameters. The return value of a call to `flow` is also a state monad, so it is possible to use flows within flows.

A state monad is just a record containing a function from a state (a map containing the components) to a pair `[<return-value>, <updated-state>]`. Think about it as a state transition function on steroids (that also has a return value)

One of the main advantages of using a state monad for building the flows is to avoid using the world as a global mutable cache of intermediate results. Another advantage is to provide us with great tools to write and compose general flows into more complicated flows.

95% of the time there is no need to create a state monad from scratch, since you can use the functions on `state-flow.helpers` for that. But if you do want to, you can check https://github.com/nubank/nu-algebraic-data-types#state-monad and the helpers for examples.

```clojure
(ns postman.example
  (:require [state-flow.core :refer [flow]
            [state-flow.helpers.http :as helpers.http]
            [state-flow.helpers.kafka :as helpers.kafka))

(flow "Make a request and consume a message"
  (helpers.http/make-request my-post-request-fn
  (helpers.http/consume {:message my-payload :topic :my-topic}))
```

### Testing with verify

`verify` is a function that takes three arguments: a description, a value or state monad and another value or checker (TODO: Currently the checker needs to be passed quoted)
and produces a state monad that when executed, verifies that the second argument matches the third argument. It replicates the functionality of a `fact` from midje.
In fact, if a simple value is passed as second argument, what it does is simply call `fact` internally.

However, if we pass a state monad as second argument, it will try to evaluate the state monad several times until its return value matches the third argument or
there is a timeout. This can be useful for avoiding asynchronous problems, when we need to wait for the state to become consistent.

Fhe return value of the verify monad is the second argument if it is a value, or the last return value of the state monad. This makes it possible to use the result
of a verify on a later part of the flow execution if that is desired.

## Simple Example

Say we have a function for making a POST request that stores data in datomic (`store-data-request`),
and we also have a funtion that fetches this data from db (`fetch-data`). We want to check that after we make the POST, the data is saved.

```clojure
(:require [nu.monads.state :as state]
          [nu.monads.postman :refer [flow verify]]
          [nu.monads.postman.helpers :refer [with-db])

(def get-db (comp ddb/db :datomic :system))

(defn stores-data-in-db
  [data]
  (flow "save data"
    (state/wrap-fn #(store-data-request data)
    [saved-data (with-db #(fetch-data %))]
    (verify "data is stored in db"
      saved-data
      expected-data)))

(state/run (stores-data-in-db <data>) (init/init!))
=> (d/pair <data> <world>)
```

`GET` and `POST` requests can be done by simple function calls without requiring any components as dependency. Because of this, we need to watch that we wrap any http request in a state monad. `wrap-fn` does just that.

## Real-life example

You can check https://github.com/nubank/purgatory/blob/master/postman/postman/agreement.clj for real life examples of StateFlow flows.
