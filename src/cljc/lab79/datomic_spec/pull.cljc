(ns lab79.datomic-spec.pull
  (:require [clojure.spec :as s]
            [clojure.spec.gen :as gen]
            [lab79.clojure-spec-helpers :as csh :refer [extract-spec-keys spec->spec-keys]]))

(s/fdef keys->pull-pattern-spec
        :args (s/cat :all-keys (s/coll-of keyword? :min-count 1))
        :ret seq?)
(defn- keys->pull-pattern-spec
  "Returns the quoted clojure.spec that defines the shape of Datomic pulls that pull back data that will conform
  to `(s/keys :opt ~all-keys)"
  [all-keys]
  (let [{map-keys     true
         non-map-keys false} (->> all-keys
                                  (group-by (fn [key]
                                              (let [data (gen/generate (s/gen key))]
                                                (or (map? data)
                                                    (and (coll? data)
                                                         ; s/every with no :min-count may generate an empty coll, so
                                                         ; make sure we generate a coll with at least cardinality 1
                                                         (let [coll-with-1+ (gen/generate (s/gen (s/and key
                                                                                                        seq)))]
                                                           (map? (first coll-with-1+)))))))))]
    (if (empty? map-keys)
      `(s/coll-of ~(set non-map-keys) :min-count 1)
      `(s/coll-of
         (s/or
           :attr-name ~(set non-map-keys)
           :map-spec (s/map-of
                       ~(set map-keys)
                       (s/or ~@(->> map-keys
                                    (mapcat (fn [key]
                                              (let [pull (->> (extract-spec-keys key)
                                                              ((juxt :req :opt))
                                                              (apply concat)
                                                              keys->pull-pattern-spec)]
                                                [key pull])))))))
         :min-count 1))))

(s/fdef spec->pull-pattern-spec
        :args (s/cat :kind ::csh/spec-name))
(defn spec->pull-pattern-spec
  "Given a `spec-name`, returns the clojure.spec that defines the shape of Datomic pulls that pull back data that
  will conform to `spec-name`."
  [spec-name]
  (->> (extract-spec-keys spec-name)
       ((juxt :req :opt))
       (apply concat)
       (keys->pull-pattern-spec)
       eval))