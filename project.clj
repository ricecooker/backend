(defproject e85th/backend "0.1.39-alpha8"
  :description "Backend server code."
  :url "https://github.com/e85th/backend"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [e85th/commons "0.1.29-alpha7"]
                 [com.taoensso/timbre "4.10.0"] ; explicitly include
                 [org.clojure/core.async "0.3.443"] ;; override sente version for spec ns
                 [io.pedestal/pedestal.service       "0.5.3" :scope "provided"]
                 [com.walmartlabs/lacinia "0.22.1" :exclusions [org.clojure/clojure]]
                 [com.taoensso/sente "1.11.0"] ; websockets
                 [com.layerware/hugsql "0.4.8"]
                 [buddy "2.0.0"]
                 [com.google.api-client/google-api-client "1.23.0" :scope "provided"]
                 [com.google.firebase/firebase-server-sdk "3.0.3" :scope "provided"]
                 [org.clojure/tools.nrepl "0.2.13" :scope "provided"]]


  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]

  ;; only to quell lein-cljsbuild when using checkouts
  :cljsbuild {:builds []}

  :profiles {:dev  [:project/dev  :profiles/dev]
             :test [:project/test :profiles/test]
             :profiles/dev  {}
             :profiles/test {}
             :project/dev   {:dependencies [[reloaded.repl "0.2.3"]
                                            [org.clojure/tools.namespace "0.2.11"]
                                            [org.clojure/tools.nrepl "0.2.13"]
                                            [e85th/test "0.1.7"]]
                             :source-paths   ["dev/src"]
                             :resource-paths ["dev/resources"]
                             :repl-options {:init-ns user}
                             :env {:port "7000"}}
             :project/test  {}}
  :deploy-repositories [["releases"  {:sign-releases false :url "https://clojars.org/repo"}]
                        ["snapshots" {:sign-releases false :url "https://clojars.org/repo"}]])
