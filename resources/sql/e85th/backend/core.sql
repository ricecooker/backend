-- :name select-user :? :1
  select
         u.id
       , u.first_name      as "first-name"
       , u.last_name       as "last-name"
       , u.password_digest as "password-digest"
    from "user" u
   where (:id-nil? or u.id = :id)

-- :name select-users-by-ids
  select
         u.id
       , u.first_name      as "first-name"
       , u.last_name       as "last-name"
    from "user" u
   where u.id in (:v*:ids)


-- :name select-channels
   select
          c.id
        , c.user_id          as "user-id"
        , c.channel_type_id  as "channel-type-id"
        , ct.name            as "channel-type-name"
        , c.identifier
        , c.token
        , c.token_expiration as "token-expiration"
        , c.verified_at      as "verified-at"
     from channel      c
     join channel_type ct
       on c.channel_type_id = ct.id
    where (:id-nil? or c.id = :id)
      and (:user-id-nil? or c.user_id = :user-id)
      and (:channel-type-id-nil? or c.channel_type_id = :channel-type-id)
      and (:identifier-nil? or c.identifier = :identifier)
      and (:token-nil? or c.token = :token)
      and (:token-expiration-nil? or c.token_expiration > :token-expiration)
      and (:verified-at-nil? or c.verified_at > :verified-at)

-- :name select-user-roles
   select
          r.id
        , r.name
        , r.description
     from user_role ur
     join "role"    r
       on ur.role_id = r.id
    where (:user-id-nil? or ur.user_id = :user-id)

-- :name select-user-permissions
   select distinct
          p.id
        , p.name
        , p.description
     from user_role ur
     join role_permission rp
       on ur.role_id = rp.role_id
     join permission p
       on rp.permission_id = p.id
    where (:user-id-nil? or ur.user_id = :user-id)


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

-- :name select-permissions-by-roles
   select
          distinct
          p.id
        , p.name
        , p.description
     from role r
     join role_permission rp
       on r.id = rp.role_id
     join permission p
       on rp.permission_id = p.id
    where true
--~ (when (seq (:role-ids params))        "and r.id in (:v*:role-ids)")

-- :name select-roles-by-permissions
   select
          r.id
        , r.name
        , r.description
     from role r
     join role_permission rp
       on r.id = rp.role_id
    where rp.permission_id in (:v*:permission-ids)

-- :name select-address
   select
          a.id
        , a.street_number as "street-number"
        , a.street
        , a.unit
        , a.city
        , a.state
        , a.postal_code as "postal-code"
        , a.lat
        , a.lng
     from address a
    where (:id-nil? or a.id = :id)

-- :name select-user-address
   select
          ua.id
        , ua.user_id     as "user-id"
        , ua.address_id  as "address-id"
     from user_address ua
    where ua.user_id = :user-id

-- :name select-users-with-roles
   select
           ur.id
         , ur.user_id    as "user-id"
         , ur.role_id    as "role-id"
     from user_role ur
    where ur.role_id in (:v*:role-ids)


-- :name select-user-role-and-permission-names
   select
          'role' as kind
        , r.name
     from user_role ur
     join role      r
       on ur.role_id = r.id
    where ur.user_id = :user-id
union all
   select
          'permission' as kind
        , p.name
     from user_role       ur
     join role_permission rp
       on ur.role_id = rp.role_id
     join permission      p
       on rp.permission_id = p.id
    where ur.user_id = :user-id
