-- Run user-audit.sql first
-- At the bottom you will have to edit items in <>
-- such as <PASS> and <YOUR-APP-USER>

drop table if exists user_event;
drop table if exists user_role;
drop table if exists role_permission;
drop table if exists role;
drop table if exists permission;
drop table if exists channel;
drop table if exists channel_type;
drop table if exists user_address;
drop table if exists address;
drop table if exists "user";


create table "user" (
    id serial primary key
  , first_name varchar(50) not null
  , last_name varchar(50) not null
  , password_digest varchar(100) null -- nullable ie if you use social login
  , created_at timestamp not null default now()
  , created_by integer not null
  , updated_at timestamp not null default now()
  , updated_by integer not null
);

create trigger tg_user_audit before insert or update or delete on "user"
  for each row execute procedure audit.user_audit();

insert into "user"(first_name, last_name, created_by, updated_by)
  values ('System', 'User', 1, 1);

create table channel_type (
    id serial primary key
  , name varchar(20) not null
  , created_at timestamp not null default now()
  , created_by integer not null references "user"(id)
);

create unique index unq_channel_type_name on channel_type(name);

insert into channel_type (name, created_by)
  values ('mobile',1), ('email',1), ('google',1);

create table channel (
    id serial primary key
  , user_id integer not null references "user"(id)
  , channel_type_id integer not null references channel_type(id)
  , identifier varchar(50) not null -- email address, google id, phone #, etc
  , token varchar(50) null -- token used for verification
  , token_expiration timestamp null
  , verified_at timestamp null
  , created_at timestamp not null default now()
  , created_by integer not null references "user"(id)
  , updated_at timestamp not null default now()
  , updated_by integer not null references "user"(id)
);

create unique index unq_channel_channel_type_identifier on channel(channel_type_id,identifier);
create index idx_channel_user_id on channel(user_id);

create table address (
   id serial primary key
 , street_1    varchar(50)
 , street_2    varchar(20)
 , city        varchar(50)    not null
 , state       varchar(50)    not null
 , postal_code varchar(9)
 , lat         decimal(9,6)
 , lng         decimal(9,6)
 , created_at  timestamp      not null default now()
 , created_by  integer        not null references "user"(id)
 , updated_at  timestamp      not null default now()
 , updated_by  integer        not null references "user"(id)
);

create trigger tg_address_audit before insert or update or delete on address
for each row execute procedure audit.address_audit();


create table user_address (
    id serial primary key
  , user_id integer not null references "user"(id)
  , address_id integer not null references address(id)
  , created_at timestamp not null default now()
  , created_by integer not null references "user"(id)
);

create unique index unq_user_address on user_address(user_id, address_id);

create trigger tg_user_address_audit before insert or update or delete on user_address
  for each row execute procedure audit.user_address_audit();

--
-- Auth
--
create table "permission" (
    id serial primary key
  , name varchar(50) not null
  , description varchar(100) not null
  , created_at timestamp not null default now()
  , created_by integer not null references "user"(id)
  , updated_at timestamp not null default now()
  , updated_by integer not null references "user"(id)
);

create unique index unq_permission_name on "permission"(name);

create trigger tg_permission_audit before insert or update or delete on "permission"
  for each row execute procedure audit.permission_audit();

create table "role" (
    id serial primary key
  , name varchar(50) not null
  , description varchar(100) not null
  , created_at timestamp not null default now()
  , created_by integer not null references "user"(id)
  , updated_at timestamp not null default now()
  , updated_by integer not null references "user"(id)
);

create unique index unq_role_name on "role"(name);

create trigger tg_role_audit before insert or update or delete on "role"
  for each row execute procedure audit.role_audit();


create table role_permission (
    id serial primary key
  , role_id integer not null references role(id)
  , permission_id integer not null references permission(id)
  , created_at timestamp not null default now()
  , created_by integer not null references "user"(id)
);

create unique index unq_role_permission on role_permission(role_id, permission_id);

create trigger tg_role_permission_audit before insert or update or delete on role_permission
  for each row execute procedure audit.role_permission_audit();


create table user_role (
    id serial primary key
  , user_id integer not null references "user"(id)
  , role_id integer not null references role(id)
  , created_at timestamp not null default now()
  , created_by integer not null references "user"(id)
);

create unique index unq_user_role on user_role(user_id, role_id);

create trigger tg_user_role_audit before insert or update or delete on user_role
  for each row execute procedure audit.user_role_audit();

--
-- User tracking
---
create table user_event (
    id serial primary key
  , user_id integer not null references "user"(id)
  , name varchar(100) not null
  , doc jsonb not null
  , created_at timestamp not null
);

create index idx_user_event_user_id on user_event(user_id);
create index idx_user_event_name on user_event(name);

create role readonly login inherit password <PASS>;

grant usage on schema audit to readonly;
grant usage on schema public to readonly;
grant select on all tables in schema audit to readonly;
grant select on all tables in schema public to readonly;

grant usage on schema audit to <YOUR-APP-USER>;
grant usage on schema public to <YOUR-APP-USER>;
grant select, insert, update, delete on all tables in schema public to <YOUR-APP-USER>;
grant select, insert on all tables in schema audit to <YOUR-APP-USER>;
grant usage, select on all sequences in schema public to <YOUR-APP-USER>;

revoke delete on "user" from <YOUR-APP-USER>;
revoke update, delete on user_event from <YOUR-APP-USER>;
revoke update, delete on "address" from <YOUR-APP-USER>;
