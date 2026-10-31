# W5-02 — Phụ lục triển khai của Huy, 31/10/2026

Ghi nhận code và self-test, chờ peer review; không thay quyết định DD/API đã duyệt.
Nguồn: issue #4/#20/#25, phần foundation #16/#17; nhánh feature/huy-assessment-foundation.

## Kiến trúc đã triển khai

Assessment sở hữu Question, Attempt/snapshot/answers/deadline, Result và Outbox.
Progress sở hữu History, Completion, ProcessedEvent và count projection.
Hai service build độc lập bằng Java 25/Spring Boot 4.0.8; PostgreSQL 17, database và migration riêng.
Gateway assertion và service JWT theo runtime handoff của Khoa; version/compatibility #42 còn chờ quyết định nhóm.
Content gọi readiness Assessment; readiness chỉ đọc Assessment DB, không callback Content.
Start mới kiểm tra Content context và User setup. Retry key đã có giữ attempt và snapshot cũ.
Result và Outbox commit atomic. Relay giữ eventId và payload khi retry; publish xong mới mark SENT.
Progress validate event rồi commit ProcessedEvent/History/Completion/projection atomic; commit DB trước Kafka ACK.
Retry consumer 1s/5s/30s, DLT giữ key/payload/eventId; replay sau khi xử lý nguyên nhân.
Broker/topic/relay/consumer adapters đã có cấu hình foundation; Kafka E2E thật chưa chạy.

## Mapping dữ liệu triển khai

Assessment: assessment, exercise_lesson, question, submission, result, outbox_event và learner_lock.
Question/options/answer-key được lưu JSON TEXT trong question.definition; snapshot và answers là JSON TEXT ở submission.
Đây là mapping vật lý khác cách phân rã bảng logical; Khoa/Huy cần review với W3-03, không ghi đã normalize QUESTION_OPTION/SUBMISSION_ANSWER.
Progress: processed_event(event_id PK), learning_history(submission_id UNIQUE, result_id UNIQUE), lesson_completion(PK user_id/lesson_id), course_progress(PK user_id/course_id) và learner_lock.
Course/Level/User/Lesson nguồn là external refs; không FK hoặc SQL xuyên service.
CourseProgress lưu count projection; denominator gồm tất cả Lesson được đọc qua Content owner API khi query.
Exercise điểm 0 vẫn complete; retake thêm History nhưng không cộng trùng Lesson; Quiz/Test không complete Lesson.
History giữ snapshot Course/Level. Completion giữ lần hoàn thành sớm nhất nếu event đến lệch thứ tự.
EventId trùng hoặc submission trùng cùng payload không nhân dữ liệu; payload conflict rollback và yêu cầu review/replay có kiểm soát.

## Bằng chứng và phần còn mở

Migration, transaction, dedupe, concurrency, rollback và API/security được kiểm thử thủ công; source SHA và test report theo hồ sơ repository.
Owner APIs trong test dùng stub có ghi nhãn; không coi đó là Gateway/User/Content LIVE hoặc Kafka E2E thật.
Tích hợp nhóm, Docker/Kafka thật, Statistics đầy đủ, peer review/merge và nghiệm thu tuần vẫn theo issue liên quan.
Lựa chọn database và appendix là cập nhật của owner Huy trong phiên làm việc; chưa ký hoặc xác nhận thay Khoa/Hưng.
Không đổi trạng thái Trello hoặc đóng issue tự động vì đã bổ sung tài liệu.
