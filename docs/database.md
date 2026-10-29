# PostgreSQL data dictionary — two owner databases

All UUID references across services are external, without physical FK. Business timestamps are UTC timestamptz. Trigger-managed updated_at applies to mutable records.

## user_db

### app_user

| Column | Type | Constraints |
|---|---|---|
| user_id | uuid | PRIMARY KEY DEFAULT gen_random_uuid() |
| email_normalized | varchar(254) | NOT NULL UNIQUE CHECK(email_normalized=lower(btrim(email_normalized)) AND char_length(email_normalized)>0) |
| password_hash | varchar(255) | NOT NULL CHECK(char_length(password_hash)>0) |
| role | varchar(16) | NOT NULL CHECK(role IN ('LEARNER','ADMIN')) |
| status | varchar(16) | NOT NULL CHECK(status IN ('ACTIVE','BLOCKED')) |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| updated_at | timestamptz | NOT NULL DEFAULT now() |
### learner_profile

| Column | Type | Constraints |
|---|---|---|
| profile_id | uuid | PRIMARY KEY DEFAULT gen_random_uuid() |
| user_id | uuid | NOT NULL UNIQUE REFERENCES app_user(user_id) ON DELETE RESTRICT |
| display_name | varchar(100) | CHECK(display_name IS NULL OR char_length(btrim(display_name))>0) |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| updated_at | timestamptz | NOT NULL DEFAULT now() |
### learning_setup

| Column | Type | Constraints |
|---|---|---|
| user_id | uuid | PRIMARY KEY REFERENCES app_user(user_id) ON DELETE RESTRICT |
| current_course_id | uuid | NOT NULL |
| current_level_id | uuid | NOT NULL |
| updated_at | timestamptz | NOT NULL DEFAULT now() |
### revoked_token

| Column | Type | Constraints |
|---|---|---|
| jti | uuid | PRIMARY KEY |
| expires_at | timestamptz | NOT NULL |
### spring_session

| Column | Type | Constraints |
|---|---|---|
| primary_id | char(36) | PRIMARY KEY |
| session_id | char(36) | NOT NULL UNIQUE |
| creation_time | bigint | NOT NULL |
| last_access_time | bigint | NOT NULL |
| max_inactive_interval | integer | NOT NULL |
| expiry_time | bigint | NOT NULL |
| principal_name | varchar(100) |  |
### spring_session_attributes

| Column | Type | Constraints |
|---|---|---|
| session_primary_id | char(36) | NOT NULL REFERENCES spring_session(primary_id) ON DELETE CASCADE |
| attribute_name | varchar(200) | NOT NULL |
| attribute_bytes | bytea | NOT NULL,PRIMARY KEY(session_primary_id,attribute_name) |

SQL migration is the authoritative executable definition; indexes, grants and triggers are in the corresponding migration.

## content_db

### course

| Column | Type | Constraints |
|---|---|---|
| course_id | uuid | PRIMARY KEY DEFAULT gen_random_uuid() |
| name | varchar(200) | NOT NULL CHECK(char_length(btrim(name))>0) |
| name_normalized | text | NOT NULL UNIQUE CHECK(char_length(name_normalized)>0) |
| published | boolean | NOT NULL DEFAULT false |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| updated_at | timestamptz | NOT NULL DEFAULT now() |
### level

| Column | Type | Constraints |
|---|---|---|
| level_id | uuid | PRIMARY KEY DEFAULT gen_random_uuid() |
| course_id | uuid | NOT NULL REFERENCES course(course_id) ON DELETE RESTRICT |
| name | varchar(200) | NOT NULL CHECK(char_length(btrim(name))>0) |
| name_normalized | text | NOT NULL CHECK(char_length(name_normalized)>0) |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| updated_at | timestamptz | NOT NULL DEFAULT now(),UNIQUE(course_id,name_normalized) |
### course_module

| Column | Type | Constraints |
|---|---|---|
| module_id | uuid | PRIMARY KEY DEFAULT gen_random_uuid() |
| course_id | uuid | NOT NULL REFERENCES course(course_id) ON DELETE RESTRICT |
| title | varchar(200) | NOT NULL CHECK(char_length(btrim(title))>0) |
| title_normalized | text | NOT NULL CHECK(char_length(title_normalized)>0) |
| order_no | integer | NOT NULL CHECK(order_no>0) |
| published | boolean | NOT NULL DEFAULT false |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| updated_at | timestamptz | NOT NULL DEFAULT now(),UNIQUE(course_id,title_normalized),UNIQUE(course_id,order_no) |
### lesson

| Column | Type | Constraints |
|---|---|---|
| lesson_id | uuid | PRIMARY KEY DEFAULT gen_random_uuid() |
| module_id | uuid | NOT NULL REFERENCES course_module(module_id) ON DELETE RESTRICT |
| title | varchar(200) | NOT NULL CHECK(char_length(btrim(title))>0) |
| title_normalized | text | NOT NULL CHECK(char_length(title_normalized)>0) |
| content | text | NOT NULL CHECK(char_length(btrim(content))>0 AND char_length(content)<=20000) |
| order_no | integer | NOT NULL CHECK(order_no>0) |
| published | boolean | NOT NULL DEFAULT false |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| updated_at | timestamptz | NOT NULL DEFAULT now(),UNIQUE(module_id,title_normalized),UNIQUE(module_id,order_no) |
### vocabulary

| Column | Type | Constraints |
|---|---|---|
| vocabulary_id | uuid | PRIMARY KEY DEFAULT gen_random_uuid() |
| lesson_id | uuid | NOT NULL REFERENCES lesson(lesson_id) ON DELETE RESTRICT |
| word | varchar(128) | NOT NULL CHECK(char_length(btrim(word))>0) |
| meaning | varchar(500) | NOT NULL CHECK(char_length(btrim(meaning))>0) |
| example | varchar(1000) |  |
| phonetic | varchar(128) |  |
| word_normalized | text | NOT NULL CHECK(char_length(word_normalized)>0) |
| meaning_normalized | text | NOT NULL CHECK(char_length(meaning_normalized)>0) |
| published | boolean | NOT NULL DEFAULT false |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| updated_at | timestamptz | NOT NULL DEFAULT now(),UNIQUE(lesson_id,word_normalized,meaning_normalized) |
### learning_content

| Column | Type | Constraints |
|---|---|---|
| content_id | uuid | PRIMARY KEY DEFAULT gen_random_uuid() |
| lesson_id | uuid | NOT NULL REFERENCES lesson(lesson_id) ON DELETE RESTRICT |
| type | varchar(16) | NOT NULL CHECK(type IN ('GRAMMAR','LISTENING','READING')) |
| title | varchar(200) | NOT NULL CHECK(char_length(btrim(title))>0) |
| text | text | CHECK(text IS NULL OR (char_length(btrim(text))>0 AND char_length(text)<=20000)) |
| resource_url | varchar(2048) | CHECK(resource_url IS NULL OR resource_url ~ '^https://') |
| published | boolean | NOT NULL DEFAULT false |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| updated_at | timestamptz | NOT NULL DEFAULT now(),CHECK((type IN ('GRAMMAR','READING') AND text IS NOT NULL) OR (type='LISTENING' AND resource_url IS NOT NULL)) |
### user_vocabulary

| Column | Type | Constraints |
|---|---|---|
| user_vocabulary_id | uuid | PRIMARY KEY DEFAULT gen_random_uuid() |
| user_id | uuid | NOT NULL |
| vocabulary_id | uuid | NOT NULL REFERENCES vocabulary(vocabulary_id) ON DELETE RESTRICT |
| added_at | timestamptz | NOT NULL DEFAULT now(),UNIQUE(user_id,vocabulary_id) |

SQL migration is the authoritative executable definition; indexes, grants and triggers are in the corresponding migration.

## Reconciliation

API-02/IS-01: Vocabulary belongs Lesson, normalized import key unique. Course derived through Lesson/Module, no redundant course_id.
API-03/IS-02–04: password_hash varchar(255), fullName→display_name, revoked token, current setup and JDBC CSRF state. Assessment snapshots/Result/outbox and Progress processed-event/history are external dependencies, not tables added here.
API-09: UUID IDs, ACTIVE/BLOCKED and paging/order match API. Constraints reject null/blank and invalid lengths/types.
API-10: Levels are children of Course supplied by Content seed CLI. No implicit Level→Lesson/Assessment filter.
API-12: Grammar/Reading text; Listening HTTPS resource with explicit availability.
API-13: FR-17 forbids delete/soft-delete Course/Module/Lesson. No Vocabulary delete endpoint in v0.1; no runtime DELETE grant is a least-privilege choice, not a permanent business prohibition. Internal FK protects saved references.

Bootstrap owns privileged provisioning; migration owns schema; runtime has SELECT/INSERT/UPDATE only on required tables. Runtime DELETE is restricted to user_vocabulary, expired revoked tokens and session cleanup. Runtime cannot DDL/TRUNCATE. PostgreSQL replaces MySQL only for User and Content under user's implementation instruction; review/synchronization of project-wide technology documents remains explicit.
