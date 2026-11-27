# Database ERD

```mermaid
erDiagram
 APP_USER ||--o| LEARNER_PROFILE : owns
 APP_USER ||--o| LEARNING_SETUP : selects
 SPRING_SESSION ||--o{ SPRING_SESSION_ATTRIBUTES : stores
 COURSE ||--o{ LEVEL : has
 COURSE ||--o{ COURSE_MODULE : contains
 COURSE_MODULE ||--o{ LESSON : contains
 LESSON ||--o{ VOCABULARY : provides
 LESSON ||--o{ LEARNING_CONTENT : provides
 VOCABULARY ||--o{ USER_VOCABULARY : referenced
```

USER_VOCABULARY.user_id is external to Content, not an FK to User DB. LEARNING_SETUP.current_course_id/current_level_id are external to User. REVOKED_TOKEN has no cross-service relationship.
