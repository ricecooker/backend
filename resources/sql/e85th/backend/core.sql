-- :name select-user :? :1
  select
         u.id
       , u.first_name      as "first-name"
       , u.last_name       as "last-name"
       , u.password_digest as "password-digest"
    from "user" u
   where u.id = :id

-- :name select-channels
   select
          c.id
        , c.user_id          as "user-id"
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

-- :name select-user-auth
   select
          'role' as kind
        , r.name
     from user_role ur
     join "role"    r
       on ur.role_id = r.id
    where ur.user_id = :user-id
union all
   select 'permission' as kind
        , p.name
     from user_role ur
     join role_permission rp
       on ur.role_id = rp.role_id
     join permission p
       on rp.permission_id = p.id
    where ur.user_id = :user-id


-- :name select-role
   select
          r.id
        , r.name
        , r.description
     from "role" r
    where (:id-nil? or r.id = :id)
      and (:name-nil? or r.name = :name)

-- :name select-permission
   select
          p.id
        , p.name
        , p.description
     from permission p
    where (:id-nil? or p.id = :id)
      and (:name-nil? or p.name = :name)