(ns state-flow.test-helpers
  (:require [clojure.java.io :as io]
            [cats.core :as m]
            [cats.data :as d]
            [cats.monad.state :as state]
            [state-flow.state :as sf.state])
  (:import (java.io File)))

(def get-value (comp deref :value))
(def get-value-state (state/gets get-value))

(def add-two
  (m/mlet [state (sf.state/get)]
    (m/return (+ 2 (-> state :value)))))

(defn delayed-add-two
  [delay-ms]
  "Changes state in the future"
  (state/state (fn [state]
                 (future (do (Thread/sleep delay-ms)
                             (swap! (:value state) + 2)))
                 (d/pair nil state))))

(defmacro run-flow [flow state]
  `(let [report-data# (atom nil)
         res#         (with-redefs [clojure.test/do-report (fn [data#] (reset! report-data# data#))]
                        (binding [*out* (io/writer (File/createTempFile "test" "log"))]
                          (state-flow/run!
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
      :flow-res    (first res#)
      :flow-state  (second res#)}))
