(ns e85th.backend.core.routes
  (:require [ring.util.http-response :as http-response]
            [e85th.backend.core.user :as user]
            [e85th.backend.core.models :as m]
            [compojure.api.sweet :refer [defroutes context POST GET PUT]]
            [e85th.backend.web]
            [schema.core :as s]
            [taoensso.timbre :as log]))

(defroutes user-routes
  (context "/v1/user" [] :tags ["user"]
    :components [res]

    (GET "/:id" []
      :return m/User
      :path-params [id :- s/Int]
      :exists [found (user/find-user-by-id res id)]
      (http-response/ok found))

    (POST "/actions/new-user" []
      :body [new-user m/NewUser]
      (http-response/ok (user/create-new-user res new-user)))))
