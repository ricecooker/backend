(ns e85th.backend.core.domain
  (:require [clojure.spec.alpha :as s]))

(defn datetime?
  [x]
  (instance? org.joda.time.DateTime x))

(s/def ::id uuid?)

(s/def ::name        string?)
(s/def ::description string?)

(s/def ::first-name      string?)
(s/def ::last-name       string?)
(s/def ::password        string?)
(s/def ::password-digest string?)

(s/def ::user-id           ::id)
(s/def ::channel-type-id   ::id)
(s/def ::channel-type-name string?)
(s/def ::identifier        string?)
(s/def ::token             (s/nilable string?))
(s/def ::token-expiration  (s/nilable datetime?))
(s/def ::verified-at       (s/nilable datetime?))

(s/def ::message-body string?)

(s/def ::street-number (s/nilable string?))
(s/def ::street        (s/nilable string?))
(s/def ::unit          (s/nilable string?))
(s/def ::city          string?)
(s/def ::state         string?)
(s/def ::postal-code   string?)
(s/def ::lat           (s/nilable number?))
(s/def ::lng           (s/nilable number?))



;;----------------------------------------------------------------------
;; Channel
;;----------------------------------------------------------------------
(s/def ::channel  (s/keys :req-un [::id ::user-id ::channel-type-id ::identifier]
                          :opt-un [::channel-type-name ::token ::token-expiration ::verified-at]))

(s/def ::channel-identifier (s/keys :req-un [::identifier ::channel-type-id]))
(s/def ::channel-identifiers (s/coll-of ::channel-identifier))

;;----------------------------------------------------------------------
;; Address
;;----------------------------------------------------------------------
(s/def ::address (s/keys :req-un [::id ::city ::state ::postal-code]
                         :opt-un [::street-number ::street ::unit ::lat ::lng]))

;;----------------------------------------------------------------------
;; RBAC
;;----------------------------------------------------------------------
(s/def :permission/name keyword?)
(s/def ::permission  (s/keys :req-un [::id :permission/name ::description]))

(s/def :role/name keyword?)
(s/def ::role  (s/keys :req-un [::id :role/name ::description]))


;;----------------------------------------------------------------------
;; User
;;----------------------------------------------------------------------
(s/def ::user (s/keys :req-un [::id ::first-name ::last-name]
                      :opt-un [::password-digest]))

(s/def ::role-ids (s/coll-of ::id))
(s/def ::new-user (s/keys :req-un [::first-name ::last-name ::channel-identifiers ::role-ids]
                          :opt-un [::password ::address]))

;;----------------------------------------------------------------------
;; Auth
;;----------------------------------------------------------------------
(s/def ::one-time-pass-request (s/keys :req-un [::token ::token-expiration ::identifier ::message-body]))

(s/def ::permission-names (s/coll-of keyword?))
(s/def ::role-names (s/coll-of keyword?))
(s/def ::role-and-permission-names (s/keys :req-un [::permission-names ::role-names]))
