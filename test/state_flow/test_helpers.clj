(ns state-flow.test-helpers
  (:require [state-flow.core]
            [state-flow.state :as state]))

(defmacro this-line-number
  "Returns the line number of the call site."
  []
  (let [m (meta &form)]
    `(:line ~m)))

(def get-value (comp deref :value))
(def get-value-state (state/gets get-value))

(def add-two
  (state/gets (comp (partial + 2) :value)))

(defn delayed-add-two
  [delay-ms]
  "Changes state in the future"
  (state/modify (fn [state]
                      (future (do (Thread/sleep delay-ms)
                                  (swap! (:value state) + 2)))
                      state)))



(defmacro run-flow
  "Wrapper for `state-flow.core/run!`, but captures clojure.test's report data
  instead of printing it to *out*. Returns a map of:

    :report-data - the data that _would_ be reported via clojure.test
    :flow-ret    - the return value of the flow
    :flow-state  - the end-state of the flow "
  [flow state]
  `(let [report-data# (atom nil)
         res#         (with-redefs [clojure.test/do-report (fn [data#] (reset! report-data# data#))]
                        (binding [*out* (clojure.java.io/writer (java.io.File/createTempFile "test" "log"))]
                          (state-flow.core/run!
                           ~flow
                           ~state)))]
     {:report-data (->> (deref report-data#)
                        ;; NOTE: :matcher-combinators.result/value is a Mismatch object, which is
                        ;; a defrecord, so equality on a map won't pass, hence pouring it into a map
                        ;; to facilitate equality checks.
                        (clojure.walk/postwalk (fn [node#]
                                                 (if (instance? matcher_combinators.model.Mismatch node#)
                                                   (into {} node#)
                                                   node#))))
      :flow-ret    (first res#)
      :flow-state  (second res#)}))
