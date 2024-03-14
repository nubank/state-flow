(ns state-flow.cljtest
  (:require [clojure.test :as t]
            [matcher-combinators.clj-test] ;; to register clojure.test assert-expr for `match?`
            [matcher-combinators.printer :as matcher-combinators.printer]
            [matcher-combinators.result :as result]
            [matcher-combinators.test]
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

(defn- assertion-report->match-combinators-result [assertion-report]
  {::result/value (:mismatch/detail assertion-report)})

(defn- clojure-test-result-report
  [{:match/keys [result expected actual]
    :flow/keys [description-stack]
    :as assertion-report}]
  (let [message (core/format-description description-stack)]
    {:type (case result :match :pass :mismatch :fail)
     :message message
     :expected expected
     :actual (if (:mismatch/detail assertion-report)
               (matcher-combinators.clj-test/tagged-for-pretty-printing
                (list '~'not (list 'match? expected actual))
                (assertion-report->match-combinators-result assertion-report))
               actual)
     :file (-> description-stack last core/description->file)
     :line (-> description-stack last :line)}))

(defmacro defflow
  {:doc      "Creates a flow and binds it a Var named by name"
   :arglists '([name & flows]
               [name parameters & flows])}
  [name & forms]
  (let [[parameters & flows] (if (map? (first forms))
                               forms
                               (cons {} forms))
        flow                 `(core/flow ~(str name) ~@flows)]
    `(t/deftest ~(vary-meta name assoc :state-flow {:flow       flow
                                                    :parameters parameters})
       (let [[ret# state#] (core/run* ~parameters ~flow)
             assertions#   (get-in (meta state#) [:test-report :assertions])]
         (doseq [assertion-data# assertions#]
           (t/report (#'clojure-test-result-report assertion-data#)))
         (let [message# (str "Finished running flow " name)]
           (t/report
            {:type    :end-flow
             :message message#
             :final-state state#
             :flow-return ret#}))
         [ret# state#]))))
