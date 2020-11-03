# Changelog

## [5.8.0]

* enables the user to add a hook that is executed before flow execution and after description is updated, via the `before-flow-hook` option

## [5.7.0]

* add `state-flow.api/for` macro [#142](https://github.com/nubank/state-flow/pull/142)

## [5.6.1]

* downgrade timbre logging library major version bump that was done in `5.6.0` because it is causing transient issues.

## [5.6.0]

* create the `:fail-fast?` options for `run*`. When set the flow will failing fast on the first assertion instead of continuing to run

## [5.5.0]

* support n flows passed to `state-flow.labs.state/with-redefs` [#133](https://github.com/nubank/state-flow/pull/133)

## [5.4.0]

* upgrade to [matcher-combinators-3.0.1](https://github.com/nubank/matcher-combinators/blob/master/CHANGELOG.md#301)

## [5.3.0]

* upgrade to [matcher-combinators-2.1.1](https://github.com/nubank/matcher-combinators/blob/master/CHANGELOG.md#211)

## [5.2.0]

* add stack-trace filtering [#129](https://github.com/nubank/state-flow/pull/129)

## [5.1.0]

* improve error message when last arg to flow is a vector [#128](https://github.com/nubank/state-flow/pull/128)

## [5.0.0]

* upgrade to matcher-combinators-2.0.0

*WARNING*: matcher-combinators-2.0.0 includes breaking changes for edge cases.
See https://github.com/nubank/matcher-combinators/blob/master/CHANGELOG.md#200

## [4.1.0]

* add experimental `wrap-fn` and `with-redefs` helpers [#123](https://github.com/nubank/state-flow/pull/123)

## [4.0.3]

* `state-flow.api/match?` throws `times-to-try` exception a runtime instead of macro-expansion time [#125](https://github.com/nubank/state-flow/pull/125)
  * The deprecated `state-flow.cljtest/match?` no longer throws that exception at all.

## [4.0.2]

* Make sure Intellij can find the vars imported in api ns.

## [4.0.1]

* Add `state-flow.api` namespace [#118](https://github.com/nubank/state-flow/pull/118)
  * New namespace which has everything you need™
  * New `fmap` fn

## [3.1.0]

* Add state-flow.core/runner to access runner within flows [#119](https://github.com/nubank/state-flow/pull/119)
* Throw when calling match? with times-to-try > 1 and a value for actual (should be a step) [#116](https://github.com/nubank/state-flow/pull/116)

## [3.0.0]

*WARNING*: for any code relying on previously undocumented behavior of
`state-flow.assertions.matcher-combinators/match?`, this release includes a
breaking change.

``` clojure
;; if you were doing this before in a binding
[actual (match? <expected> <step-that-produces-actual>)]
;; you can do this, now
[actual (fmap report->actual (match? <expected> <step-that-produces-actual>))]
;; or
[report (match? <expected> <step-that-produces-actual>)
 :let [actual (report->actual report)]]
```

- `state-flow.assertions.matcher-combinators/match?` returns a map instead of
   the `actual` value [#110](https://github.com/nubank/state-flow/pull/110)
  - use `state-flow.assertions.matcher-combinators/report->actual` to get the actual value if you need it
- Upgrade to [matcher-combinators 1.5.1](https://github.com/nubank/matcher-combinators/blob/master/CHANGELOG.md#151) (from 1.2.7))

## [2.3.1]

- Upgrade to [funcool.cats 2.3.5](https://github.com/funcool/cats/blob/master/CHANGELOG.md#version-235)

## [2.3.0]

- Enhancements to `state-flow.core/run*`
  - Add `:on-error` option (with default to log and rethrow)
  - Add `:cleanup` option to clean up after an exception
- Deprecate `state-flow.core/run!`
  - `run*` now has the same behavior by default (note the argument order is
    switched and the initial state is passed in as a part of the option map)

## [2.2.6]

- Fix issues with exceptions being thrown and not returned as the left value of
  the error-state monad

## [2.2.5]

- add shell script to refactor match? expressions and `:require [state-flow.cljtest} ...`

## [2.2.4]

- state-flow.state/modify and state-flow.state/gets pass additional args to f
- Introduce `state-flow.assertions.matcher-combinators/match?`
  - deprecate `state-flow.cljtest/match?`
  - add `state-flow.refactoring-tools.refactor-match/refactor-all` to help with
    refactoring to the new version

## [2.2.3]

- Revert changes in `2.2.2` until a few issues are resolved

## ~[2.2.2]~

DO NOT USE VERSION 2.2.2

Changes were reintroduced in `2.2.4`, so use that.

## [2.2.1]
- Use vectors in internal state data structure instead of cats pairs

## [2.2.0]

- Remove delay from the first try of `probe`
- Refactor probe and change return value to include the probed value and check result of each try

## [2.1.5]

- Make `state-flow.state/return` constructable/def'able outside monadic context.

## [2.1.4]

- Add 1-arg arity to `state-flow.core/run` and `state-flow.core/run!` with default initial-state of `{}`

## [2.1.3]
- Improve error when a non-flow expression is provided as a subflow
- Add flow declaration line numbers to failure output

## [2.1.2]
- Added state-flow.core/top-level-description fn
  - mostly for tooling built on top of state-flow
- Removed state-flow.core/get-description
  - This is for internal use, but if you happen to have been using it, you can
    use state-flow.core/current-description instead.

## [2.1.1]
- Moved flow descriptions to the State object's meta

## [2.1.0]
- Moved probe to its own namespace
- Changed push-meta and pop-meta so that execution descriptions are logged (internal)

## [2.0.5]
- Add `state-flow.labs` namespace for experimental features
- Add `state-flow.labs.cljtest/testing`

## [2.0.4]
- update `times-to-try` default from `100` to `5` and `sleep-time` default from `10` to `200`

## [2.0.3]
- Add `state-flow.state/modify` to align with rest of the fn names from Haskell's State Monad
- Deprecate `state-flow.state/swap` (use `modify` instead)

## [2.0.2]
- Update cats and matcher-combinators to latest versions

## [2.0.1]
- Allow `(str ...)` to be a valid flow description

## [2.0.0]
- [BREAKING] Move `verify` to from `state-flow.core` to the `state-flow.midje` namespace.

## [1.15.1]
- Add alias for m/return as state/return

## [1.15.0]
- Require flows to have a string description to prevent the first subflow from
  being used as the description.

## [1.14.0]
- Allow for empty flows

## [1.13.0]
- Add optional parameters to `match?`, making it possible to tweak times-to-try and sleep-time of test probing

## [1.12.1]
- Fix and update matcher-combinators dependency

## [1.12.0]
- Fix license name in `project.clj`

## [1.11.0]
- Implement test probing for match?

## [1.10.0]
- Improved support for clojure test

## [1.9.1]
- Add state related functions
- Move wrap-fn to state namespace
- Improve documentation

## [1.9.0]
- Clean up helpers

## [1.8.0]
- Add with-responses helper

## [1.7.0]
- Add `state-flow.core/ftap` for State pretty printing;

## [1.6.0]
- Add support for clojure.test + matcher-combinators

## [1.5.0]
- Add `helpers.kafka/last-consumed-message`

## [1.4.0]
- Update cats and nu-algebraic-data-types dependencies

## [1.3.0]
- Request helper using request-map

## [1.2.0]
- Experimental req function without exceptions and status assertion
- Update-component helper function

## [1.1.0]
- Add Flow schema
- Add system swap helper
- Add http client get-responses and clear-requests

## [1.0.0]
- Remove responses from function pararemeters

## [0.1.0]
- Moved core code from nu-algebraic-data-types
- Moved helpers from purgatory code
