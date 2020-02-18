(ns state-flow.refactoring-tools.refactor-match-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [clojure.edn :as edn]
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
             (zip '(before/match? "description" actual expected {:sleep-time 250}))))))))

(deftest refactor-match-exprs
  (testing "at root"
    (is (= "(after/match? expected actual)"
           (refactor-match/refactor-match-exprs
            {:str "(before/match? \"description\" actual expected)"
             :sym-before 'before/match?
             :sym-after 'after/match?}))))

  (testing "in deftest"
    (is (= "(deftest thing (after/match? expected actual))"
           (refactor-match/refactor-match-exprs
            {:str "(deftest thing (before/match? \"description\" actual expected))"
             :sym-before 'before/match?
             :sym-after 'after/match?}))))

  (testing "multiple matches"
    (is (= "(deftest thing (after/match? expected actual)\n  (after/match? expected2 actual2))"
           (refactor-match/refactor-match-exprs
            {:str "(deftest thing (before/match? \"description\" actual expected)\n  (before/match? \"description\" actual2 expected2))"
             :sym-before 'before/match?
             :sym-after 'after/match?}))))

  (testing "with wrap-in-flow option"
    (is (= "(deftest thing (flow \"description\" \n(after/match? expected actual)))"
           (refactor-match/refactor-match-exprs
            {:str "(deftest thing (before/match? \"description\" actual expected))"
             :sym-before 'before/match?
             :sym-after 'after/match?
             :wrap-in-flow true})))))

(deftest refactor-require
  (is (= "(ns x\n (:require [state-flow.cljtest]))"
         (refactor-match/refactor-require {:str "(ns x\n (:require [state-flow.cljtest]))"})))
  (is (= "(ns x\n (:require [state-flow.assertions.matcher-combinators :refer [match?]]))"
         (refactor-match/refactor-require {:str "(ns x\n (:require [state-flow.cljtest :refer [match?]]))"})))
  (is (= "(ns x\n (:require [state-flow.assertions.matcher-combinators :refer [match?]] \n [state-flow.cljtest :refer [defflow]]))"
         (refactor-match/refactor-require {:str "(ns x\n (:require [state-flow.cljtest :refer [defflow match?]]))"}))))
