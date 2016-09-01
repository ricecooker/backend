(ns e85th.backend.core.auth
  (:require [e85th.backend.core.models :as m]
            [e85th.backend.core.db :as db]
            [e85th.commons.ex :as ex]
            [schema.core :as s]))

(s/defn find-role-by-id :- (s/maybe m/Role)
  [{:keys [db]} role-id :- s/Int]
  (db/select-role-by-id db role-id))

(def ^{:doc "Same as find-role-by-id but throws an exception when not found"}
  find-role-by-id! (ex/wrap-not-found find-role-by-id))

(s/defn find-role-by-name :- (s/maybe m/Role)
  [{:keys [db]} role-name :- s/Str]
  (db/select-role-by-name db role-name))

(def ^{:doc "Same as find-role-by-name but throws an exception when not found"}
  find-role-by-name! (ex/wrap-not-found find-role-by-name))

(s/defn find-permission-by-id :- (s/maybe m/Permission)
  [{:keys [db]} permission-id :- s/Int]
  (db/select-permission-by-id db permission-id))

(def ^{:doc "Same as find-permission-by-id but throws an exception when not found"}
  find-permission-by-id! (ex/wrap-not-found find-permission-by-id))

(s/defn find-permission-by-name :- (s/maybe m/Permission)
  [{:keys [db]} permission-name :- s/Str]
  (db/select-permission-by-name db permission-name))

(def ^{:doc "Same as find-permission-by-name but throws an exception when not found"}
  find-permission-by-name! (ex/wrap-not-found find-permission-by-name))
