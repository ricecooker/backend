(ns e85th.backend.core.permission
  (:require [clojure.spec.alpha :as s]
            [e85th.commons.ex :as ex]
            [e85th.backend.core.db :as db]
            [clojure.java.jdbc :as jdbc]
            [e85th.commons.sql :as sql]
            [e85th.backend.core.domain :as domain]
            [clojure.set :as set]))

(def duplicate :permission.error/duplicate)
(def associated-with-roles :permission.error/role-associations-exist)

;;----------------------------------------------------------------------
(s/fdef get-all
        :args (s/cat :res map?)
        :ret  (s/coll-of ::domain/permission))

(defn get-all
  [{:keys [db]}]
  (db/select-all-permissions db))

;;----------------------------------------------------------------------
(s/fdef get-by-id
        :args (s/cat :res map? :id ::domain/id)
        :ret  (s/nilable ::domain/permission))

(defn get-by-id
  [{:keys [db]} id]
  (db/select-permission-by-id db id))

(def get-by-id! (ex/wrap-not-found get-by-id))

;;----------------------------------------------------------------------
(s/fdef get-by-name
        :args (s/cat :res map? :name ::domain/name)
        :ret  (s/nilable ::domain/permission))

(defn get-by-name
  [{:keys [db]} name]
  (db/select-permission-by-name db name))

(def get-by-name! (ex/wrap-not-found get-by-name))


;;----------------------------------------------------------------------
(s/fdef get-by-role-ids
        :args (s/cat :res map? :role-ids (s/coll-of ::domain/id))
        :ret  (s/coll-of ::domain/permission))

(defn get-by-role-ids
  [{:keys [db]} role-ids]
  (db/select-permissions-by-role-ids db role-ids))

(defn get-by-role-id
  [res role-id]
  (get-by-role-ids res [role-id]))

;;----------------------------------------------------------------------
(s/fdef create
        :args (s/cat :res map? :permission ::domain/permission :user-id ::domain/user-id)
        :ret  ::domain/id)

(defn create
  "Create a new permission record. Returns the permission ID."
  [{:keys [db] :as res} permission user-id]
  (try
    (db/insert-permission db permission user-id)
    (catch java.sql.SQLException ex
      (if (sql/unique-violation? ex)
        (throw (ex/validation :permission.error/duplicate "A permission with this name already exists."))
        (throw ex)))))

;;----------------------------------------------------------------------
(s/fdef update-by-id
        :args (s/cat :res map? :id ::domain/id :permission ::domain/permission :user-id ::domain/user-id)
        :ret  int?)

(defn update-by-id
  "Updates the permission by the db id and returns the count of rows updated."
  [{:keys [db] :as res} id permission user-id]
  (try
    (db/update-permission-by-id db id permission user-id)
    (catch java.sql.SQLException ex
      (if (sql/unique-violation? ex)
        (throw (ex/validation :permission.error/duplicate "A permission with this name already exists."))
        (throw ex)))))

(defn- validate-no-role-permission
  [{:keys [db]} id]
  (when (seq (db/select-roles-by-permission-ids db [id]))
    (throw (ex/validation associated-with-roles "This permission still has associated roles."))))

;;----------------------------------------------------------------------
(s/fdef delete-by-id
        :args (s/cat :res map? :id ::domain/id)
        :ret any?)

(defn delete-by-id
  [{:keys [db] :as res} id]
  (validate-no-role-permission res id)
  (jdbc/with-db-transaction [txn db]
    (db/delete-role-permissions-by-permission-id txn id)
    (db/delete-permission-by-id txn id)))
