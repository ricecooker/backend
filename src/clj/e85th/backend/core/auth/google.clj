(ns e85th.backend.core.auth.google
  (:require [e85th.backend.core.user :as user]
            [e85th.backend.core.channel :as channel]
            [e85th.backend.libs.google-oauth :as google-oauth]
            [e85th.commons.ex :as ex]))

;; (defn authenticate
;;   "jwt is a token as a string"
;;   [res jwt]
;;   (let [auth-ex-fn (fn [ex]
;;                      (throw
;;                       (ex/auth :google/auth-failed "Google Auth Failed" {} ex)))
;;         {:keys [email]} (google-oauth/verify-token jwt)]
;;     (try
;;       (let [{:keys [email]} (google-oauth/verify-token jwt)]
;;         (assert email "We don't handle anonymous logins.")
;;         (if-let [{:keys [user-id]} (channel/get-email-channel res email)]
;;           (user/user-id->auth-response res user-id)
;;           (throw (ex/auth :user/no-such-user "No such user."))))
;;       (catch Exception ex
;;         (throw
;;          (ex/auth :google/auth-failed "Google Auth Failed" {} ex))))))
