drop schema if exists audit cascade;

create schema audit authorization dba;

create table audit.user (
    op char(1)
  , ts timestamp
  , username varchar(25)
  , id integer
  , first_name varchar(50)
  , last_name varchar(50)
  , password_digest char(100)
  , created_at timestamp
  , created_by integer
  , updated_at timestamp
  , updated_by integer
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
    op char(1)
  , ts timestamp
  , username varchar(25)
  , id integer
  , user_id integer
  , channel_type_id integer
  , identifier varchar(50)
  , token varchar(50)
  , token_expiration timestamp
  , verified_at timestamp
  , created_at timestamp
  , created_by integer
  , updated_at timestamp
  , updated_by integer
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
   op          char(1)
 , ts          timestamp
 , username    varchar(25)
 , id          integer
 , street_1    varchar(50)
 , street_2    varchar(20)
 , city        varchar(50)
 , state       varchar(50)
 , postal_code varchar(9)
 , lat         decimal(9,6)
 , lng         decimal(9,6)
 , created_at  timestamp
 , created_by  integer
 , updated_at  timestamp
 , updated_by  integer
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
    op char(1)
  , ts timestamp
  , username varchar(25)
  , id integer
  , user_id integer
  , address_id integer
  , created_at timestamp
  , created_by integer
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
    op char(1)
  , ts timestamp
  , username varchar(25)
  , id integer
  , name varchar(50)
  , description varchar(100)
  , created_at timestamp
  , created_by integer
  , updated_at timestamp
  , updated_by integer
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
    op char(1)
  , ts timestamp
  , username varchar(25)
  , id integer
  , name varchar(50)
  , description varchar(100)
  , created_at timestamp
  , created_by integer
  , updated_at timestamp
  , updated_by integer
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
    op char(1)
  , ts timestamp
  , username varchar(25)
  , id integer
  , role_id integer
  , permission_id integer
  , created_at timestamp
  , created_by integer
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
    op char(1)
  , ts timestamp
  , username varchar(25)
  , id integer
  , user_id integer
  , role_id integer
  , created_at timestamp
  , created_by integer
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
