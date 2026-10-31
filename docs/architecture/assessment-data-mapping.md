# Assessment: database và mapping triển khai

Owner: Huy. Nguồn: W5-02/#4, #20, #25; DD v0.2, approved API v0.1.
Huy chọn PostgreSQL 17 trong phiên triển khai. Peer review schema/ERD và quyết
định tương thích chung vẫn được theo dõi ở #20/#42.

| Logical data | Physical mapping V1 |
|---|---|
| Assessment | `assessment`: UUID, type, external course/lesson IDs, title, ready, time limit |
| Exercise cuối Lesson | `exercise_lesson`: lesson_id PK, assessment_id unique FK nội bộ |
| Question/Option/answer key | `question.definition`: JSON TEXT questionId/type/content/points/options/key |
| Attempt/Submission | `submission`: ID, user/assessment, key, started/deadline/status |
| Snapshot | `submission.snapshot`: JSON TEXT Question/key/points, Course/Level/time lúc start |
| Saved answers | `submission.answers`: JSON TEXT answer-set, full replacement |
| Idempotency | UNIQUE(user_id,idempotency_key); assessment ID là fingerprint của start không body |
| Start lock | `learner_lock`: user_id PK; serialize key reuse giữa các Assessment |
| Result | `result`: UUID, unique submission FK, user/time, payload JSON TEXT |
| Outbox | `outbox_event`: event/submission unique, payload, topic/key, status/retry/error |

User/Content IDs là external refs, không FK xuyên service. Snapshot không phải
bản sao sống của entity nguồn. Option/Answer/Result fields trong JSON được
validate ở application layer. Không có shared business entity/JAR.

JSON TEXT giúp đóng băng snapshot và build/test độc lập. Đây là mapping vật lý
cần review với W3-03; không khẳng định đã tạo nguyên xi các bảng QUESTION_OPTION
và SUBMISSION_ANSWER trong logical dictionary. Nếu nhóm yêu cầu normalize,
bổ sung migration version mới; không sửa migration đã áp dụng ở môi trường chung.

Result + Outbox + transition được ghi cùng transaction. SELECT FOR UPDATE trên
submission và UNIQUE result.submission_id chống submit/finalizer trùng.
Start khóa Assessment và per-user row; retry có key không gọi User/Content.
Key không được tái sử dụng cho Assessment khác.

Readiness chỉ đọc Assessment DB. Start mới kiểm tra Content visibility và User
setup bằng owner APIs, validate Level thuộc Course. Nguồn lỗi trả 503 và không
tạo submission. Event không chứa câu hỏi/đáp án chấm.

Progress/History/ProcessedEvent thuộc #34/#35. Kafka broker/relay thuộc #30;
Outbox PENDING không chứng minh event đã gửi. Không xác nhận nghiệm thu thay nhóm.
