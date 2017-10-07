(ns e85th.backend.core.auth.firebase
  (:require [e85th.backend.core.user :as user]
            [e85th.backend.libs.firebase :as firebase]
            [e85th.commons.ex :as ex]))

(defmethod user/authenticate :with-firebase
  [res {:keys [with-firebase]}]
  (let [jwt (:token with-firebase)
        auth-ex-fn (fn [ex]
                     (throw
                      (ex/auth :firebase/auth-failed "Firebase Auth Failed" {} ex)))
        {:keys [email]} (firebase/verify-id-token! jwt auth-ex-fn)]

    (assert email "We don't handle anonymous logins a la firebase.")

    (if-let [{:keys [user-id]} (user/find-email-channel res email)]
      (user/user-id->auth-response res user-id)
      (throw (ex/auth :user/no-such-user "No such user.")))))
