CREATE TABLE assessment (
 assessment_id UUID PRIMARY KEY,
 type VARCHAR(16) NOT NULL CHECK(type IN ('EXERCISE','QUIZ','TEST')),
 course_id UUID NOT NULL, lesson_id UUID, title VARCHAR(200) NOT NULL,
 time_limit_seconds INTEGER, ready BOOLEAN NOT NULL DEFAULT FALSE,
 CHECK((type='EXERCISE' AND lesson_id IS NOT NULL AND time_limit_seconds IS NULL)
 OR (type IN ('QUIZ','TEST') AND time_limit_seconds>0 AND time_limit_seconds IS NOT NULL))
);
CREATE TABLE exercise_lesson (
 lesson_id UUID PRIMARY KEY, assessment_id UUID NOT NULL UNIQUE REFERENCES assessment(assessment_id)
);
CREATE TABLE question (
 question_id UUID PRIMARY KEY, assessment_id UUID NOT NULL REFERENCES assessment(assessment_id),
 definition TEXT NOT NULL
);
CREATE INDEX question_assessment ON question(assessment_id,question_id);
CREATE TABLE submission (
 submission_id UUID PRIMARY KEY, assessment_id UUID NOT NULL REFERENCES assessment(assessment_id),
 user_id UUID NOT NULL, idempotency_key UUID NOT NULL,
 snapshot TEXT NOT NULL, answers TEXT NOT NULL DEFAULT '[]',
 started_at TIMESTAMP WITH TIME ZONE NOT NULL, deadline_at TIMESTAMP WITH TIME ZONE,
 status VARCHAR(16) NOT NULL CHECK(status IN ('IN_PROGRESS','SUBMITTED','TIMEOUT')),
 UNIQUE(user_id,idempotency_key)
);
CREATE INDEX submission_owner ON submission(user_id,started_at,submission_id);
CREATE INDEX submission_expiry ON submission(status,deadline_at);
CREATE TABLE result (
 result_id UUID PRIMARY KEY, submission_id UUID NOT NULL UNIQUE REFERENCES submission(submission_id),
 user_id UUID NOT NULL, completed_at TIMESTAMP WITH TIME ZONE NOT NULL, payload TEXT NOT NULL
);
CREATE INDEX result_owner ON result(user_id,completed_at,result_id);
CREATE TABLE outbox_event (
 event_id UUID PRIMARY KEY, submission_id UUID NOT NULL UNIQUE REFERENCES submission(submission_id),
 event_type VARCHAR(64) NOT NULL, schema_version INTEGER NOT NULL CHECK(schema_version=1),
 topic VARCHAR(128) NOT NULL, partition_key VARCHAR(36) NOT NULL, payload TEXT NOT NULL,
 occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','SENT')),
 retry_count INTEGER NOT NULL DEFAULT 0, last_error TEXT
);

CREATE TABLE learner_lock (user_id UUID PRIMARY KEY);
