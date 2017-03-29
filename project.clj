(defproject lab79/datomic-spec "0.2.0-alpha7"
  :description "clojure.spec for Datomic"
  :url "https://github.com/lab-79/datomic-spec"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/cljc"]
  :test-paths ["test"]
  :dependencies [[org.clojure/clojure "1.9.0-alpha15"]
                 [org.clojure/clojurescript "1.9.494"]
                 [org.clojure/test.check "0.9.0"]
                 [lab79/clojure-spec-helpers "0.1.0-alpha8"]]
  :profiles {:dev {:dependencies [[com.datomic/datomic-free "0.9.5561"]
                                  [org.clojure/tools.namespace "0.3.0-alpha3"]]}}

  ; https://github.com/technomancy/leiningen/issues/2173
  :monkeypatch-clojure-test false

  :plugins [[lein-cloverage "1.0.7-SNAPSHOT"]])
