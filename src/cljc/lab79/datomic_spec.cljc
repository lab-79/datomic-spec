(ns lab79.datomic-spec
  "clojure.spec.alpha defintions for Datomic"
  (:require [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as tcgen]
            [clojure.string :refer [starts-with?]]
            [clojure.spec.gen.alpha :as gen])
  #?(:clj (:import (datomic.db Db DbId))))

(def datomic-value-types
  #{:db.type/string :db.type/boolean :db.type/long :db.type/bigint :db.type/float :db.type/double :db.type/bigdec
    :db.type/instant :db.type/uuid :db.type/uri :db.type/keyword :db.type/bytes :db.type/ref})

(def datomic-schema-keys
  #{:db/id :db/ident :db/valueType :db/cardinality :db/doc :db/unique :db/index :db/isComponent :db/noHistory
    :db/fulltext :db.install/_attribute :db.install/_partition})

;
; Special Datomic attributes
;

#?(:clj (s/def ::db #(instance? Db %)))

;; The map may include a :db/id key identifying the entity data that the map refers to
;; http://docs.datomic.com/transactions.html#sec-1-2
(s/def ::entity (s/keys :opt [:db/id]))

(s/def :db/id
  (s/or
    :entity-id number?
    :lookup-ref ::lookup-ref
    :tempid :db.id/tempid
    :ident :db.id/ident))

(s/def :db.id/long number?)
(s/def ::lookup-ref
  (s/tuple keyword?
           (s/or :string string?
                 :keyword keyword?
                 :num number?
                 :uuid uuid?)))
(s/def :db.id/tempid
  (s/or
    #?@(:clj [:dbid #(instance? DbId %)])
    :string (s/and string? #(not (starts-with? % ":")))))
; TODO Deprecate ::tempid
(s/def ::tempid :db.id/tempid)
(s/def :db.id/ident keyword?)

(s/def :db/ident (s/with-gen keyword? #(tcgen/resize 2 (gen/keyword-ns))))
(s/def :db/valueType datomic-value-types)
(s/def :db/cardinality #{:db.cardinality/one :db.cardinality/many})
(s/def :db/doc string?)
(s/def :db/unique #{:db.unique/value :db.unique/identity})
(s/def :db/index boolean?)
(s/def :db/isComponent boolean?)
(s/def :db/noHistory boolean?)
(s/def :db/fulltext boolean?)

(s/def :db.install/_attribute #{:db.part/db})

(s/def :datomic/field-schema
  (s/keys :req [:db/ident :db/valueType :db/cardinality]
          :opt [:db/id :db.install/_attribute :db/doc :db/unique :db/index
                :db/isComponent :db/noHistory :db/fulltext]))

;
; Partitions
;


(s/def :db.install/_partition #{:db.part/db})

(s/def :datomic/partition-schema (s/keys :req [:db/id :db/ident :db.install/_partition]))

(s/def :datomic/enum-schema (s/keys :req [:db/id :db/ident] :opt [:db/doc]))

;
; Datalog values
;

(s/def :datomic-spec.value/any (s/or :kw keyword?
                                     :str string?
                                     :bool boolean?
                                     :num number?
                                     :int integer?
                                     :float #?(:clj float? :cljs number?)
                                     :inst inst?
                                     :uuid uuid?
                                     :uri #?(:clj uri? :cljs string?) ; TODO Get more precise than string?
                                     :bytes #?(:clj bytes? :cljs string?))) ; TODO Get more precise than string?

;
; Datalog
;

(defn- spec-for-prefixed-sym
  [prefix]
  (s/with-gen
    (s/and symbol?
           #(clojure.string/starts-with? (name %) prefix))
    #(->> (gen/not-empty (gen/string-alphanumeric))
          (gen/fmap (fn [keyword-name] (symbol (str prefix keyword-name)))))))

(s/def :datomic/query (s/or :map-query :datomic/map-query
                            :vec-query :datomic/vec-query))
(s/def :datomic/vec-query (s/spec (s/cat :find-spec      :datalog/find
                                         :with-clause?   (s/? :datalog/with-clause)
                                         :inputs?        (s/? :datalog/inputs)
                                         :where-clauses? (s/? :datalog/where-clauses))))
(s/def :datalog/find (s/cat :find #{:find}
                            :find-vars :datalog/find-vars))
(s/def :datomic/map-query (s/keys :req-un [:datomic.query.kv/find :datomic.query.kv/where]))
(s/def :datomic.query.kv/find
  (s/with-gen (s/spec (s/cat :find-rhs :datalog/find-vars))
              #(gen/return ['?e '.])))
(s/def :datomic.query.kv/where (s/coll-of :datalog/clause :kind vector? :min-count 1))


(s/def :datalog/src-var (spec-for-prefixed-sym "$"))
(s/def :datalog/variable (spec-for-prefixed-sym "?"))
(s/def :datalog/plain-symbol (s/and symbol?
                                    (fn [sym]
                                      (every? #(not (starts-with? (name sym) %)) ["$" "?"]))))
(s/def :datalog/pattern-var :datalog/plain-symbol)
(s/def :datalog/rules-var #{'%})

(s/def :datalog/find-vars (s/alt :relation     :datalog/find-rel
                                 :collection   :datalog/find-coll
                                 :scalar       :datalog/find-scalar
                                 :single-tuple :datalog/find-tuple))
(s/def :datalog/find-elem (s/alt :variable     :datalog/variable
                                 :pull-expr    :datalog/pull-expr
                                 :aggregate    :datalog/aggregate
                                 ))
(s/def :datalog/find-rel (s/+ :datalog/find-elem))
(s/def :datalog/find-scalar (s/cat :scalar :datalog/find-elem
                                   :.      #{'.}))
;(s/def :datalog/find-coll (s/spec (s/tuple :datalog/find-elem #{'...})))
(s/def :datalog/find-coll (s/tuple :datalog/find-elem #{'...}))
(s/def :datalog/find-tuple (s/coll-of :datalog/find-elem :kind vector? :min-count 1))
(s/def :datalog/pull-expr (s/cat :pull #{'pull}
                                 :variable :datalog/variable
                                 :pattern :datalog/pattern))
(s/def :datalog/pattern (s/+ :datalog.pattern/attr-spec))
(s/def :datalog.pattern/attr-spec (s/alt :attr-name       :datalog.pattern/attr-name
                                         :wildcard        :datalog.pattern/wildcard
                                         :map-spec        :datalog.pattern/map-spec
                                         :attr-spec       :datalog.pattern/attr-spec
                                         :limit-expr      :datalog.pattern/limit-expr
                                         :default-expr    :datalog.pattern/default-expr
                                         :recursion-limit :datalog.pattern/recursion-limit))
(s/def :datalog.pattern/attr-name keyword?)
(s/def :datalog.pattern/wildcard #{"*" '*})
(s/def :datalog.pattern/map-spec (s/map-of (s/or :attr-name  :datalog.pattern/attr-name
                                                 :limit-expr :datalog.pattern/limit-expr)
                                           (s/or :pattern         :datalog/pattern
                                                 :recursion-limit :datalog.pattern/recursion-limit)
                                           :min-count 1))
(s/def :datalog.pattern/attr-spec (s/alt :limit-expr :datalog.pattern/limit-expr
                                         :default-expr :datalog.pattern/default-expr))
(s/def :datalog.pattern/limit-expr (s/cat :limit #{"limit" 'limit}
                                          :attr-name :datalog.pattern/attr-name
                                          :pos-num-or-nil (s/alt :pos-num (s/and integer? pos?)
                                                                 :nil nil?)))
(s/def :datalog.pattern/default-expr (s/cat :default #{"default" 'default}
                                            :attr-name :datalog.pattern/attr-name
                                            :any-value any?))
(s/def :datalog.pattern/recursion-limit (s/alt :pos-num (s/and integer? pos?)
                                               :... #{'...}))


;
; Aggregates
;

(s/def :datalog/aggregate (s/spec (s/cat :aggregate-fn-name symbol?
                                         :fn-args (s/+ :datalog/fn-arg))))
(s/def :datalog/fn-arg (s/alt :variable :datalog/variable
                              :constant :datalog/constant
                              :src-var  :datalog/src-var))
(s/def :datalog/constant (s/with-gen
                           (comp not symbol?)
                           #(s/gen :datomic-spec.value/any)))

(s/def :datalog/inputs (s/cat :in #{:in}
                              :inputs (s/+ (s/alt :src-var     :datalog/src-var
                                                  :binding     :datalog/binding
                                                  :pattern-var :datalog/pattern-var
                                                  :rules-var   :datalog/rules-var))))

(s/def :datalog/with-clause (s/cat :with #{:with}
                                   :variable+ (s/+ :datalog/variable)))

(s/def :datalog/where-clauses (s/cat :where #{:where}
                                     :clauses (s/+ :datalog/clause)))

(s/def :datalog/clause
  (s/or :not-clause :datalog/not-clause
        :not-join-clause :datalog/not-join-clause
        :or-clause :datalog/or-clause
        :or-join-clause :datalog/or-join-clause
        :expression-clause :datalog/expression-clause))

(s/def :datalog/or-clause (s/cat :or-sym #{'or}
                                 :clauses (s/+ (s/alt :clause :datalog/clause
                                                      :and-clause :datalog/and-clause))))
(s/def :datalog/and-clause
  (s/spec (s/cat :and-sym #{'and}
                 :clauses (s/+ :datalog/clause))))
(s/def :datalog/not-clause (s/cat :src-var? (s/? :datalog/src-var)
                                  :not-sym #{'not}
                                  :clause+ (s/+ :datalog/clause)))
(s/def :datalog/not-join-clause (s/cat :src-var?     (s/? :datalog/src-var)
                                       :not-join     #{'not-join}
                                       :variable-vec (s/coll-of :datalog/variable :kind vector? :min-count 1)
                                               :clause+      (s/+ :datalog/clause)))
(s/def :datalog/or-join-clause (s/cat :src-var? (s/? :datalog/src-var)
                                      :or-join #{'or-join}
                                      :rule-vars :datalog/rule-vars
                                      :clauses (s/+ (s/alt :clause :datalog/clause
                                                           :and-clause :datalog/and-clause))))
(s/def :datalog/rule-vars (s/alt :variable+ (s/+ :datalog/variable)
                                 :req&unboundvars (s/cat :req-vars (s/spec (s/+ :datalog/variable))
                                                         :unbound-vars (s/* :datalog/variable))))
(s/def :datalog/expression-clause
  (s/alt :data-pattern :datalog/data-pattern
         :pred-expr    :datalog/pred-expr
         :fn-expr      :datalog/fn-expr
         :rule-expr    :datalog/rule-expr))
(s/def :datalog/data-pattern (s/cat :src-var? (s/? :datalog/src-var)
                                    :pattern (s/+ (s/alt :variable :datalog/variable
                                                         :constant :datalog/constant
                                                         :_        #{'_}))))
(s/def :datalog/pred-expr (s/tuple (s/spec (s/cat :pred    :datalog/plain-symbol
                                                  :fn-arg+ (s/+ :datalog/fn-arg)))))
(s/def :datalog/fn-expr (s/tuple (s/spec (s/cat :fn :datalog/plain-symbol
                                                :fn-arg+ (s/+ :datalog/fn-arg)))
                                 :datalog/binding))
(s/def :datalog/binding (s/alt :bind-scalar :datalog/variable
                               :bind-tuple  :datalog/bind-tuple
                               :bind-coll   :datalog/bind-coll
                               :bind-rel    :datalog/bind-rel))
(s/def :datalog/bind-tuple (s/spec (s/+ (s/alt :variable :datalog/variable
                                               :_        #{'_}))))
(s/def :datalog/bind-coll (s/tuple :datalog/variable #{'...}))
(s/def :datalog/bind-rel (s/tuple (s/+ (s/alt :variable :datalog/variable
                                                       :_         #{'_}))))
(s/def :datalog/rule-expr (s/spec (s/cat :src-var?  (s/? :datalog/src-var)
                                         :rule-name :datalog/rule-name
                                         :rest      (s/+ (s/alt :variable :datalog/variable
                                                                :constant :datalog/constant
                                                                :_         #{'_})))))
(s/def :datalog/rule-name :datalog/plain-symbol)

;
; Rules - http://docs.datomic.com/query.html#rules
;

(s/def :datalog/rule (s/spec (s/+ (s/spec (s/cat :rule-head :datalog.rule/rule-head
                                                 :clause+   (s/+ :datalog/clause))))))
(s/def :datalog.rule/rule-head (s/tuple :datalog/rule-name :datalog/rule-vars))


(s/def :datomic.api/q
  (s/fspec :args (s/cat :query (s/with-gen
                                 (s/or :map-query :datomic/map-query
                                       :vector-query :datomic/vec-query)
                                 #(gen/return {:find ['?e '.] :where [['?e :db/id]]})
                                 ;#(s/gen :datomic/map-query)
                                 )
                         :db (s/coll-of (s/tuple :db/id
                                                 :db/ident
                                                 :datomic-spec.value/any))
                         :params (s/with-gen
                                   (s/* any?)
                                   ; Never generate parameters
                                   #(gen/return [])))
            :ret any?))
