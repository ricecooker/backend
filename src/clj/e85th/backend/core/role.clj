(ns e85th.backend.core.role
  (:require [clojure.spec.alpha :as s]
            [e85th.commons.ex :as ex]
            [e85th.backend.core.db :as db]
            [e85th.backend.core.permission :as permission]
            [e85th.commons.sql :as sql]
            [clojure.java.jdbc :as jdbc]
            [e85th.backend.core.domain :as domain]
            [clojure.set :as set]))


(def duplicate :role.error/duplicate)
(def associated-with-users :role.error/user-associations-exist)

;;----------------------------------------------------------------------
(s/fdef get-all
        :args (s/cat :res map?)
        :ret  (s/coll-of ::domain/role))

(defn get-all
  [{:keys [db]}]
  (db/select-all-roles db))

;;----------------------------------------------------------------------
(s/fdef get-by-id
        :args (s/cat :res map? :id ::domain/id)
        :ret  (s/nilable ::domain/role))

(defn get-by-id
  [{:keys [db]} id]
  (db/select-role-by-id db id))

(def get-by-id! (ex/wrap-not-found get-by-id))

;;----------------------------------------------------------------------
(s/fdef get-by-name
        :args (s/cat :res map? :name ::domain/name)
        :ret  (s/nilable ::domain/role))

(defn get-by-name
  [{:keys [db]} name]
  (db/select-role-by-name db name))

(def get-by-name! (ex/wrap-not-found get-by-name))


;;----------------------------------------------------------------------
(s/fdef create
        :args (s/cat :res map? :role ::domain/role :user-id ::domain/user-id)
        :ret  ::domain/id)

(defn create
  "Create a new role record. Returns the role ID. Can throw ValidationExceptionInfo with :role.error/duplicate "
  [{:keys [db] :as res} role user-id]
  (try
    (db/insert-role db role user-id)
    (catch java.sql.SQLException ex
      (if (sql/unique-violation? ex)
        (throw (ex/validation duplicate "A role with that name exists already."))
        (throw ex)))))

;;----------------------------------------------------------------------
(s/fdef update-by-id
        :args (s/cat :res map? :id ::domain/id :role ::domain/role :user-id ::domain/user-id)
        :ret  int?)

(defn update-by-id
  "Updates the role by the db id and returns the count of rows updated."
  [{:keys [db] :as res} id role user-id]
  (try
    (db/update-role-by-id db id role user-id)
    (catch java.sql.SQLException ex
      (if (sql/unique-violation? ex)
        (throw (ex/validation duplicate "A role with that name exists already."))
        (throw ex)))))

(defn validate-no-user-role
  [{:keys [db]} role-id]
  (when (seq (db/select-user-ids-with-role-ids db [role-id]))
    (throw (ex/validation associated-with-users "This role still has associated users."))))

;;----------------------------------------------------------------------
(s/fdef delete-by-id
        :args (s/cat :res map? :id ::domain/id)
        :ret any?)

(defn delete-by-id
  "Deletes role permission mappings and then the role itself only if no users are associated with the role."
  [{:keys [db] :as res} id]
  (validate-no-user-role res id)
  (jdbc/with-db-transaction [txn db]
    (db/delete-role-permissions-by-role-id txn id)
    (db/delete-role-by-id txn id)))


;;----------------------------------------------------------------------
(s/fdef assoc-permissions
        :args (s/cat :res map? :role-id ::domain/id :permission-ids (s/coll-of ::domain/id) :user-id ::domain/user-id)
        :ret any?)

(defn assoc-permissions
  [{:keys [db]} role-id permission-ids user-id]
  (when (seq permission-ids)
    (db/insert-role-permissions db role-id (set permission-ids) user-id)))

;;----------------------------------------------------------------------
(s/fdef dissoc-permissions
        :args (s/cat :res map? :role-id ::domain/id :permission-ids (s/coll-of ::domain/id) :user-id ::domain/user-id)
        :ret any?)

(defn dissoc-permissions
  [{:keys [db]} role-id permission-ids user-id]
  (db/delete-role-permissions db role-id (set permission-ids)))


;;----------------------------------------------------------------------
(s/fdef create-with-permissions
        :args (s/cat :res map? :role ::domain/role :permission-ids (s/coll-of ::domain/id) :user-id ::domain/user-id)
        :ret ::domain/id)

(defn create-with-permissions
  "Creates a role with associated permissions. Returns the role's db id."
  [{:keys [db] :as res} role permission-ids user-id]
  (jdbc/with-db-transaction [txn db]
    (let [res (assoc res :db txn)
          role-id (create res role user-id)]
      (assoc-permissions res role-id permission-ids user-id)
      role-id)))

;;----------------------------------------------------------------------
(s/fdef update-with-permissions
        :args (s/cat :res map? :role-id ::domain/id :role ::domain/role :permission-ids (s/coll-of ::domain/id) :user-id ::domain/user-id)
        :ret any?)

(defn update-with-permissions
  [{:keys [db] :as res} role-id role permission-ids user-id]
  (let [permission-ids (set permission-ids)
        cur-perms (set (map :id (permission/get-by-role-ids res [role-id])))
        rm-perms (set/difference cur-perms permission-ids)
        add-perms (set/difference permission-ids cur-perms)]
    (jdbc/with-db-transaction [txn db]
      (let [res (assoc res :db txn)]
        (update-by-id res role-id role user-id)
        (when (seq rm-perms)
          (dissoc-permissions res role-id rm-perms user-id))
        (when (seq add-perms)
          (assoc-permissions res role-id add-perms user-id))))))
