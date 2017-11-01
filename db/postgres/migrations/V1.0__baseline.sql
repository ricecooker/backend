drop role if exists ${roles.primary};
create role ${roles.primary};
create extension if not exists "pgcrypto";

create schema audit authorization ${users.superuser.name};

create table audit.user (
    op              char(1)
  , ts              timestamp
  , username        text
  , id              uuid
  , first_name      text
  , last_name       text
  , password_digest text
  , created_at      timestamp
  , created_by      uuid
  , updated_at      timestamp
  , updated_by      uuid
);

create or replace function audit.user_audit() returns trigger as $$
  begin
    if (TG_OP = 'DELETE') then
      insert into audit.user select 'D', now(), user, old.*;
      return old;
    elsif (TG_OP = 'UPDATE') then
      insert into audit.user select 'U', now(), user, new.*;
      return new;
    elsif (TG_OP = 'INSERT') then
      insert into audit.user select 'I', now(), user, new.*;
      return new;
    end if;
    return null;
  end;
$$
language plpgsql;

create table audit.channel (
    op               char(1)
  , ts               timestamp
  , username         text
  , id               uuid
  , user_id          uuid
  , channel_type_id  uuid
  , identifier       text
  , token            text
  , token_expiration timestamp
  , verified_at      timestamp
  , created_at       timestamp
  , created_by       uuid
  , updated_at       timestamp
  , updated_by       uuid
);

create or replace function audit.channel_audit() returns trigger as $$
  begin
    if (TG_OP = 'DELETE') then
      insert into audit.channel select 'D', now(), user, old.*;
      return old;
    elsif (TG_OP = 'UPDATE') then
      insert into audit.channel select 'U', now(), user, new.*;
      return new;
    elsif (TG_OP = 'INSERT') then
      insert into audit.channel select 'I', now(), user, new.*;
      return new;
    end if;
    return null;
  end;
$$
language plpgsql;

create table audit.address (
   op            char(1)
 , ts            timestamp
 , username      text
 , id            uuid
 , street_number text
 , street        text
 , unit          text
 , city          text
 , state         text
 , postal_code   text
 , lat           decimal(9,6)
 , lng           decimal(9,6)
 , created_at    timestamp
 , created_by    uuid
 , updated_at    timestamp
 , updated_by  uuid
);

create or replace function audit.address_audit() returns trigger as $$
  begin
    if (TG_OP = 'DELETE') then
      insert into audit.address select 'D', now(), user, old.*;
      return old;
    elsif (TG_OP = 'UPDATE') then
      insert into audit.address select 'U', now(), user, new.*;
      return new;
    elsif (TG_OP = 'INSERT') then
      insert into audit.address select 'I', now(), user, new.*;
      return new;
    end if;
    return null;
  end;
$$
language plpgsql;


create table audit.user_address (
    op         char(1)
  , ts         timestamp
  , username   text
  , id         uuid
  , user_id    uuid
  , address_id uuid
  , created_at timestamp
  , created_by uuid
);

create or replace function audit.user_address_audit() returns trigger as $$
  begin
    if (TG_OP = 'DELETE') then
      insert into audit.user_address select 'D', now(), user, old.*;
      return old;
    elsif (TG_OP = 'UPDATE') then
      insert into audit.user_address select 'U', now(), user, new.*;
      return new;
    elsif (TG_OP = 'INSERT') then
      insert into audit.user_address select 'I', now(), user, new.*;
      return new;
    end if;
    return null;
  end;
$$
language plpgsql;

--
-- Auth
--
create table audit.permission (
    op          char(1)
  , ts          timestamp
  , username    text
  , id          uuid
  , name        text
  , description text
  , created_at  timestamp
  , created_by  uuid
  , updated_at  timestamp
  , updated_by  uuid
);

create or replace function audit.permission_audit() returns trigger as $$
  begin
    if (TG_OP = 'DELETE') then
      insert into audit.permission select 'D', now(), user, old.*;
      return old;
    elsif (TG_OP = 'UPDATE') then
      insert into audit.permission select 'U', now(), user, new.*;
      return new;
    elsif (TG_OP = 'INSERT') then
      insert into audit.permission select 'I', now(), user, new.*;
      return new;
    end if;
    return null;
  end;
$$
language plpgsql;


create table audit.role (
    op          char(1)
  , ts          timestamp
  , username    text
  , id          uuid
  , name        text
  , description text
  , created_at  timestamp
  , created_by  uuid
  , updated_at  timestamp
  , updated_by  uuid
);

create or replace function audit.role_audit() returns trigger as $$
  begin
    if (TG_OP = 'DELETE') then
      insert into audit.role select 'D', now(), user, old.*;
      return old;
    elsif (TG_OP = 'UPDATE') then
      insert into audit.role select 'U', now(), user, new.*;
      return new;
    elsif (TG_OP = 'INSERT') then
      insert into audit.role select 'I', now(), user, new.*;
      return new;
    end if;
    return null;
  end;
$$
language plpgsql;

create table audit.role_permission (
    op            char(1)
  , ts            timestamp
  , username      text
  , id            uuid
  , role_id       uuid
  , permission_id uuid
  , created_at    timestamp
  , created_by    uuid
);

create or replace function audit.role_permission_audit() returns trigger as $$
  begin
    if (TG_OP = 'DELETE') then
      insert into audit.role_permission select 'D', now(), user, old.*;
      return old;
    elsif (TG_OP = 'UPDATE') then
      insert into audit.role_permission select 'U', now(), user, new.*;
      return new;
    elsif (TG_OP = 'INSERT') then
      insert into audit.role_permission select 'I', now(), user, new.*;
      return new;
    end if;
    return null;
  end;
$$
language plpgsql;

create table audit.user_role (
    op         char(1)
  , ts         timestamp
  , username   text
  , id         uuid
  , user_id    uuid
  , role_id    uuid
  , created_at timestamp
  , created_by uuid
);

create or replace function audit.user_role_audit() returns trigger as $$
  begin
    if (TG_OP = 'DELETE') then
      insert into audit.user_role select 'D', now(), user, old.*;
      return old;
    elsif (TG_OP = 'UPDATE') then
      insert into audit.user_role select 'U', now(), user, new.*;
      return new;
    elsif (TG_OP = 'INSERT') then
      insert into audit.user_role select 'I', now(), user, new.*;
      return new;
    end if;
    return null;
  end;
$$
language plpgsql;

-- end user-audit

-- begin user info
create table "user" (
    id              uuid      primary key default gen_random_uuid()
  , first_name      text      not null
  , last_name       text      not null
  , password_digest text      null -- nullable ie if you use social login
  , created_at      timestamp not null default now()
  , created_by      uuid      null
  , updated_at      timestamp not null default now()
  , updated_by      uuid      null
);


insert into "user"(first_name, last_name, created_by, updated_by)
  values ('System', 'User');

update "user" set created_by = id, updated_by = id;

alter table "user" alter column created_by set not null, alter column updated_by set not null;

create trigger tg_user_audit before insert or update or delete on "user"
for each row execute procedure audit.user_audit();

create table channel_type (
    id         uuid      primary key default gen_random_uuid()
  , name       text      not null
  , created_at timestamp not null default now()
  , created_by uuid      not null references "user"(id)
);

create unique index unq_channel_type_name on channel_type(name);

insert into channel_type (id, name, created_by)
       select c.id, c.name, u.id
         from (values
                      ('4e3c5337-7d7d-4398-bfa7-c4e17bbffa21', 'email')
                    , ('b9e260b7-72ce-4b38-9689-9f45266eff43', 'mobile')) as c(id, name)
   cross join "user" u;

create table channel (
    id               uuid      primary key default gen_random_uuid()
  , user_id          uuid      not null references "user"(id)
  , channel_type_id  uuid      not null references channel_type(id)
  , identifier       text      not null -- email address, google id, phone #, etc
  , token            text      null -- token used for verification
  , token_expiration timestamp null
  , verified_at      timestamp null
  , created_at       timestamp not null default now()
  , created_by       uuid      not null references "user"(id)
  , updated_at       timestamp not null default now()
  , updated_by       uuid      not null references "user"(id)
);

create unique index unq_channel_channel_type_identifier on channel(channel_type_id,identifier);
create index idx_channel_user_id on channel(user_id);
create unique index unq_channel_token on channel(token);

create trigger tg_channel_audit before insert or update or delete on channel
for each row execute procedure audit.channel_audit();

create table address (
   id            uuid        primary key default gen_random_uuid()
 , street_number text
 , street        text
 , unit          text
 , city          text         not null
 , state         text         not null
 , postal_code   text
 , lat           decimal(9,6)
 , lng           decimal(9,6)
 , created_at    timestamp    not null default now()
 , created_by    uuid         not null references "user"(id)
 , updated_at    timestamp    not null default now()
 , updated_by    uuid         not null references "user"(id)
);

create trigger tg_address_audit before insert or update or delete on address
for each row execute procedure audit.address_audit();


create table user_address (
    id         uuid      primary key default gen_random_uuid()
  , user_id    uuid      not null references "user"(id)
  , address_id uuid      not null references address(id)
  , created_at timestamp not null default now()
  , created_by uuid      not null references "user"(id)
);

create unique index unq_user_address on user_address(user_id, address_id);

create trigger tg_user_address_audit before insert or update or delete on user_address
  for each row execute procedure audit.user_address_audit();

--
-- Auth
--
create table "permission" (
    id          uuid      primary key default gen_random_uuid()
  , name        text      not null
  , description text      not null
  , created_at  timestamp not null default now()
  , created_by  uuid      not null references "user"(id)
  , updated_at  timestamp not null default now()
  , updated_by  uuid      not null references "user"(id)
);

create unique index unq_permission_name on "permission"(name);

create trigger tg_permission_audit before insert or update or delete on "permission"
  for each row execute procedure audit.permission_audit();

create table "role" (
    id          uuid      primary key default gen_random_uuid()
  , name        text      not null
  , description text      not null
  , created_at  timestamp not null default now()
  , created_by  uuid      not null references "user"(id)
  , updated_at  timestamp not null default now()
  , updated_by  uuid      not null references "user"(id)
);

create unique index unq_role_name on "role"(name);

create trigger tg_role_audit before insert or update or delete on "role"
  for each row execute procedure audit.role_audit();


create table role_permission (
    id            uuid      primary key default gen_random_uuid()
  , role_id       uuid      not null references role(id)
  , permission_id uuid      not null references permission(id)
  , created_at    timestamp not null default now()
  , created_by    uuid      not null references "user"(id)
);

create unique index unq_role_permission on role_permission(role_id, permission_id);

create trigger tg_role_permission_audit before insert or update or delete on role_permission
  for each row execute procedure audit.role_permission_audit();


create table user_role (
    id         uuid      primary key default gen_random_uuid()
  , user_id    uuid      not null references "user"(id)
  , role_id    uuid      not null references role(id)
  , created_at timestamp not null default now()
  , created_by uuid      not null references "user"(id)
);

create unique index unq_user_role on user_role(user_id, role_id);

create trigger tg_user_role_audit before insert or update or delete on user_role
  for each row execute procedure audit.user_role_audit();

--
-- Access Tracking
---
create table access_log (
   id              uuid      primary key default gen_random_uuid()
 , action          text      not null
 , user_id         uuid      null references "user"(id) -- not all access requires login
 , params          jsonb     not null
 , remote_addr     text      not null
 , x_forwarded_for text      null
 , server_name     text      not null
 , request_method  text      not null
 , uri             text      not null
 , headers         jsonb     not null
 , created_at      timestamp not null default now()
);

drop role if exists readonly;
create role readonly login inherit password '${users.readonly.pass}';


grant usage on schema audit to ${roles.primary};
grant usage on schema public to ${roles.primary};
grant select, insert, update, delete on all tables in schema public to ${roles.primary};
grant select, insert on all tables in schema audit to ${roles.primary};
grant usage, select on all sequences in schema public to ${roles.primary};


grant usage on schema audit to readonly;
grant usage on schema public to readonly;
grant select on all tables in schema audit to readonly;
grant select on all tables in schema public to readonly;

revoke update, delete on access_log from ${roles.primary};
revoke truncate on access_log from ${roles.primary};


drop role if exists ${users.app.name};
create role ${users.app.name} with login inherit encrypted password '${users.app.pass}';
grant ${roles.primary} to ${users.app.name};
revoke connect on database ${database.name} from public;
grant connect on database ${database.name} to ${users.app.name};


------------------------------------------------------------------------
-- Clojure flavored JSONB Functions
------------------------------------------------------------------------
create or replace function array_last(xs anyarray) returns anyelement immutable as $$
  begin
    return xs[array_length(xs, 1)];
  end;
$$ language plpgsql;


create or replace function array_butlast(xs anyarray) returns anyarray immutable as $$
begin
  return xs[1:array_length(xs, 1) - 1];
end;
$$ language plpgsql;

create or replace function array_rest(xs anyarray) returns anyarray immutable as $$
begin
  return xs[2:];
end;
$$ language plpgsql;



-- nb if v is text you probably need to ::text it otherwise you get
-- ERROR:  could not determine polymorphic type because input has type "unknown"
-- must make sure the last item is a map otherwise if vector won't do what you want
-- no way of checking the type of the document element
create or replace function jsonb_assoc_in(doc jsonb, path text[], v anyelement) returns jsonb immutable as $$
  declare
    k text; -- the key
    actual_path text[];
    new_val jsonb;
  begin
    k := array_last(path);
    actual_path := array_butlast(path);
    new_val := (doc #> actual_path) || jsonb_build_object(k, v);
    return jsonb_set(doc, actual_path, new_val);
  end;
$$
language plpgsql;
