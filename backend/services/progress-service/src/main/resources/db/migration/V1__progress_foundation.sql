CREATE TABLE learner_lock (user_id UUID PRIMARY KEY);
CREATE TABLE processed_event (
 event_id UUID PRIMARY KEY, submission_id UUID NOT NULL, payload_hash VARCHAR(64) NOT NULL,
 processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE learning_history (
 history_id UUID PRIMARY KEY, user_id UUID NOT NULL, assessment_id UUID NOT NULL,
 assessment_type VARCHAR(16) NOT NULL CHECK(assessment_type IN ('EXERCISE','QUIZ','TEST')),
 submission_id UUID NOT NULL UNIQUE, result_id UUID NOT NULL UNIQUE,
 course_id UUID NOT NULL, level_id_at_start UUID NOT NULL, lesson_id UUID,
 earned_points NUMERIC NOT NULL CHECK(earned_points>=0), max_points NUMERIC NOT NULL CHECK(max_points>0),
 completed_at TIMESTAMP WITH TIME ZONE NOT NULL, payload_hash VARCHAR(64) NOT NULL,
 CHECK(earned_points<=max_points), CHECK(assessment_type<>'EXERCISE' OR lesson_id IS NOT NULL)
);
CREATE INDEX history_owner ON learning_history(user_id,completed_at,history_id);
CREATE TABLE lesson_completion (
 user_id UUID NOT NULL, lesson_id UUID NOT NULL, course_id UUID NOT NULL,
 level_id_at_first_completion UUID NOT NULL, first_submission_id UUID NOT NULL,
 completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
 PRIMARY KEY(user_id,lesson_id)
);
CREATE TABLE course_progress (
 user_id UUID NOT NULL, course_id UUID NOT NULL,
 completed_lessons INTEGER NOT NULL CHECK(completed_lessons>=0),
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL, PRIMARY KEY(user_id,course_id)
);
