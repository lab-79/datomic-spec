(ns datomic-spec.core-test
  (:require [clojure.test :refer :all]
            [lab79.datomic-spec :refer :all]
            [clojure.spec :as s]))

(deftest datalog-vars
  (testing "src vars"
    (is (s/valid? :datalog/src-var '$))
    (is (s/valid? :datalog/src-var '$x))
    (is (not (s/valid? :datalog/src-var '?e))))
  (testing "variables"
    (is (s/valid? :datalog/variable '?))
    (is (s/valid? :datalog/variable '?e))
    (is (not (s/valid? :datalog/variable '$e))))
  (testing "pattern vars"
    (is (s/valid? :datalog/pattern-var 'x))
    (is (not (s/valid? :datalog/pattern-var '$e)))
    (is (not (s/valid? :datalog/pattern-var '?e)))))

(deftest datalog-clauses
  (testing "datalog tuple clauses"
    (is (nil? (s/explain-data :datalog/clause '[?e :x/y])))
    (is (nil? (s/explain-data :datalog/clause ['?e :x/y]))))
  (testing "datalog triplet clauses"
    (is (nil? (s/explain-data :datalog/clause '[?e :x/y :z])))
    (testing "with blanks"
      (is (nil? (s/explain-data :datalog/clause '[_ :x/y :z])))
      (testing "being explicit about implicit blanks"
        (is (nil? (s/explain-data :datalog/clause '[_ :release/name ?release-name _ _]))))))
  (testing "or clauses"
    (is (nil? (s/explain-data :datalog/clause '(or [?e :x/y]))))
    (is (nil? (s/explain-data :datalog/clause '(or [?e :x/y]
                                                [?e :x/z :z]))))
    (testing "eliding brackets should be invalid"
      (is (not (s/valid? :datalog/clause '(or ?e :x/y)))))
;    (testing "accidental nested quotes should be invalid"
;      (is (not (s/valid? :datalog/clause '(or ['?e :x/y])))))
    )
  (testing "and clauses"
    (is (nil? (s/explain-data :datalog/and-clause '(and [?e :x/y]
                                                        [?e :x/z :z]))))
    (testing "eliding brackets should be invalid"
      (is (not (s/valid? :datalog/and-clause '(and ?e :x/y))))))
  (testing "not clauses"
    (is (nil? (s/explain-data :datalog/clause '(not [?e :x/y])))))
  (testing "or and clauses"
    (is (nil? (s/explain-data :datalog/clause '(or [?e :x/y]
                                                   (and [?e :y/z]
                                                        [?e :z/a])))))))

(deftest datomic-queries
  (testing "map form"
    (is (nil? (s/explain-data :datomic/query '{:find [?e]
                                               :where [[?e :x/y :z]]}))))
  (testing "vec form"
    (is (nil? (s/explain-data :datomic/query '[:find ?e :where [?e :x/y :z]
                                                               [?e :x/z :z]])))
    (is (nil? (s/explain-data :datomic/query '[:find ?a (min ?b) ?c (sample 12 ?d)
                                               :where [?a :a/a :a]
                                                      [?b :b/b :b]
                                                      [?c :c/c]
                                                      [?d :d/d]])))))

(deftest vec-queries
  (testing "with blanks"
    (is (nil? (s/explain-data
                :datomic/query
                '[:find ?x 
                  :where [_ :likes ?x]]))))
  (testing "with inputs"
    (is (nil? (s/explain-data
                :datomic/query
                '[:find ?release-name
                  :in $
                  :where [$ _ :release/name ?release-name]])))
    (testing "multiple inputs"
      (is (nil? (s/explain-data
                  :datomic/query
                  '[:find ?release-name
                    :in $ ?artist-name
                    :where [?artist :artist/name ?artist-name]
                           [?release :release/artists ?artist]
                           [?release :release/name ?release-name]]))))
    (testing "tuple bindings"
      (is (nil? (s/explain-data
                  :datomic/query
                  '[:find ?release
                    :in $ [?artist-name ?release-name]
                    :where [?artist :artist/name ?artist-name]
                           [?release :release/artists ?artist]
                           [?release :release/name ?release-name]]))))
    (testing "collection bindings"
      (is (nil? (s/explain-data
                  :datomic/query
                  '[:find ?release-name
                    :in $ [?artist-name ...]
                    :where [?artist :artist/name ?artist-name]
                           [?release :release/artists ?artist]
                           [?release :release/name ?release-name]]))))
    (testing "relation bindings"
      (is (nil? (s/explain-data :datalog/bind-rel '[[?artist-name ?release-name]])))
      (is (not (s/valid? :datalog/bind-rel '[?artist-name ?release-name])))
      (is (not (s/valid? :datalog/bind-rel '[[?artist-name ?release-name] [?x ?y]])))
      (is (nil? (s/explain-data
                  :datomic/query
                  '[:find ?release
                    :in $ [[?artist-name ?release-name]]
                    :where [?artist :artist/name ?artist-name]
                           [?release :release/artists ?artist]
                           [?release :release/name ?release-name]])))))
  (testing "find relation"
    (is (nil? (s/explain-data
                :datomic/query
                '[:find ?e ?x 
                  :where [?e :age 42] [?e :likes ?x]])))))
