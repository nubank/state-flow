(ns state-flow.cljtest
  (:require [clojure.test :as t]
            [state-flow.assertions.matcher-combinators]
            [state-flow.core :as core]
            [state-flow.probe :as probe]))

(defmacro ^:deprecated match?
  "DEPRECATED. Use state-flow.assertions.matcher-combinators/match? instead. "
  [match-desc actual expected & [params]]
  (let [params* (merge {:times-to-try probe/default-times-to-try
                        :sleep-time   probe/default-sleep-time
                        :caller-meta  (meta &form)
                        :description  match-desc
                        :called-from-deprecated-match? true}
                       params)]
    `(~'state-flow.assertions.matcher-combinators/match? ~expected ~actual ~params*)))

(defn ^:private assert-with-clojure-test []
  (state-flow.core/modify-meta assoc :assert-with-clojure-test? true))

(defmacro defflow
  {:doc "Creates a flow and binds it a Var named by name"
   :arglists '([name & flows]
               [name parameters & flows])}
  [name & forms]
  (let [[parameters & flows] (if (map? (first forms))
                               forms
                               (cons {} forms))]
    `(t/deftest ~name
       (core/run* ~parameters
         (core/flow ~(str name) (#'assert-with-clojure-test) ~@flows)))))
