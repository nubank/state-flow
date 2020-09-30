(ns state-flow.refactoring-tools.refactor-match-test
  (:require [clojure.edn :as edn]
            [clojure.test :as t :refer [are deftest is testing]]
            [rewrite-clj.zip :as z]
            [state-flow.probe :as probe]
            [state-flow.refactoring-tools.refactor-match :as refactor-match]))

(defn zip [expr]
  (z/of-string (str expr)))

(defn unzip [zipper]
  (edn/read-string (z/root-string zipper)))

(deftest refactor-match-expr
  (is (= '(after/match? expected actual)
         (unzip
          (refactor-match/refactor-match-expr
           {:sym-after 'after/match?}
           (zip '(after/match? "description" actual expected))))))

  (testing "with wrap-in-flow option"
    (is (= '(flow "description" (after/match? expected actual))
           (unzip
            (refactor-match/refactor-match-expr
             {:wrap-in-flow true
              :sym-after    'after/match?}
             (zip '(before/match? "description" actual expected)))))))
  (testing "with force-probe-params option"
    (is (= `(~'flow "description" (~'after/match? ~'expected ~'actual
                                                  {:times-to-try ~probe/default-times-to-try
                                                   :sleep-time   ~probe/default-sleep-time}))
           (unzip
            (refactor-match/refactor-match-expr
             {:wrap-in-flow       true
              :force-probe-params true
              :sym-after          'after/match?}
             (zip '(before/match? "description" actual expected))))))
    (is (= `(~'flow "description" (~'after/match? ~'expected ~'actual
                                                  {:times-to-try 1
                                                   :sleep-time   ~probe/default-sleep-time}))
           (unzip
            (refactor-match/refactor-match-expr
             {:wrap-in-flow       true
              :force-probe-params true
              :sym-after          'after/match?}
             (zip '(before/match? "description" actual expected {:times-to-try 1}))))))
    (is (= `(~'flow "description" (~'after/match? ~'expected ~'actual
                                                  {:times-to-try ~probe/default-times-to-try
                                                   :sleep-time 250}))
           (unzip
            (refactor-match/refactor-match-expr
             {:wrap-in-flow       true
              :force-probe-params true
              :sym-after          'after/match?}
             (zip '(before/match? "description" actual expected {:sleep-time 250})))))))
  (testing "edge cases"
    (testing "#(anon-fn) becomes (fn* [] (anon-fn))"
      (is (= "(after/match? (fn* [] (anon-fn)) actual)"
             (z/root-string
              (refactor-match/refactor-match-expr
               {:sym-after    'after/match?}
               (z/of-string "(before/match? \"description\" actual #(anon-fn))"))))))
    (testing "map should not end up w/ commas or reorder map content"
      (is (= "(after/match? {:c :d :a :b} {:a :b :c :d})"
             (z/root-string
              (refactor-match/refactor-match-expr
               {:sym-after    'after/match?}
               (z/of-string "(before/match? \"description\" {:a :b :c :d} {:c :d :a :b})"))))))))

(deftest refactor-match-exprs
  (testing "at root"
    (is (= "(after/match? expected actual)"
           (z/root-string
            (refactor-match/refactor-match-exprs
             {:str "(before/match? \"description\" actual expected)"
              :sym-before 'before/match?
              :sym-after 'after/match?})))))

  (testing "in deftest"
    (is (= "(deftest thing (after/match? expected actual))"
           (z/root-string
            (refactor-match/refactor-match-exprs
             {:str "(deftest thing (before/match? \"description\" actual expected))"
              :sym-before 'before/match?
              :sym-after 'after/match?})))))

  (testing "multiple matches"
    (is (= "(deftest thing (after/match? expected actual)\n  (after/match? expected2 actual2))"
           (z/root-string
            (refactor-match/refactor-match-exprs
             {:str "(deftest thing (before/match? \"description\" actual expected)\n  (before/match? \"description\" actual2 expected2))"
              :sym-before 'before/match?
              :sym-after 'after/match?})))))

  (testing "with wrap-in-flow option"
    (is (= "(deftest thing (flow \"description\" \n(after/match? expected actual)))"
           (z/root-string
            (refactor-match/refactor-match-exprs
             {:str "(deftest thing (before/match? \"description\" actual expected))"
              :sym-before 'before/match?
              :sym-after 'after/match?
              :wrap-in-flow true}))))))

(deftest refactor-ns-declaration
  (testing "ns declarations that get refactored"
    (testing "without wrap-in-flow"
      (are [expected input] (= expected
                               (z/root-string
                                (refactor-match/refactor-ns-dec
                                 {:z (z/of-string input)})))

        "(ns x\n (:require [state-flow.assertions.matcher-combinators :refer [match?]]))"
        "(ns x\n (:require [state-flow.cljtest :refer [match?]]))"

        "(ns x\n (:require [state-flow.assertions.matcher-combinators :refer [match?]] \n [state-flow.cljtest :refer [defflow]]))"
        "(ns x\n (:require [state-flow.cljtest :refer [defflow match?]]))"

        "(ns x\n (some-expression)\n (:require [state-flow.assertions.matcher-combinators :refer [match?]]))"
        "(ns x\n (some-expression)\n (:require [state-flow.cljtest :refer [match?]]))"

        "(ns x\n (some-expression)\n (:require [clojure.string :as str]\n [state-flow.assertions.matcher-combinators :refer [match?]]))"
        "(ns x\n (some-expression)\n (:require [clojure.string :as str]\n [state-flow.cljtest :refer [match?]]))"))

    (testing "with wrap-in-flow"
      (are [expected input] (= expected
                               (z/root-string
                                (refactor-match/refactor-ns-dec
                                 {:z (z/of-string input)
                                  :wrap-in-flow true})))

        "(ns x (:require [state-flow.core :refer [flow]]))"
        "(ns x (:require [state-flow.core :refer [flow]]))"

        "(ns x (:require [state-flow.core :refer [abc flow]]))"
        "(ns x (:require [state-flow.core :refer [abc]]))"

        "(ns x (:require [state-flow.core :as state-flow :refer [flow]]))"
        "(ns x (:require [state-flow.core :as state-flow]))"

        "(ns x (:require [state-flow.assertions.matcher-combinators :refer [match?]]\n[state-flow.core :refer [flow]]))"
        "(ns x (:require [state-flow.cljtest :refer [match?]]\n[state-flow.core :refer [flow]]))"

        "(ns x (:require [state-flow.assertions.matcher-combinators :refer [match?]]\n[state-flow.core :refer [abc flow]]))"
        "(ns x (:require [state-flow.cljtest :refer [match?]]\n[state-flow.core :refer [abc]]))"

        "(ns x (:require [state-flow.assertions.matcher-combinators :refer [match?]]\n[state-flow.core :as state-flow :refer [flow]]))"
        "(ns x (:require [state-flow.cljtest :refer [match?]]\n[state-flow.core :as state-flow]))"

        "(ns x (:require [a-lib] \n[state-flow.core :refer [flow]]))"
        "(ns x (:require [a-lib]))")))

  (testing "ns declarations that don't change"
    (doseq [exp ["(ns x\n (:require [state-flow.cljtest]))"
                 "(ns x\n (:require [state-flow.cljtest :refer [defflow]]))"
                 "(ns x\n (:require [another-library :refer [match?]]))"
                 "(ns x\n (some-expression)\n (:require [another.lib :refer [match?]]))"
                 "(ns x\n (some-expression)\n (:require [clojure.string :as str]\n [another.lib :refer [match?]]))"]]
      (is (= exp (z/root-string (refactor-match/refactor-ns-dec {:z (z/of-string exp)})))))))

(deftest refactor
  (let [input "(ns x (:require [state-flow.cljtest :refer [match?]]))\n\n(defflow a-flow (match? \"desc\" actual expected))"]
    (testing "without wrap-in-flow"
      (is (= (str "(ns x (:require [state-flow.assertions.matcher-combinators :refer [match?]]))"
                  "\n\n(defflow a-flow (match? expected actual))")
             (refactor-match/refactor!
              {:str input}))))
    (testing "with wrap-in-flow"
      (is (= (str "(ns x (:require [state-flow.assertions.matcher-combinators :refer [match?]] \n[state-flow.core :refer [flow]]))"
                  "\n\n(defflow a-flow (flow \"desc\" \n(match? expected actual)))")
             (refactor-match/refactor!
              {:str input
               :wrap-in-flow true}))))))

(clojure.test/run-tests)
