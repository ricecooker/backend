(defproject e85th/backend "0.1.28"
  :description "Backend server code."
  :url "https://github.com/e85th/backend"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha15" :scope "provided"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [e85th/commons "0.1.19"]
                 [org.clojure/core.async "0.3.442"] ;; override sente version for spec ns
                 [com.taoensso/sente "1.11.0"] ; websockets
                 [com.layerware/hugsql "0.4.6"]
                 [metosin/compojure-api "1.1.10"]
                 [metosin/ring-http-response "0.8.1"]
                 [ring-cors "0.1.7" :exclusions [ring/ring-core]]
                 [org.immutant/web "2.1.5" :exclusions [ring/ring-core]]
                 [http-kit "2.2.0"]
                 [buddy "1.0.0"]
                 [com.google.api-client/google-api-client "1.22.0"]
                 [com.google.firebase/firebase-server-sdk "3.0.1"]
                 [org.clojure/tools.nrepl "0.2.12"]]


  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]

  ;; only to quell lein-cljsbuild when using checkouts
  :cljsbuild {:builds []}

  :profiles {:dev  [:project/dev  :profiles/dev]
             :test [:project/test :profiles/test]
             :profiles/dev  {}
             :profiles/test {}
             :project/dev   {:dependencies [[reloaded.repl "0.2.2"]
                                            [org.clojure/tools.namespace "0.2.11"]
                                            [org.clojure/tools.nrepl "0.2.12"]
                                            [eftest "0.1.1"]
                                            [e85th/test "0.1.0"]]
                             :source-paths   ["dev/src"]
                             :resource-paths ["dev/resources"]
                             :repl-options {:init-ns user}
                             :env {:port "7000"}}
             :project/test  {}}

  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]])
