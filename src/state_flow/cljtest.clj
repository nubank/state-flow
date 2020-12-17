(ns state-flow.cljtest
  (:require [clojure.test :as t]
            [matcher-combinators.test] ;; to register clojure.test assert-expr for `match?`
            [state-flow.assertions.matcher-combinators]
            [state-flow.core :as core]
            [state-flow.probe :as probe]
            [matcher-combinators.printer :as matcher-combinators.printer]))

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

(defn- tag-for-pretty-printing [actual-summary result]
  (with-meta {:summary      actual-summary
              :match-result result}
    {:type ::mismatch}))

(defmethod clojure.core/print-method ::mismatch [{:keys [match-result]} out]
  (binding [*out* out]
    (matcher-combinators.printer/pretty-print match-result)))

(defn- clojure-test-report
  [{:match/keys [result expected actual]
    :flow/keys [description-stack]
    :as assertion-report}]
  (let [message (core/format-description description-stack)]
    {:type (case result :match :pass :mismatch :fail)
     :message message
     :expected expected
     :actual (if (:mismatch/detail assertion-report)
               (tag-for-pretty-printing
                (list '~'not (list 'match? expected actual))
                (:mismatch/detail assertion-report))
               actual)
     :file (-> description-stack last core/description->file)
     :line (-> description-stack last :line)}))

(defmacro defflow
  {:doc "Creates a flow and binds it a Var named by name"
   :arglists '([name & flows]
               [name parameters & flows])}
  [name & forms]
  (let [[parameters & flows] (if (map? (first forms))
                               forms
                               (cons {} forms))]
    `(t/deftest ~name
       (let [[ret# state#] (core/run* ~parameters (core/flow ~(str name) ~@flows))
             assertions# (get-in (meta state#) [:test-report :assertions])]
         (doseq [assertion-data# assertions#]
           (t/report (#'clojure-test-report assertion-data#)))
         [ret# state#]))))
