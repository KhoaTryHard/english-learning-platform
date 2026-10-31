# Progress foundation: data mapping và kiến trúc W5-02

Owner: Huy; #4/#16/#17/#20, các lát đầy đủ #30/#34/#35. Triển khai PostgreSQL 17
cho Progress, database/credentials/migration riêng, cùng hướng công nghệ Assessment.
Đây là lựa chọn triển khai của Huy; cần Khoa/Huy review mapping và cập nhật ERD chung.

| Logical W3-03 | Physical V1 |
|---|---|
| ProcessedEvent | `processed_event`: event_id PK, submission_id, payload_hash, processed_at |
| LearningHistory | `learning_history`: history UUID, owner, assessment/type, submission/result, Course/Level snapshot, lesson nullable, points/time |
| LessonCompletion | `lesson_completion`: PK(user_id,lesson_id), immutable course, level/first submission/time |
| CourseProgress | `course_progress`: PK(user_id,course_id), completed_lessons, updated_at |
| Consumer lock | `learner_lock`: per-user lock trong Progress DB |

History có UNIQUE submission_id và result_id; Completion unique user+lesson.
NUMERIC giữ điểm thập phân; CHECK bảo vệ earned>=0, max>0, earned<=max.
Các ID nguồn đều external refs; không FK/join/transaction xuyên service.

Consumer kiểm schema v1, UUID/time/UTC, Kafka key=userId và điểm. Event trùng cùng
payload là no-op; eventId mới/cùng submission cùng payload không nhân History.
Payload conflict không ghi đè; rollback và chuyển retry/DLT theo phân loại lỗi.
ProcessedEvent + History + Completion + count projection commit trước Kafka ACK.

Retake tạo History mới, không nhân Completion. Quiz/Test không tạo Completion.
Event đến lệch thứ tự giữ snapshot của lần hoàn thành sớm nhất theo completedAt,
tie-break bằng submissionId. Thay current setup không gán lại Course/Level lịch sử.

CourseProgress trong DB chỉ giữ count projection, không nhân bản denominator
sống của Content. API lấy tổng tất cả persisted Lessons qua Content owner API,
kể cả unpublish; Course rỗng trả null, nguồn lỗi trả 503. Count lớn hơn denominator
được coi là inconsistency cần repair, không clamp hoặc bịa phần trăm.

Dashboard lấy setup qua User owner API và History của principal. Có tối đa 5
recentHistory; chưa setup thì progress=null, giữ lịch sử cũ. Statistics sáu metric,
source API và repair runtime đầy đủ tiếp tục #31/#36; không suy từ foundation.

Baseline DD/API giữ nguyên; title PROPOSED còn trong schema máy đọc là nhãn lịch sử
theo ghi nhận giữ nguyên bytes v0.1. Bản Word W3-02/03 local được bổ sung appendix
triển khai, không ký duyệt hoặc đổi approved baseline thay thành viên khác.
