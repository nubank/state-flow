(ns state-flow.cljtest
  (:require [clojure.test :as t]
            [state-flow.assertions.matcher-combinators]
            [matcher-combinators.test] ;; to register clojure.test assert-expr for `match?`
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

(defn report->assertion
  [assertion-report]
  (let [description (core/format-description (:flow/description-stack assertion-report))
        expected (:match/expected assertion-report)
        actual (:match/actual assertion-report)]
    (t/testing description (t/is (match? expected actual)))))

(defmacro defflow
  {:doc "Creates a flow and binds it a Var named by name"
   :arglists '([name & flows]
               [name parameters & flows])}
  [name & forms]
  (let [[parameters & flows] (if (map? (first forms))
                               forms
                               (cons {} forms))]
    `(t/deftest ~name
       (let [[ret# final-state#] (core/run* ~parameters (core/flow ~(str name) ~@flows))
             test-report# (get (meta final-state#) :test-report)]
         (doseq [assertion# (:assertions test-report#)]
           (report->assertion assertion#))
         [ret# final-state#]))))
