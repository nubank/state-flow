(ns state-flow.test-helpers
  (:require [cats.core :as m]
            [cats.data :as d]
            [cats.monad.state :as state]
            [state-flow.state :as sf.state]))

(def get-value (comp deref :value))
(def get-value-state (state/gets get-value))

(def increment-two
  (m/mlet [state (sf.state/get)]
    (m/return (+ 2 (-> state :value)))))

(defn delayed-increment-two
  [delay-ms]
  "Changes state in the future"
  (state/state (fn [state]
                 (future (do (Thread/sleep delay-ms)
                             (swap! (:value state) + 2)))
                 (d/pair nil state))))

(defmacro run-flow [flow state]
  `(let [report-data# (atom nil)
         res#         (with-redefs [clojure.test/do-report (fn [data#] (reset! report-data# data#))]
                        (state-flow/run
                          ~flow
                          ~state))]
     {:report-data (->> (deref report-data#)
                        ;; NOTE: :matcher-combinators.result/value is a Mismatch object, which is
                        ;; a defrecord, so equality on a map won't pass, hence pouring it into a map
                        ;; to facilitate equality checks.
                        (clojure.walk/postwalk (fn [node#]
                                                 (if (instance? matcher_combinators.model.Mismatch node#)
                                                   (into {} node#)
                                                   node#))))
      :flow-res    (first res#)
      :flow-state  (second res#)}))
