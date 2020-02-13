(ns state-flow.refactoring-tools.refactor-match-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [clojure.edn :as edn]
            [rewrite-clj.zip :as z]
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
              :sym-after 'after/match?}
             (zip '(before/match? "description" actual expected))))))))

(deftest refactor-all
  (testing "at root"
    (is (= "(after/match? expected actual)"
           (refactor-match/refactor-all
            {:str "(before/match? \"description\" actual expected)"
             :sym-before 'before/match?
             :sym-after 'after/match?}))))

  (testing "in deftest"
    (is (= "(deftest thing (after/match? expected actual))"
           (refactor-match/refactor-all
            {:str "(deftest thing (before/match? \"description\" actual expected))"
             :sym-before 'before/match?
             :sym-after 'after/match?}))))

  (testing "multiple matches"
    (is (= "(deftest thing (after/match? expected actual)\n  (after/match? expected2 actual2))"
           (refactor-match/refactor-all
            {:str "(deftest thing (before/match? \"description\" actual expected)\n  (before/match? \"description\" actual2 expected2))"
             :sym-before 'before/match?
             :sym-after 'after/match?}))))

  (testing "with wrap-in-flow option"
    (is (= "(deftest thing (flow \"description\" (after/match? expected actual)))"
           (refactor-match/refactor-all
            {:str "(deftest thing (before/match? \"description\" actual expected))"
             :sym-before 'before/match?
             :sym-after 'after/match?
             :wrap-in-flow true})))))
