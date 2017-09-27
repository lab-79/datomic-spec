(defproject lab79/datomic-spec "1.1.1-SNAPSHOT"
  :description "clojure.spec for Datomic"
  :url "https://github.com/lab-79/datomic-spec"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/cljc"]
  :test-paths ["test"]
  :dependencies [[org.clojure/clojure "1.9.0-beta1"]
                 [org.clojure/clojurescript "1.9.908"]
                 [org.clojure/test.check "0.9.0"]
                 [lab79/clojure-spec-helpers "1.1.1"]]
  :profiles {:dev {:dependencies [[com.datomic/datomic-free "0.9.5561.54"]
                                  [org.clojure/tools.namespace "0.3.0-alpha4"]]}}

  ; https://github.com/technomancy/leiningen/issues/2173
  :monkeypatch-clojure-test false

  :plugins [[lein-cloverage "1.0.7-SNAPSHOT"]])
