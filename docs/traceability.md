# FR / AC / API / implementation trace

| Operation | Owner | FR | AC | Implementation / verification |
|---|---|---|---|---|
| getCsrf: GET /api/v1/auth/csrf | User Service | FR-01, FR-02, FR-03 | AC-01, AC-02, AC-19 | UserController; PostgreSQL tests / Gateway smoke contract check |
| register: POST /api/v1/auth/register | User Service | FR-01, FR-05 | AC-01, AC-19 | UserController; PostgreSQL tests / Gateway smoke contract check |
| login: POST /api/v1/auth/login | User Service | FR-02, FR-05 | AC-02, AC-19 | UserController; PostgreSQL tests / Gateway smoke contract check |
| logout: POST /api/v1/auth/logout | User Service | FR-03, FR-05 | AC-02, AC-19 | UserController; PostgreSQL tests / Gateway smoke contract check |
| getMyProfile: GET /api/v1/me/profile | User Service | FR-04, FR-05 | AC-03, AC-19 | UserController; PostgreSQL tests / Gateway smoke contract check |
| patchMyProfile: PATCH /api/v1/me/profile | User Service | FR-04, FR-05 | AC-03, AC-19 | UserController; PostgreSQL tests / Gateway smoke contract check |
| getLearningSetup: GET /api/v1/me/learning-setup | User Service | FR-06, FR-07 | AC-05, AC-06 | UserController; PostgreSQL tests / Gateway smoke contract check |
| putLearningSetup: PUT /api/v1/me/learning-setup | User Service | FR-06, FR-07 | AC-05, AC-06 | UserController; PostgreSQL tests / Gateway smoke contract check |
| listUsers: GET /api/v1/admin/users | User Service | FR-05 | AC-04 | UserController; PostgreSQL tests / Gateway smoke contract check |
| getUser: GET /api/v1/admin/users/{userId} | User Service | FR-05 | AC-04 | UserController; PostgreSQL tests / Gateway smoke contract check |
| patchUser: PATCH /api/v1/admin/users/{userId} | User Service | FR-05 | AC-04 | UserController; PostgreSQL tests / Gateway smoke contract check |
| listCourses: GET /api/v1/courses | Learning Content Service | FR-06 | AC-05 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| getCourse: GET /api/v1/courses/{courseId} | Learning Content Service | FR-06, FR-08 | AC-07, AC-08 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| listLevels: GET /api/v1/courses/{courseId}/levels | Learning Content Service | FR-06, FR-07 | AC-05 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| listModules: GET /api/v1/courses/{courseId}/modules | Learning Content Service | FR-08 | AC-07, AC-08 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| listLessons: GET /api/v1/modules/{moduleId}/lessons | Learning Content Service | FR-08 | AC-07, AC-08 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| getLessonLearning: GET /api/v1/lessons/{lessonId} | Learning Content Service | FR-08, FR-09 | AC-07, AC-08 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| listSavedVocabulary: GET /api/v1/me/vocabulary | Learning Content Service | FR-10, FR-11 | AC-09, AC-10 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| saveVocabulary: PUT /api/v1/me/vocabulary/{vocabularyId} | Learning Content Service | FR-10 | AC-09 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| removeVocabulary: DELETE /api/v1/me/vocabulary/{vocabularyId} | Learning Content Service | FR-11 | AC-10 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| adminListCourses: GET /api/v1/admin/courses | Learning Content Service | FR-17 | AC-16 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| createCourse: POST /api/v1/admin/courses | Learning Content Service | FR-17 | AC-16 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| adminGetCourse: GET /api/v1/admin/courses/{courseId} | Learning Content Service | FR-17 | AC-16 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| patchCourse: PATCH /api/v1/admin/courses/{courseId} | Learning Content Service | FR-17 | AC-16 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| adminListModules: GET /api/v1/admin/modules | Learning Content Service | FR-17 | AC-16 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| createModule: POST /api/v1/admin/modules | Learning Content Service | FR-17 | AC-16 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| adminGetModule: GET /api/v1/admin/modules/{moduleId} | Learning Content Service | FR-17 | AC-16 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| patchModule: PATCH /api/v1/admin/modules/{moduleId} | Learning Content Service | FR-17 | AC-16 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| adminListLessons: GET /api/v1/admin/lessons | Learning Content Service | FR-17 | AC-16 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| createLesson: POST /api/v1/admin/lessons | Learning Content Service | FR-17 | AC-16 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| adminGetLesson: GET /api/v1/admin/lessons/{lessonId} | Learning Content Service | FR-17 | AC-16 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| patchLesson: PATCH /api/v1/admin/lessons/{lessonId} | Learning Content Service | FR-17 | AC-16 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| adminListVocabulary: GET /api/v1/admin/vocabulary | Learning Content Service | FR-18 | AC-16 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| adminGetVocabulary: GET /api/v1/admin/vocabulary/{vocabularyId} | Learning Content Service | FR-18 | AC-16 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| patchVocabulary: PATCH /api/v1/admin/vocabulary/{vocabularyId} | Learning Content Service | FR-18 | AC-16 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| adminListLearningContent: GET /api/v1/admin/learning-contents | Learning Content Service | FR-18 | AC-16 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| createLearningContent: POST /api/v1/admin/learning-contents | Learning Content Service | FR-18 | AC-16 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| adminGetLearningContent: GET /api/v1/admin/learning-contents/{contentId} | Learning Content Service | FR-18 | AC-16 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| patchLearningContent: PATCH /api/v1/admin/learning-contents/{contentId} | Learning Content Service | FR-18 | AC-16 | ContentController + ContentStore; PostgreSQL tests / Gateway smoke contract check |
| importVocabulary: POST /api/v1/admin/imports/vocabulary | Learning Content Service | FR-21 | AC-23 | ImportController + Imports; PostgreSQL tests / Gateway smoke contract check |
| importLessons: POST /api/v1/admin/imports/lessons | Learning Content Service | FR-21 | AC-23 | ImportController + Imports; PostgreSQL tests / Gateway smoke contract check |
| getTemplateVocabulary: GET /api/v1/admin/import-templates/vocabulary | Learning Content Service | FR-21 | AC-23 | ImportController + Imports; PostgreSQL tests / Gateway smoke contract check |
| getTemplateLessons: GET /api/v1/admin/import-templates/lessons | Learning Content Service | FR-21 | AC-23 | ImportController + Imports; PostgreSQL tests / Gateway smoke contract check |

## Open reviewer actions

Khoa code/contract/fixture evidence does not close Huy's schema/architecture work or Hưng's WBS review. API-04 SRS/UC-AC 0.4 still requires actual cross-review. API-05/07/08/11 across Assessment/Progress require owner integration; this package provides Content context/readiness adapter and User setup/cohort only. API-14/Figma not assessed. FIXTURE-01 files and expected results are in docs/fixtures, parser tests and verification report.
