(ns state-flow.protocols)

(defprotocol Functor
  "A data type that can be mapped over without altering its context."
  (-fmap [ftor f fv] "Applies function f to the value(s) inside the context of the functor fv."))

(defprotocol Applicative
  "The Applicative abstraction."
  (-fapply [app af av]
    "Applies the function(s) inside af's context to the value(s)
     inside av's context while preserving the context.")
  (-pure [app v]
    "Takes any context or monadic value `app` and any value `v`, and puts
     the value `v` in the most minimal context (normally `mempty`) of same type of `app`"))

(defprotocol Monad
  "The Monad abstraction."
  (-mreturn [m v])
  (-mbind [m mv f]))

(defprotocol Contextual
  "Abstraction that establishes a concrete type as a member of a context.

  A great example is the Maybe monad type Just. It implements
  this abstraction to establish that Just is part of
  the Maybe monad."
  (-get-context [_] "Get the context associated with the type."))

(defprotocol Extract
  "A type class to extract the
  value from a monad context."
  (-extract [mv] "Extract the value from monad context."))

(defprotocol Printable
  "An abstraction to make a type printable in a platform
  independent manner."
  (-repr ^String [_] "Get the repl ready representation of the object."))

(defprotocol MonadState
  "A specific case of Monad abstraction for
  work with state in pure functional way."
  (-get-state [m] "Return the current state.")
  (-put-state [m newstate] "Update the state.")
  (-swap-state [m f] "Apply a function to the current state and update it."))
