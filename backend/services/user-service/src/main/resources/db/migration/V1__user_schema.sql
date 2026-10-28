CREATE FUNCTION set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN NEW.updated_at=clock_timestamp(); RETURN NEW; END $$;
CREATE TABLE app_user (
 user_id uuid PRIMARY KEY DEFAULT gen_random_uuid(), email_normalized varchar(254) NOT NULL UNIQUE CHECK(email_normalized=lower(btrim(email_normalized)) AND char_length(email_normalized)>0),
 password_hash varchar(255) NOT NULL CHECK(char_length(password_hash)>0),role varchar(16) NOT NULL CHECK(role IN ('LEARNER','ADMIN')),status varchar(16) NOT NULL CHECK(status IN ('ACTIVE','BLOCKED')),
 created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE learner_profile(profile_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),user_id uuid NOT NULL UNIQUE REFERENCES app_user(user_id) ON DELETE RESTRICT,display_name varchar(100) CHECK(display_name IS NULL OR char_length(btrim(display_name))>0),created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE learning_setup(user_id uuid PRIMARY KEY REFERENCES app_user(user_id) ON DELETE RESTRICT,current_course_id uuid NOT NULL,current_level_id uuid NOT NULL,updated_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX learning_setup_cohort ON learning_setup(current_course_id,current_level_id,user_id);
CREATE TABLE revoked_token(jti uuid PRIMARY KEY,expires_at timestamptz NOT NULL);
CREATE INDEX revoked_token_expiry ON revoked_token(expires_at);
CREATE TABLE spring_session(primary_id char(36) PRIMARY KEY,session_id char(36) NOT NULL UNIQUE,creation_time bigint NOT NULL,last_access_time bigint NOT NULL,max_inactive_interval integer NOT NULL,expiry_time bigint NOT NULL,principal_name varchar(100));
CREATE INDEX spring_session_expiry ON spring_session(expiry_time);
CREATE INDEX spring_session_principal ON spring_session(principal_name);
CREATE TABLE spring_session_attributes(session_primary_id char(36) NOT NULL REFERENCES spring_session(primary_id) ON DELETE CASCADE,attribute_name varchar(200) NOT NULL,attribute_bytes bytea NOT NULL,PRIMARY KEY(session_primary_id,attribute_name));
CREATE TRIGGER app_user_timestamp BEFORE UPDATE ON app_user FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER profile_timestamp BEFORE UPDATE ON learner_profile FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER setup_timestamp BEFORE UPDATE ON learning_setup FOR EACH ROW EXECUTE FUNCTION set_updated_at();
GRANT SELECT,INSERT,UPDATE ON app_user,learner_profile,learning_setup TO user_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON revoked_token,spring_session,spring_session_attributes TO user_runtime;
GRANT USAGE ON SCHEMA public TO user_runtime;
