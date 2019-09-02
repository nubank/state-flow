# Changelog

## [2.0.0]
- [BREAKING] Move `verify` to `state-flow.midje` namespace.

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
