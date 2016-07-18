(ns e85th.backend.user.core
  (:require [e85th.backend.user.db :as user-db]
            [e85th.backend.user.models :as m]
            [e85th.commons.tel :as tel]
            [schema.core :as s]))

(s/defn find-user-all-fields-by-id :- (s/maybe m/UserAllFields)
  "This exists to make it a little bit harder to expose the password digest."
  [{:keys [db]} id :- s/Int]
  (user-db/select-user db {:id id}))

(s/defn find-user-by-id :- (s/maybe m/User)
  "This exists to make it a little bit harder to expose the password digest."
  [res id :- s/Int]
  (some-> (find-user-all-fields-by-id res id)
          (dissoc :password-digest)))

(s/defn find-channel-by-id :- (s/maybe m/Channel)
  [{:keys [db]} id :- s/Int]
  (user-db/select-channel-by-id db id))

(s/defn find-channels-by-user-id :- [m/Channel]
  "Enumerates all channels by the user-id."
  [{:keys [db]} user-id :- s/Int]
  (user-db/select-channels-by-user-id db user-id))

(s/defn find-mobile-channel :- (s/maybe m/Channel)
  "Finds an mobile channel for the given mobile phone number."
  [{:keys [db]} mobile-nbr :- s/Str]
  (user-db/select-channel-by-type db m/mobile-channel-id (tel/normalize mobile-nbr)))

(s/defn find-email-channel :- (s/maybe m/Channel)
  "Finds an email channel for the given email address."
  [{:keys [db]} email :- s/Str]
  (user-db/select-channel-by-type db m/email-channel-id email))

(s/defn create-user :- m/User
  "Creates a user in the db and returns the User record."
  [{:keys [db publisher] :as res} user :- m/NewUser user-id :- s/Int]
  (->> (user-db/insert-user db user user-id)
       (find-user-by-id res)))

(s/defn create-channel :- m/Channel
  "Creates a new channel and returns the Channel record."
  [{:keys [db] :as res} channel :- m/NewChannel user-id :- s/Int]
  (->> (user-db/insert-channel db channel user-id)
       (find-channel-by-id res)))
