(ns lab79.datomic-spec.gen-overrides
  (:require [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as tcgen :refer [generator?]]))


(s/fdef sized-overrides-for
        :args (s/cat :gen-size (s/and integer? pos?)
                     :spec-names (s/* #{:datalog/clause :datomic.query.kv/where}))
        :ret (s/map-of keyword? (s/fspec :args (s/cat)
                                         :ret generator?)))
(defn sized-overrides-for
  "Returns a map from spec names to "
  [gen-size & spec-names]
  (let [datalog-clause-override {:datalog/clause #(tcgen/resize
                                                    gen-size
                                                    (s/gen :datalog/data-pattern))}
        datomic-query-kv-where-override {:datomic.query.kv/where #(tcgen/resize
                                                                    gen-size
                                                                    (s/gen :datomic.query.kv/where
                                                                           datalog-clause-override))}
        spec-set (set spec-names)
        spec-filter (filter (fn [[k _]]
                              (or (empty? spec-set)
                                  (contains? spec-set k))))]
    (->> (merge datalog-clause-override
                datomic-query-kv-where-override)
         (into {} spec-filter))))
