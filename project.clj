(defproject e85th/backend "0.1.0"
  :description "Backend server code."
  :url "https://github.com/e85th/backend"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [e85th/commons "0.1.0"]
                 [com.layerware/hugsql "0.4.6"]
                 [metosin/compojure-api "1.1.4"]
                 [metosin/ring-http-response "0.8.0"]
                 [ring-cors "0.1.7" :exclusions [ring/ring-core]]
                 [org.immutant/web "2.1.5" :exclusions [ring/ring-core]]
                 [http-kit "2.2.0"]
                 [buddy "1.0.0"]
                 [org.clojure/tools.nrepl "0.2.12"]]


  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]

  :profiles {:dev  [:project/dev  :profiles/dev]
             :test [:project/test :profiles/test]
             :profiles/dev  {}
             :profiles/test {}
             :project/dev   {:dependencies [[reloaded.repl "0.2.2"]
                                            [org.clojure/tools.namespace "0.2.11"]
                                            [org.clojure/tools.nrepl "0.2.12"]
                                            [eftest "0.1.1"]
                                            [e85th/test "0.1.0-SNAPSHOT"]]
                             :source-paths   ["dev/src"]
                             :resource-paths ["dev/resources"]
                             :repl-options {:init-ns user}
                             :env {:port "7000"}}
             :project/test  {}}

  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]])
