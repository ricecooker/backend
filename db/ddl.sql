--
-- DDL
--
-- Defines tables and relationships between tables.  For seed
-- data see seed.sql
--

--
--  Schema
--
DROP SCHEMA IF EXISTS e85;
CREATE SCHEMA e85;
SET SCHEMA 'e85';

--
-- Authentication
--
CREATE SEQUENCE user_id_seq START 1;
DROP TABLE IF EXISTS "user";
CREATE TABLE "user"
(
  id INTEGER PRIMARY KEY NOT NULL DEFAULT nextval('user_id_seq'::regclass),
  first_name CHARACTER VARYING(50) NOT NULL,
  last_name CHARACTER VARYING(50) NOT NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
  created_by INTEGER NOT NULL,
  updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
  updated_by INTEGER NOT NULL,
  password_digest CHARACTER(60)
);
CREATE UNIQUE INDEX user_id_uindex ON "user" USING BTREE (id);

CREATE SEQUENCE channel_id_seq START 1;
DROP TABLE IF EXISTS channel;
CREATE TABLE channel
(
  id INTEGER PRIMARY KEY NOT NULL DEFAULT nextval('channel_id_seq'::regclass),
  user_id INTEGER NOT NULL,
  channel_type_id INTEGER NOT NULL,
  identifier CHARACTER VARYING(50) NOT NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
  created_by INTEGER NOT NULL,
  updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
  updated_by INTEGER NOT NULL,
FOREIGN KEY (user_id) REFERENCES "user" (id)
MATCH SIMPLE ON UPDATE NO ACTION ON DELETE NO ACTION
);
CREATE UNIQUE INDEX channel_id_uindex ON channel USING BTREE (id);

--
-- Authorization
--
CREATE SEQUENCE permission_id_seq START 1;
DROP TABLE IF EXISTS permission;
CREATE TABLE permission
(
  id INTEGER PRIMARY KEY NOT NULL DEFAULT nextval('permission_id_seq'::regclass),
  name CHARACTER VARYING(50) NOT NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
  created_by INTEGER NOT NULL
);
CREATE UNIQUE INDEX permission_id_uindex ON permission USING BTREE (id);

CREATE SEQUENCE role_id_seq START 1;
DROP TABLE IF EXISTS role;
CREATE TABLE role
(
  id INTEGER PRIMARY KEY NOT NULL DEFAULT nextval('role_id_seq'::regclass),
  name CHARACTER VARYING(20) NOT NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
  created_by INTEGER NOT NULL
);
CREATE UNIQUE INDEX role_id_uindex ON role USING BTREE (id);

CREATE SEQUENCE role_permission_id_seq START 1;
DROP TABLE IF EXISTS role_permission;
CREATE TABLE role_permission
(
  id INTEGER PRIMARY KEY NOT NULL DEFAULT nextval('role_permission_id_seq'::regclass),
  role_id INTEGER NOT NULL,
  permission_id INTEGER NOT NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
  created_by INTEGER NOT NULL,
  FOREIGN KEY (permission_id) REFERENCES permission (id)
  MATCH SIMPLE ON UPDATE NO ACTION ON DELETE NO ACTION,
  FOREIGN KEY (role_id) REFERENCES role (id)
  MATCH SIMPLE ON UPDATE NO ACTION ON DELETE NO ACTION
);
CREATE UNIQUE INDEX role_permission_id_uindex ON role_permission USING BTREE (id);

CREATE SEQUENCE user_role_id_seq START 1;
DROP TABLE IF EXISTS user_role;
CREATE TABLE user_role
(
  id INTEGER PRIMARY KEY NOT NULL DEFAULT nextval('user_role_id_seq'::regclass),
  user_id INTEGER NOT NULL,
  role_id INTEGER NOT NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
  created_by INTEGER NOT NULL,
  FOREIGN KEY (role_id) REFERENCES role (id)
  MATCH SIMPLE ON UPDATE NO ACTION ON DELETE NO ACTION,
  FOREIGN KEY (user_id) REFERENCES "user" (id)
  MATCH SIMPLE ON UPDATE NO ACTION ON DELETE NO ACTION
);
CREATE UNIQUE INDEX user_role_id_uindex ON user_role USING BTREE (id);

--
-- User tracking
--

CREATE SEQUENCE user_event_id_seq START 1;
DROP TABLE IF EXISTS user_event;
CREATE TABLE user_event
(
  id INTEGER PRIMARY KEY NOT NULL DEFAULT nextval('user_event_id_seq'::regclass),
  user_id INTEGER NOT NULL,
  name CHARACTER VARYING(100) NOT NULL,
  properties CHARACTER VARYING(200) NOT NULL DEFAULT '{}'::character varying,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
  created_by INTEGER NOT NULL,
  FOREIGN KEY (user_id) REFERENCES "user" (id)
  MATCH SIMPLE ON UPDATE NO ACTION ON DELETE NO ACTION
);
CREATE UNIQUE INDEX user_event_id_uindex ON user_event USING BTREE (id);

