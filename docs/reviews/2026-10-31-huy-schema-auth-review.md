# Chuẩn bị review #20/#42 cho Huy — 31/10/2026

Đây là static review do Codex hỗ trợ Huy, chưa phải quyết định `Đồng ý` của Huy
hoặc chấp thuận của Khoa/Hưng. Không đổi DD/API approved, không tự tick gate review.

## Phiên bản được đọc

- Huy foundation code: `2825b85`; hồ sơ publication: `0348a94`; Draft PR #43.
- Nhánh Khoa đã có trong workspace: `bf183461e5647ad83c7ec16dc6c7d1953379b10e`.
- Nội dung live #20/#42 và trạng thái PR #43 được đọc ngày 31/10 qua GitHub API.
  #20/#42 đang OPEN; PR #43 OPEN/Draft, chưa merge tại thời điểm đọc.
- W3-03 Word local và migration SQL của đúng owner. Review này không chạy lại
  suite Khoa hoặc tự coi source Khoa đã được peer approve.

## #20 — logical dictionary so với physical implementation

| Nội dung | Đối chiếu source | Kết luận cần xử lý |
|---|---|---|
| fullName nullable/max100 | User `learner_profile.display_name varchar(100)` nullable | Mapping tên khác nhưng phù hợp API; Khoa/Huy xác nhận |
| Vocabulary normalized key | Content UNIQUE(lesson_id,word_normalized,meaning_normalized) | Map logical normalized_word/normalized_meaning sang tên physical; có meaning |
| MODULE.published | `course_module.published NOT NULL DEFAULT false` | Có trong source Khoa |
| LearningContent | Một bảng `learning_content`, type GRAMMAR/LISTENING/READING, text/resource_url và CHECK | ERD mới cần thể hiện entity này; availability là DTO derive |
| Child cardinality | Parent FK NOT NULL; không ép parent phải có ít nhất một child | Parent 1 → child 0..*, không phải child tối thiểu 1 |
| Revoked token | Word mô tả revoked_token_id/user_id/jti VARCHAR/exp/revoked_at; V1 User chỉ `jti UUID PK, expires_at` | GAP cần Khoa/Huy chốt mapping/ý nghĩa trường bị lược; chưa sửa schema User hoặc claim match hoàn toàn |
| Assessment Questions/options | Logical QUESTION/QUESTION_OPTION tách bảng; physical `question.definition` JSON TEXT | GAP cần quyết định giữ physical JSON và cập nhật logical mapping, hoặc migration V2 normalize; không tự chọn thay nhóm |
| Saved answers | Logical SUBMISSION_ANSWER có saved_at/unique; physical `submission.answers` JSON full-set | GAP cần xác nhận metadata saved_at và mapping; validation uniqueness hiện ở application |
| Result fields | Logical fields điểm/time; physical metadata + payload JSON | DTO/behavior có tests; không khẳng định tất cả field có cột SQL tương ứng |
| Quiz/Test lesson snapshots | Assessment/History lesson_id nullable; Exercise bắt buộc lessonId | Có constraint/validation owner; không ép Quiz/Test luôn có Lesson |
| CourseProgress | Logical có progress_id/total_lessons/progress_percent; physical dùng composite key + count/updated_at, denominator đọc Content | GAP về physical projection; không gán nhầm “đủ cột logical”; source lỗi không thành 0 |
| External refs | User/Course/Level/Lesson nguồn không FK hoặc SQL xuyên service | Giữ boundary; event nguồn được Assessment kiểm trước khi phát |

Tài liệu mapping đã ghi rõ các khác biệt, thay vì bổ sung cột vào DB của người khác.
Huy có thể đề xuất giữ mapping JSON cho foundation, nhưng quyết định schema/dictionary
phải được Khoa/Huy ghi nhận trong #20. Nếu chọn normalize, làm migration version mới
và test dữ liệu hiện có; không sửa V1 đã chạy ở môi trường dùng chung.

ERD Content mới trong `docs/architecture/diagrams/content-physical-erd.*` bám source
Khoa tại SHA trên, có LearningContent và 0..*. Đây là artifact để review, không là
bằng chứng đã được nhóm chấp thuận. W3-03 local có phụ lục hình, giữ lịch sử cũ.

## #42 — so contract approved và runtime handoff

| Điểm review | Đã đối chiếu | Còn cần chính Huy/nhóm xác nhận |
|---|---|---|
| Session request | v0.1 chỉ token; proposal v0.1.1 bắt buộc token + requestMethod, unsafe có csrfSessionId/csrfToken | Đây là breaking change; không tự gọi tương thích ngược |
| Caller | Gateway `api` gọi session validate; Assessment/Progress gọi owner setup/context riêng | Hai service Huy không tự thay Gateway kiểm session hoặc default requestMethod=GET |
| Service JWT | RS256, issuer group7-service:<id>, audience owner, expiry/iat/jti và chữ ký | Cần trao public key và chạy với stack Khoa thật |
| Gateway assertion | issuer group7-gateway, key api; Huy kiểm audience, method/path, user/role/sessionJti | Negative tests có evidence; còn LIVE routing/CSRF/revocation qua Gateway |
| RBAC/ownership | Admin/Learner tách route, resource owner lấy principal, không tin browser userId | Có service/HTTP self-tests; chưa phải full session/security acceptance |
| Fail closed | Huy xử lý dependency không sẵn sàng bằng 503 ở các đường đã test | Không suy unit/stub tests thành outage matrix/network/pool/key rotation đầy đủ |
| Rollout | Khoa có đề xuất triển khai/rollback User+Gateway theo cặp | Huy/Khoa chọn version, cặp image và rollback; Hưng ghi baseline thực tế |

**Kết luận kỹ thuật:** cơ chế caller/assertion của code Huy phù hợp với runtime
handoff được đọc. Proposal session input vẫn là breaking so với approved v0.1.
Chưa có đủ evidence để đóng #42 hoặc tự phê duyệt rollout. Huy cần đọc bảng này,
chọn `Đồng ý`/`Yêu cầu sửa`, ghi SHA đã review; Khoa phản hồi, Hưng ghi quyết định.

## Nội dung Huy có thể dùng khi phản hồi

Không đăng nguyên văn như approval nếu bạn chưa đọc và chưa chọn phương án.

> Tôi đã xem review kỹ thuật tại PR #43, source Huy 2825b85 và source Khoa
> bf183461. Về #20, tôi đề xuất [giữ JSON/count projection và cập nhật logical
> mapping / normalize bằng migration V2]. Các khác biệt RevokedToken, saved_at,
> Question/Option/Answer và CourseProgress cần Khoa/Huy chốt rõ.
> Về #42, tôi [đồng ý hướng proposal / yêu cầu sửa các điểm ...]. Token-only v0.1
> không tương thích requestMethod bắt buộc; chưa coi proposal đã thành baseline.
> Đề nghị Khoa xác nhận cặp User/Gateway và public keys để chạy integration,
> Hưng ghi version/rollout sau quyết định nhóm. LIVE integration/peer review còn mở.
