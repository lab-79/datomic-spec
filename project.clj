(defproject lab79.datomic-spec "0.1.2"
  :description "clojure.spec for Datomic"
  :url "https://github.com/lab-79/datomic-spec"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/cljc"]
  :test-paths ["test"]
  :dependencies [[org.clojure/clojure "1.9.0-alpha12"]
                 [org.clojure/test.check "0.9.0"]]
  :profiles {:dev {:dependencies [[com.datomic/datomic-free "0.9.5390"]
                                  [org.clojure/tools.namespace "0.3.0-alpha3"]]}}

  ; https://github.com/technomancy/leiningen/issues/2173
  :monkeypatch-clojure-test false

  :plugins [[lein-cloverage "1.0.7-SNAPSHOT"]])
