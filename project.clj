(defproject backend "0.1.0-SNAPSHOT"
  :description "Backend server.  Provides endpoints."
  :url "https://github.com/e85th/backend"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :main ^:skip-aot backend.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
