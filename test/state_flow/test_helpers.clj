(ns state-flow.test-helpers
  (:require [state-flow.core]
            [state-flow.cljtest :as cljtest]
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

(defn swap-later
  "Returns a state/modify step which will (apply swap! (get state k) f args)
  in `delay-ms` milliseconds."
  [delay-ms k f & args]
  (state/modify (fn [state]
                  (future (do (Thread/sleep delay-ms)
                              (apply swap! (get state k) f args)))
                  state)))

(defn delayed-add-two
  "Adds 2 to the value of state in the future."
  [delay-ms]
  (swap-later delay-ms :value + 2))

(defmacro shhh! [& body]
  `(with-redefs [clojure.test/do-report identity]
     (binding [*out* (clojure.java.io/writer (java.io.File/createTempFile "test" "log"))]
       ~@body)))
