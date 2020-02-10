(ns state-flow.cljtest
  (:require [clojure.test :as t]
            [state-flow.core :as core]
            [state-flow.probe :as probe]
            [state-flow.assertions.matcher-combinators :as amc]))

(defmacro match?
  "DEPRECATED. Use state-flow.assertions.matcher-combinators/match? instead. "
  [match-desc actual expected & [params]]
  (let [params* (merge {:times-to-try probe/default-times-to-try
                        :sleep-time   probe/default-sleep-time
                        :caller-meta  (meta &form)
                        :description  match-desc}
                       params)]
    `(amc/match? ~expected ~actual ~params*)))

(defmacro defflow
  {:arglists '([name & flows]
               [name parameters & flows])}
  [name & forms]
  (let [[parameters & flows] (if (map? (first forms))
                               forms
                               (cons {} forms))]
    `(t/deftest ~name
       (core/run* ~parameters (core/flow ~(str name) ~@flows)))))
