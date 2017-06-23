(set-env!
 :resource-paths #{"src/clj" "resources"}
 :dependencies '[[org.clojure/clojure "1.9.0-alpha15" :scope "provided"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [e85th/commons "0.1.23"]
                 [com.taoensso/timbre "4.10.0"] ; explicitly include
                 [org.clojure/core.async "0.3.443"] ;; override sente version for spec ns
                 [com.taoensso/sente "1.11.0"] ; websockets
                 [com.layerware/hugsql "0.4.7"]
                 [metosin/compojure-api "1.1.10"]
                 [metosin/ring-http-response "0.9.0"]
                 [ring-cors "0.1.10" :exclusions [ring/ring-core]]
                 [org.immutant/web "2.1.8" :exclusions [ring/ring-core]]
                 [http-kit "2.2.0"]
                 [buddy "1.3.0"]
                 [com.google.api-client/google-api-client "1.22.0" :scope "provided"]
                 [com.google.firebase/firebase-server-sdk "3.0.3" :scope "provided"]
                 [org.clojure/tools.nrepl "0.2.13" :scope "provided"]

                 [adzerk/boot-test "1.2.0" :scope "test"]]

 :repositories #(conj %
                      ["clojars" {:url "https://clojars.org/repo"
                                  :username (System/getenv "CLOJARS_USER")
                                  :password (System/getenv "CLOJARS_PASS")}]))

(require '[adzerk.boot-test :as boot-test])

(deftask test
  "Runs the unit-test task"
  []
  (comp
   (javac)
   (boot-test/test)))


(deftask build
  "Builds a jar for deployment."
  []
  (comp
   (javac)
   (pom)
   (jar)
   (target)))

(deftask dev
  "Starts the dev task."
  []
  (comp
   (repl)
   (watch)))

(deftask deploy
  []
  (comp
   (build)
   (push)))

(task-options!
 pom {:project 'e85th/backend
      :version "0.1.33"
      :description "Backend server code."
      :url "https://github.com/e85th/backend"
      :scm {:url "https://github.com/e85th/backend"}
      :license {"Apache License 2.0" "http://www.apache.org/licenses/LICENSE-2.0"}}
 push {:repo "clojars"})
