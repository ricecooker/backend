(defproject e85th/backend "0.1.0-SNAPSHOT"
  :description "Backend server code."
  :url "https://github.com/e85th/backend"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [e85th/commons "0.1.0-SNAPSHOT"]

                 [org.postgresql/postgresql "9.4.1208"]
                 [metosin/compojure-api "1.1.4"]
                 [metosin/ring-http-response "0.8.0"]
                 [ring-cors "0.1.7" :exclusions [ring/ring-core]]
                 [org.immutant/web "2.1.5"]
                 [org.clojure/tools.nrepl "0.2.12"]

                 ]

  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]

  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]]
  )
