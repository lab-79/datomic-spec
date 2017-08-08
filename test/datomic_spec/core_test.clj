(ns datomic-spec.core-test
  (:require [clojure.test :refer :all]
            [datomic.api :as d]
            [lab79.datomic-spec :refer :all]
            [lab79.datomic-spec.pull :refer [spec->pull-pattern-spec]]
            lab79.datomic-spec.gen-overrides
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as stest]
            ;; prevent `cannot be cast to clojure.lang.MultiFn` errors by
            ;; explicitly loading test.check
            [clojure.test.check :as tc]))

(stest/instrument (stest/enumerate-namespace 'lab79.datomic-spec.gen-overrides))
(stest/instrument (stest/enumerate-namespace 'lab79.datomic-spec.pull))
(deftest stest-check
  (doseq [result-map (stest/check (stest/enumerate-namespace 'lab79.datomic-spec.gen-overrides))]
    (is (not (contains? result-map :failure)))))

(def db-uri "datomic:mem://tests")

(defn with-conn [f]
  (d/create-database db-uri)
  (f (d/connect db-uri))
  (d/delete-database db-uri))

(deftest datomic-db-test
  (testing "::db spec"
    (with-conn
      (fn [conn] (is (s/valid? :lab79.datomic-spec/db (d/db conn)))))
    (is (not (s/valid? :lab79.datomic-spec/db :foo)))))

(deftest entity-spec-test
  (testing "::entity spec"
    (is (s/valid? :lab79.datomic-spec/entity {:foo/bar 1}))
    (is (s/valid? :lab79.datomic-spec/entity {:db/id "tempid"}))
    (is (not (s/valid? :lab79.datomic-spec/entity {:db/id false}))
        "An invalid :db/id should fail")
    (is (not (s/valid? :lab79.datomic-spec/entity "not a map"))
        "Anything other than a map should fail")))

(deftest db-id-test
  (testing ":db.id/tempid"
    (is (= (first (s/conform :db.id/tempid #db/id[:db.part/user]))
           :dbid)
        "DbId tempid")
    (is (= (first (s/conform :db.id/tempid "tempid")) :string)
        "String tempid"))

  (testing ":db/id"

    (is (= (first (s/conform :db/id 123123123)) :entity-id))

    (is (= (first (s/conform :db/id [:user "foo"])) :lookup-ref))

    (is (= (first (s/conform :db/id "tempid")) :tempid))
    (is (= (first (s/conform :db/id #db/id[:db.part/user])) :tempid))
    (is (not (s/valid? :db/id ":tempid")) "bad tempid")

    (is (= (first (s/conform :db/id :my/ident)) :ident))))

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

(deftest datomic-pull-pattern-spec
  (testing "on single vectors"
    (is (false? (s/valid? :lab79.datomic-spec/pull-pattern :test)))
    (is (false? (s/valid? :lab79.datomic-spec/pull-pattern [])))
    (is (s/valid? :lab79.datomic-spec/pull-pattern ['*]))
    (is (s/valid? :lab79.datomic-spec/pull-pattern [:entity/uuid :db/id])))


  (testing "nested maps"
    (is (s/valid? :lab79.datomic-spec/pull-pattern
                  [:entity/uuid :db/id
                   {:person/name [:person.name/family
                                  :person.name/given]}])))

  (testing "deep nested maps"
    (is (false?
          (s/valid? :lab79.datomic-spec/pull-pattern
                    [:entity/uuid :db/id 6
                     {:person/name [:person.name/family
                                    :person.name/given]}
                     {:nested/test [:nested.attr/test1
                                    {:nested.attr/test2
                                     [:nested-attr.test2/test1 :nested-attr.test2/test2]}]}])))
    (is (s/valid? :lab79.datomic-spec/pull-pattern
                  [:entity/uuid :db/id
                   {:recur-depth/test 6}
                   {:person/name [:person.name/family
                                  :person.name/given]}
                   {:nested/test [:nested.attr/test1
                                  {:nested.attr/test2
                                   [:nested-attr.test2/test1 :nested-attr.test2/test2]}]}]))))

(deftest generating-pull-specs
  (testing "Basic map with scalars"
    (s/def :test-pull/flat-map (s/keys :req [:flat-map/str] :opt [:flat-map/int]))
    (s/def :flat-map/str string?)
    (s/def :flat-map/int integer?)
    (let [pull-spec (spec->pull-pattern-spec :test-pull/flat-map)]
      (is (false? (s/valid? pull-spec [])))
      (is (s/valid? pull-spec [:flat-map/str :flat-map/int]))
      (testing "generation"
        (is (s/valid? pull-spec (gen/generate (s/gen pull-spec))))))

    (testing "Map with nested maps"
      (s/def :test-pull/map-with-nested-map (s/keys :req [:test-pull/flat-map :flat-map/str] :opt [:flat-map/int]))
      (let [pull-spec (spec->pull-pattern-spec :test-pull/map-with-nested-map)]
        (is (false? (s/valid? pull-spec [])))
        (is (false? (s/valid? pull-spec [:test-pull/flat-map])))
        (is (s/valid? pull-spec [{:test-pull/flat-map [:flat-map/str]}]))
        (is (s/valid? pull-spec [{:test-pull/flat-map [:flat-map/str :flat-map/int]}]))
        (is (s/valid? pull-spec [{:test-pull/flat-map [:flat-map/str :flat-map/int]} :flat-map/str :flat-map/int]))
        (is (s/valid? pull-spec [:flat-map/str :flat-map/int]))
        (testing "generation"
          (is (s/valid? pull-spec (gen/generate (s/gen pull-spec)))))))

    (testing "Map with nested collection of maps"
      (s/def :test-pull/map-with-nested-map-coll (s/keys :req [:map-with-nested-map-coll/map-coll]))
      (s/def :map-with-nested-map-coll/map-coll (s/coll-of :test-pull/flat-map))
      (let [pull-spec (spec->pull-pattern-spec :test-pull/map-with-nested-map-coll)]
        (is (false? (s/valid? pull-spec [])))
        (is (false? (s/valid? pull-spec [:map-with-nested-map-coll/map-coll])))
        (is (s/valid? pull-spec [{:map-with-nested-map-coll/map-coll [:flat-map/str :flat-map/int]}]))
        (testing "generation"
          (is (nil? (s/explain-data pull-spec (gen/generate (s/gen pull-spec)))))))

      (testing "Deeply nested map"
        (s/def :test-pull/deep-map (s/keys :req [:deep-map/map]))
        (s/def :deep-map/map :test-pull/map-with-nested-map-coll)
        (let [pull-spec (spec->pull-pattern-spec :test-pull/deep-map)]
          (is (false? (s/valid? pull-spec [:deep-map/map])))
          (is (false? (s/valid? pull-spec [{:deep-map/map [:map-with-nested-map-coll/map-coll]}])))
          (is (false? (s/valid? pull-spec [{:deep-map/map [{:map-with-nested-map-coll/map-coll [:test-pull/flat-map]}]}])))
          (is (s/valid? pull-spec[{:deep-map/map
                                   [{:map-with-nested-map-coll/map-coll
                                     [:flat-map/str :flat-map/int]}]}]))
          (testing "generation"
            (is (nil? (s/explain-data pull-spec (gen/generate (s/gen pull-spec)))))))))))
