(ns e85th.backend.graphql
  (:require [com.walmartlabs.lacinia :as lacinia]
            [com.walmartlabs.lacinia.resolve :as resolve]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia.util :as util]
            [e85th.commons.ex :as ex]
            [e85th.commons.ext :as ext]
            [e85th.commons.util :as e85th.util]
            [taoensso.timbre :as log]
            [clojure.edn :as edn]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io])
  (:import [e85th.commons.exceptions AuthExceptionInfo ForbiddenExceptionInfo ValidationExceptionInfo NotFoundExceptionInfo]))

(defn- require-and-resolve
  "Returns the resolved var for the sym otherwise throws."
  [sym]
  ;; require the namespace if sym is namespace qualified
  (some-> sym namespace symbol require)
  (or (some-> sym resolve var-get)
      (throw (ex-info (str "Unable to resolve sym: " sym) {:sym sym}))))

(defn- kw->conformer
  [kw]
  (-> kw
      ext/keyword->symbol
      require-and-resolve
      schema/as-conformer))

(defn- as-conformers
  "m is a map of keys `:parse` and `:serialize`. Calls
  `schema/as-conformer` on the vals of `parse` and `serialize`. "
  [m]
  (-> m
      (update :parse kw->conformer)
      (update :serialize kw->conformer)))

(defn- scalar-conformers
  [scalars-map]
  (ext/map-vals as-conformers scalars-map))

(defn- file->schema
  [file]
  (-> file io/resource slurp edn/read-string))

(def allowed-symbols #{'non-null 'list})

(defn- normalize-schema
  "Turns any symbols into keywords."
  [schema-map]
  (clojure.walk/postwalk
   (fn [x]
     (cond-> x
       (and (symbol? x)
            (not (allowed-symbols x))) ext/symbol->keyword))
   schema-map))


(defn- find-resolvers-and-streamers
  "Returns a map with keys `resolvers` and `streamers`.  Each
   value is a collection of keywords indicating the fully namespace
   qualified functions."
  [schema-map]
  (let [ans {:resolvers (transient []) :streamers (transient [])}]
    (clojure.walk/postwalk
     (fn [x]
       (when (map? x)
         (let [{:keys [type resolve stream]} x
               var-kw (or resolve stream)]
           (when (and type resolve)
             (update ans :resolvers conj! resolve))
           (when (and type stream)
             (update ans :streamers conj! stream))))
       x)
     schema-map)
    (-> ans
        (update :resolvers persistent!)
        (update :streamers persistent!))))


(def ^:private kw-fn-entry
  (juxt identity (comp require-and-resolve ext/keyword->symbol)))

(defn- kw->resolved-fn-map
  "Returns a map of kw -> fn. If any kw is not resolved, throws an error."
  [kws]
  (into {} (map kw-fn-entry kws)))

(s/def ::file-input (s/or :string string?
                          :file (partial instance? java.io.File)
                          :uri uri?))

;;----------------------------------------------------------------------
;; 'Middleware' and exception handling
(defn- ex->result
  "Return a map to act as the result for the exception encountered
   See http://lacinia.readthedocs.io/en/latest/resolve/resolve-as.html."
  [ex category]
  (let [[kind msg data] (ex/error-tuple ex)]
    {:message  msg
     :category category
     :kind     kind
     :data     data}))

(defn wrap-exception-handling
  [f]
  (fn [ctx args resolved-val]
    (try
      (f ctx args resolved-val)
      (catch AuthExceptionInfo ex
        (resolve/resolve-as resolved-val (ex->result ex :authentication)))
      (catch NotFoundExceptionInfo ex
        (resolve/resolve-as resolved-val (ex->result ex :authorization)))
      (catch ForbiddenExceptionInfo ex
        (resolve/resolve-as resolved-val (ex->result ex :authorization)))
      (catch ValidationExceptionInfo ex
        (resolve/resolve-as resolved-val (ex->result ex :validation)))
      (catch Throwable t
        (let [uuid (ext/random-uuid)]
          (e85th.util/log-throwable t uuid)
          (resolve/resolve-as resolved-val {:message "Unexpected server error."
                                            :category :unexpected
                                            :kind     :unexpected
                                            :data {:uuid uuid}}))))))


(defn wrap-conform-response
  "Unqualifies and camel cases any keys to conform to graphql identifiers."
  [f]
  (fn [ctx args resolved-val]
    (ext/camel-case-keys (f ctx args resolved-val))))


(defn wrap-conform-args
  "Lisp/kebab case keys to conform to clojure idioms."
  [f]
  (fn [ctx args resolved-val]
    (f ctx (ext/lisp-case-keys args) resolved-val)))


(def wrap-standard-middleware
  "Wraps a graphql resolver function in the standard middleware which
   conforms keys to graphql/clojure expectations and
   handle exceptions since all requests must return a 200."
  (comp wrap-exception-handling wrap-conform-response wrap-conform-args))


;;----------------------------------------------------------------------
(s/fdef file->compiled-schema
        :args (s/cat :file ::file-input
                     :middleware-fn (s/? fn?))
        :ret map?)

(defn file->compiled-schema
  "Takes in an edn file `file` and parses returns a compiled lacinia schema.
   Read file, convert any symbols to keywords since that's what lacinia works with,
   make scalar parse and serialize functions act as conformers, and
   attach resolvers and streamers. The input file will have fully qualified function names
   represented as keywords/symbols for resolvers, streamers, parse and serialize functions.
   modifier-fn is a function which is invoked for each streamer and resolver to allow any
   custom processing (ie wire up middleware) etc."
  ([file]
   (file->compiled-schema file wrap-standard-middleware))
  ([file modifier-fn]
   (let [{:keys [scalars] :as schema-map} (-> file file->schema normalize-schema)
         {:keys [resolvers streamers]} (find-resolvers-and-streamers schema-map)
         resolvers (ext/map-vals modifier-fn (kw->resolved-fn-map resolvers))
         streamers (ext/map-vals modifier-fn (kw->resolved-fn-map streamers))]
     (cond-> schema-map
       (seq scalars)   (update :scalars scalar-conformers)
       (seq resolvers) (util/attach-resolvers resolvers)
       (seq streamers) (util/attach-streamers streamers)
       true            (schema/compile {:default-field-resolver schema/hyphenating-default-field-resolver})))))
