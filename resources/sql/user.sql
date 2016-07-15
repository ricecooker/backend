-- :name select-user :? :1
  select
         u.first_name      as "first-name"
       , u.last_name       as "last-name"
       , u.password_digest as "password-digest"
    from "user" u
   where u.id = :id

-- :name select-channels
   select
          c.user_id          as "user-id"
        , c.channel_type_id  as "channel-type-id"
        , c.identifier
        , c.token
        , c.token_expiration as "token-expiration"
        , c.verified_at      as "verified-at"
     from channel      c
    where (:id-nil? or c.id = :id)
      and (:user-id-nil? or c.user_id = :user-id)
      and (:channel-type-id-nil? or c.channel_type_id = :channel-type-id)
      and (:identifier-nil? or c.identifier = :identifier)
      and (:token-nil? or c.token = :token)
      and (:token-expiration-nil? or c.token_expiration < :token-expiration)
      and (:verified-at-nil? or c.verified_at > :verified-at)
