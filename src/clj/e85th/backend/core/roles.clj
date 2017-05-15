(ns e85th.backend.core.roles
  (:require [schema.core :as s]
            [e85th.commons.ex :as ex]
            [e85th.backend.core.models :as m]
            [e85th.backend.core.db :as db]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]
            [clojure.set :as set]))

(def permission-duplicate ::permission-duplicate)
(def role-duplicate ::role-duplicate)
(def role-associated-with-users ::role-associated-with-users)
(def permission-associated-with-roles ::permission-associated-with-roles)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Permissions
(s/defn find-all-permissions :- [m/Permission]
  [{:keys [db]}]
  (db/select-all-permissions db))

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

(s/defn find-permissions-by-role-ids :- [m/Permission]
  "Returns a unique set of Permissions."
  [{:keys [db]} role-ids :- [s/Int]]
  (db/select-permissions-by-role-ids db role-ids))

(s/defn create-permission :- s/Int
  [{:keys [db]} permission :- m/Permission user-id :- s/Int]
  (db/insert-permission db permission user-id))

(s/defn update-permission :- s/Int
  [{:keys [db]} permission :- m/UpdatePermission user-id :- s/Int]
  (db/update-permission db (:id permission) permission user-id))


(defn validate-no-role-permission
  [{:keys [db]} permission-id]
  (when (seq (db/select-roles-by-permission-ids db [permission-id]))
    (throw (ex/validation permission-associated-with-roles))))

(s/defn delete-permission
  [{:keys [db] :as res} permission-id]
  (validate-no-role-permission res permission-id)
  (jdbc/with-db-transaction [txn db]
    (db/delete-role-permissions-by-permission-id txn permission-id)
    (db/delete-permission txn permission-id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Roles
(s/defn find-all-roles :- [m/Role]
  [{:keys [db]}]
  (db/select-all-roles db))

(s/defn find-role-by-id :- (s/maybe m/Role)
  [{:keys [db]} role-id :- s/Int]
  (db/select-role-by-id db role-id))

(def ^{:doc "Same as find-role-by-id but throws an exception when not found"}
  find-role-by-id! (ex/wrap-not-found find-role-by-id))

(s/defn find-role-by-name :- (s/maybe m/Role)
  [{:keys [db]} role-name :- (s/either s/Str s/Keyword)]
  (let [kw->str #(string/replace-first (str %) ":" "")
        role-name (cond->> role-name
                    (keyword? role-name) kw->str)]
    (db/select-role-by-name db role-name)))

(def ^{:doc "Same as find-role-by-name but throws an exception when not found"}
  find-role-by-name! (ex/wrap-not-found find-role-by-name))

(s/defn create-role :- s/Int
  [{:keys [db]} role :- m/Role user-id :- s/Int]
  (db/insert-role db role user-id))

(s/defn update-role :- s/Int
  [{:keys [db]} role :- m/UpdateRole user-id :- s/Int]
  (db/update-role db (:id role) role user-id))

(defn validate-no-user-role
  [{:keys [db]} role-id]
  (when (seq (db/select-users-by-roles db [role-id]))
    (throw (ex/validation role-associated-with-users))))

(s/defn delete-role
  "Deletes role permission mappings and then the role itself only if no users are associated with the role."
  [{:keys [db] :as res} role-id]
  (validate-no-user-role res role-id)
  (jdbc/with-db-transaction [txn db]
    (db/delete-role-permissions-by-role-id txn role-id)
    (db/delete-role txn role-id)))


(s/defn add-role-permissions
  [{:keys [db]} role-id :- s/Int permission-ids :- (s/either [s/Int] #{s/Int}) editor-role-id :- s/Int]
  (db/insert-role-permissions db role-id (set permission-ids) editor-role-id))

(s/defn delete-role-permissions
  [{:keys [db]} role-id :- s/Int permission-ids :- (s/either [s/Int] #{s/Int}) editor-role-id :- s/Int]
  (db/delete-role-permissions db role-id (set permission-ids)))


(s/defn find-role-info
  [res id]
  {:role (find-role-by-id! res id)
   :permissions (find-permissions-by-role-ids res [id])})

(s/defn find-role-permissions :- #{s/Int}
  [res role-id]
  (->> (find-permissions-by-role-ids res [role-id])
       (map :id)
       set))

(s/defn create-role-with-permissions
  [{:keys [db] :as res} role :- m/RoleWithPermissions user-id :- s/Int]
  (jdbc/with-db-transaction [txn db]
    (let [res (assoc res :db txn)
          role-id (create-role res (dissoc role :permissions) user-id)]
      (when-let [perms (:permissions role)]
        (add-role-permissions res role-id perms user-id)))))

(s/defn update-role-with-permissions
  [{:keys [db] :as res} role :- m/RoleWithPermissions user-id :- s/Int]
  (let [role-id (:id role)
        perms (set (:permissions role))
        cur-perms (find-role-permissions res role-id)
        rm-perms (set/difference cur-perms perms)
        add-perms (set/difference perms cur-perms)]
    (jdbc/with-db-transaction [txn db]
      (let [res (assoc res :db txn)]
        (update-role res (dissoc role :permissions) user-id)
        (when (seq rm-perms)
          (delete-role-permissions res role-id rm-perms user-id))
        (when (seq add-perms)
          (add-role-permissions res role-id add-perms user-id))))))

(s/defn save-role-with-permissions
  [res role :- m/RoleWithPermissions user-id :- s/Int]
  (if (:id role)
    (update-role-with-permissions res role user-id)
    (create-role-with-permissions res role user-id)))
