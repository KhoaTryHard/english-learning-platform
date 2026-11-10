# Khoa — review backlog ngày 09/11/2026

**Checkpoint mới10/11:** đã review phần W8 mới của PR47; xem
[report PR47](2026-11-10-pr47-review.md). Finding49 vẫn còn, có thêm finding52
Admin Question chặn điểm dương hợp lệ0.001. Nội dung09/11 bên dưới giữ lịch sử.

**Kết luận: CHANGES_REQUESTED.** Reviewer: Khoa, có hỗ trợ Codex. Một lỗi frontend
mới được tái hiện tại [#49](https://github.com/KhoaTryHard/english-learning-platform/issues/49);
PR #43 còn thiếu bản sửa DB503 đã có ở PR #44. Không có approval được tạo thay
Huy/Hưng. Review này đánh giá phạm vi Khoa và các luồng tích hợp được nêu dưới đây.

## Phiên bản và phạm vi

| PR | SHA review | Phạm vi/kết luận |
|---|---|---|
| #22 | `f386170de0a788d2e21f1ece9b782aaa0cc52f47` | Learner Auth/Profile/Setup/Lesson, saved vocabulary, Attempt/Result/client; REQUEST_CHANGES vì #49 |
| #46 | `1e11c6d700a5f5e118f086ca23f9c5c66a3be031` | Delta trên #22: Admin User/Content, Import, Admin session, vocabulary; source/backend giao tiếp đúng trong ca đã chạy; kế thừa #49, REQUEST_CHANGES |
| #43 | `c1a2a074a4cc6c0d3d0bdf04ef77540a530c5930` | Giao tiếp Content/Assessment/Gateway và nguồn Progress; REQUEST_CHANGES cho gate DB503 cần đưa fix #44 vào phiên bản bàn giao |
| #44 | `413a9297d4d63f61b0ad4b81a0c1f1e7a9370abd` | Khoa là tác giả bản sửa; technical verification/self-review, cần Huy review độc lập |
| #47 | `ec373bdca31d10c9b1fe29e99b8137b776427d05` | Chỉ kiểm blob Attempt kế thừa #49; không review toàn PR hoặc chạy W8 browser suite |

PR22/46/47 cùng blob AttemptPage `7dd9f0d46c6152bae9b49b4cf198047dbc232eaf`.
Backend runtime dùng develop `af92253a95cf39fbdbfcbada2cfdd47fd413534d` và owner
`413a929...`. Runtime đã gồm fix #44; không gọi kết quả này là retest head #43
chưa có fix. Source hai owner service giữ ở checkout riêng; không merge để test.

## Spec/architecture và bằng chứng

Đối chiếu public v0.1, internal0.1.1, SRS/UC-AC0.4 và DD0.2. Các luồng đã rà:
cookie/CSRF/session, whitelist và role/ownership; Course/Level không Goal;
hierarchy/readiness; Content sở hữu My Vocabulary/import; Assessment sở hữu
Attempt/Result; Progress đọc User/Content qua HTTP và nhận event. Không có SQL
business xuyên database trong các fixture/probe của lượt này.

- Client dùng cookie/CSRF, không tự retry mutation; retry start giữ key sau mất
  response, retake key mới. Answer queue serialize full set, ACK trước submit.
  **Không đạt** recovery khi GET thành công sau PUT thất bại (#49).
- Admin forms dùng whitelist; không tạo Vocabulary thủ công hoặc Delete hierarchy.
  Import truyền đúng file/parent/order và giữ rejection metadata; không khẳng định
  rollback khi response network/5xx không có outcome. Backend rollback được Khoa
  nghiệm thu riêng bằng PostgreSQL fault thật bên dưới.
- Readiness của Assessment chỉ đọc DB Assessment, không callback Content; chỉ
  caller content được phép. Gateway assert đúng method/path, role và identity;
  các bằng chứng negative/security trước đó được kế thừa đúng phạm vi.
- Source Progress đọc setup/context, không fake0 khi nguồn lỗi. Statistics route
  chưa có trong owner này; authenticated Admin probe trả404. Đây là dependency
  #36, không phải Statistics PASS hoặc một lỗi mới của source Khoa.

## Kiểm thử mới trong lượt review

| Nhóm | Kết quả | Phạm vi |
|---|---|---|
| PR22 `npm run check` | 22 unit/UI PASS, lint/build/contract guard PASS | Suite sẵn có; không tính là 22 LIVE cases |
| PR46 `npm run check` | 46 unit/UI PASS, lint/build/contract guard PASS | Kế thừa/bao gồm suite trước; không cộng thành 68 ca độc lập |
| PR22 browser MOCK | 6 PASS | Desktop/mobile |
| PR46 browser MOCK | 8 PASS | Desktop/mobile, Admin/Learner |
| PR22 browser LIVE first slice | 2 PASS | Register/Login/Profile/Setup/Lesson/save/remove/logout |
| PR46 browser LIVE W7 | 6 PASS | Admin User/Content/Import, saved source/đổi setup, Exercise0 điểm/Kafka/retake, Quiz/Test resume/timeout |
| Import owner HTTP LIVE/PostgreSQL | 22 checks PASS | Hai INSERT thật, fault dòng2 rollback dòng1; 409 createdCount0/no rows; recovery201/2 rows; DB down503 và phục hồi giữ dữ liệu |
| Regression #49 bổ sung | FAIL ở cả PR22 và PR46 | Một lỗi chung; React/MSW HTTP double, không phải LIVE fault |

TEST project mới `khoa-backlog-review-20261109`, volumes mới; năm image service
đã có được đối chiếu source tree, PostgreSQL17.11 và Kafka4.0.0. Vite thường,
không demo cho LIVE. Manifest ghi image IDs, migration, fixture, thời gian và
checksums. Không chạy lại Maven backend toàn bộ vì application source không đổi;
kế thừa W8 evidence theo đúng SHA/tree.

Probe Import lần đầu thiếu quyền USAGE trên sequence tạm, gây503 do fixture ACL.
Đã dọn trigger, cấp quyền sequence đúng runtime role, tạo fixture mới và chạy lại
PASS. Không coi lỗi chuẩn bị fixture này là defect application. Trigger/function/
sequence tạm đã được gỡ; dữ liệu tạo qua API và volumes được giữ.

## Findings và handoff

1. **P1 / frontend / Hưng:** #49 — giữ trạng thái dirty/ACK sau lỗi lưu đáp án;
   GET thành công hiện xóa lỗi và thông báo đã lưu sai. Xem [finding](findings/high.md).
2. **P1 / Assessment / Huy review:** head #43 thiếu mapping
   CannotCreateTransactionException→503. Fix ở #44 đã kiểm kỹ thuật; Huy review
   và đưa vào baseline bàn giao trước khi nhận #43. Không tự approve #44.

[Ma trận #31](../../docs/handoffs/khoa-consumer-acceptance-2026-11-09.md) đã sẵn;
#31 giữ Open5/7 chờ Huy. #33: Khoa chấp nhận phần backend Import failure/rollback
trên evidence mới; frontend PRs còn #49 và merge/retest. #40 tiếp nhận smoke hẹp
đã PASS; Statistics/W8UI/accessibility/QA nhóm còn mở. Actual/capacity do owner xác
nhận, không lấy thời gian test làm giờ công. Production HOLD W10.

## Tái hiện

Copy [Attempt regression](fixtures/AttemptPage.resync-regression.test.tsx) vào
`frontend/src/features/assessment/AttemptPage.review.test.tsx` tại đúng snapshot,
chạy `npm run test -- --run src/features/assessment/AttemptPage.review.test.tsx`.
Assertion hiện FAIL khi UI hiển thị đã lưu dù server answers=[]; test phải PASS
sau bản sửa. Fixtures reviewer không nằm trong application frontend.

[Import probe](fixtures/import-rollback-probe.py) chỉ chạy ở project TEST của lượt
này sau khi nạp env theo runbook, đặt KHOA_REVIEW_ROOT và ADMIN_EMAIL đúng Admin
TEST. Fixture cấp USAGE sequence tạm cho content_runtime, không tăng quyền runtime
vĩnh viễn. Không copy probe vào migration hoặc môi trường dùng chung.

[Manifest và reports](../../docs/evidence/khoa-backlog-review-2026-11-09/manifest.json)
· [Runbook W9](../../docs/runbooks/khoa-w9.md).
