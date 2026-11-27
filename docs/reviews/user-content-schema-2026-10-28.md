# Khoa: đối chiếu User/Content schema — 28/10/2026

Rà soát theo yêu cầu Khoa, từ backend `56450fc`, Drive W3-03 cập nhật 27/10 và approved API v0.1. Phạm vi là source/migration/DTO; không thay Huy nghiệm thu schema Assessment/Progress hoặc xác nhận migration đã chạy trong phiên này.

| Logical/API | Physical User/Content | Kết luận source |
|---|---|---|
| User `fullName`, nullable, max 100 | `learner_profile.display_name varchar(100)` nullable; profile maps `display_name` | Khớp; không đổi thành NOT NULL |
| Vocabulary normalized unique tuple | `UNIQUE(lesson_id,word_normalized,meaning_normalized)` | Logical `normalized_word/normalized_meaning` ánh xạ `word_normalized/meaning_normalized`; gồm meaning |
| Module published | `course_module.published boolean NOT NULL DEFAULT false` | Đã có trong migration/entity/API mapping |
| Grammar/Reading text | `learning_content.type`, `text`, `resource_url`; CHECK bắt buộc text cho GRAMMAR/READING | Đã có; entity discriminator là một bảng, không cần ba DB/table riêng |
| Listening resource/availability | `resource_url` HTTPS; `availability` suy ra ở DTO | Không thêm cột availability persisted mặc định |
| Saved Vocabulary | `user_vocabulary` trong Content DB; UNIQUE(user_id,vocabulary_id) | user_id là external UUID, vocabulary_id FK local |
| Course/Module/Lesson | FK local ON DELETE RESTRICT, order_no > 0, normalized title/order uniqueness | Không thêm DELETE/soft-delete FR-17 |
| Course/Level setup | `learning_setup` trong User DB; IDs Course/Level external | Validate qua Content API trước write; không FK/SQL xuyên DB |
| Revocation/CSRF session | `revoked_token`, `spring_session`, `spring_session_attributes` | User sở hữu; cấu hình/caller compatibility còn #42 |

Không chỉnh bytes V1 migration đã có. Timestamp, grant, index và CHECK được đối chiếu trực tiếp với `backend/services/*/src/main/resources/db/migration/V1__*.sql`.

ERD source hiện hành: [docs/erd.md](../erd.md) đã có `LESSON ||--o{ LEARNING_CONTENT`, `LESSON ||--o{ VOCABULARY`, và saved relation. Đây là ERD trong repo; chưa thay hình trong Word W3-03. Huy vẫn cần cập nhật/duyệt hình, physical schema Assessment/Progress, engine và migrations ở #20.

#31: User cohort source bao gồm mọi LEARNER kể cả BLOCKED, totalLearnersGlobal độc lập filter; Content totalLessons đếm mọi persisted Lesson, kể cả unpublished. Các nguồn là HTTP owner APIs, không join DB chéo. Paging ổn định theo UUID; generatedAt không là snapshot transaction xuyên nhiều page/service. Không tự diễn giải source lỗi thành số 0. Đầu vào caller/context và handoff ở [internal auth review](../contracts/internal-auth-review-2026-10-28.md).

Static mapping hoàn tất phía Khoa. Integration tests PostgreSQL và peer acceptance vẫn phải có bằng chứng riêng; không đóng #20/#31 chỉ từ tài liệu này.


Bổ sung ngày 28/10, Java 25: PostgreSQL integration tests đã PASS, gồm cohort paging/filter/invalid UUID/global count/BLOCKED và Content context paging/hidden denominator. [Checkpoint hiện hành](2026-10-28-khoa.md) ghi SHA và môi trường; nhận bàn giao và acceptance của Huy vẫn OPEN. Nội dung mapping phía trên là audit lịch sử, không thay schema Assessment/Progress.
