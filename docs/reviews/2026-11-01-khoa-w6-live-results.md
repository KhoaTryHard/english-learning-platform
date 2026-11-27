# W6-02 — kết quả LIVE của Khoa ngày01/11/2026

Khoa/Codex đã chạy Gateway/User/Content/Assessment thật và PostgreSQL17.11 trên
host. **Chưa đủ điều kiện Done:** readiness trực tiếp khi DB Assessment ngừng
trả500 thay503; quyết định auth và peer review còn chờ người phụ trách.

## Source và môi trường

Khoa ban đầu `626b3d4853293504f1af6ad3483a238d01af9bf7`; Gateway sửa preflight
`85462cf57945de58a9f79d5d22f7d2a542bcd05a`; Assessment Huy giữ nguyên
`c1a2a074a4cc6c0d3d0bdf04ef77540a530c5930`. User/Content dùng JAR build từ
source ban đầu; report runtime ghi source và JAR hash riêng từng service.

Docker engine vẫn không khởi động được; không thao tác lại socket bị policy
chặn. Phương án HOST dùng ba PostgreSQL17.11 clusters riêng15541/15542/15543,
FlywayV1 thành công, migration/runtime roles của User/Content tách biệt; bốn
JAR nghe loopback. Kafka tắt. Không dùng stub/H2 trong các luồng LIVE này.
Docker image/network acceptance giữ NOT_RUN.

## Kết quả

| Ca | Expected và actual | Kết luận |
|---|---|---|
| Seed | Sáu Lesson draft; năm Exercise, bốn Exercise ready có câu hợp lệ, một draft, một Lesson thiếu mapping | PASS, seed qua Gateway |
| Online | Readiness false/publish409/draft; ready publish200/Learner đúng exerciseId; caller api403, chữ ký sai401 | PASS, 30 checks |
| Assessment process ngừng | Direct mất kết nối; publish503; Lesson giữ draft | PASS, 7 checks |
| Assessment DB ngừng | Direct expected503, actual500 | **FAIL**, giữ nguyên oracle/report |
| Gateway khi DB Assessment ngừng | Publish503 DEPENDENCY_UNAVAILABLE; Lesson vẫn draft | PASS riêng phía Gateway/Content |
| Recovery | Hai Lesson lỗi trước vẫn draft; recovery publish200 và Learner GET đúng Exercise | PASS, 16 checks |
| Retry/retake sau Gateway fix | Cùng key giữ attemptId/snapshot, key mới tạo attempt; submit và terminal retry giữ attempt cũ | PASS trong bộ40 checks |
| CORS/RBAC/session | Preflight204 đúng origin/credential/header; foreign origin403; role403; CSRF403/no attempt; BLOCKED401, replay logout401 | PASS trong cùng bộ40 checks, không cộng lần hai |
| User session repository fault | SELECT lỗi503, CSRF/session403, không ghi Course, khôi phục quyền và request hợp lệ | PASS |

Retry trước sửa Gateway FAIL ở preflight200 thiếu CORS headers. Sau sửa,
attempt đầu và cùng key đều `3446b55f-3785-4e6e-9b42-ce0b934b4a35`; key mới tạo
`2c863a1c-c564-48a4-8420-9d30e5543d6d`. Bộ test xác minh đúng hai attempts,
save false/submit100%, không lộ answer key. Không công bố Cookie/JWT/password.

Recovery thành công không xóa lỗi direct readiness khi DB ngừng. Các report
PASS riêng không được gom thành full LIVE PASS.

## Bản sửa Gateway

Spring MVC nhận CORS preflight rồi kết thúc trước khi gọi controller; vì vậy
OPTIONS thật trả200 không có Access-Control-Allow-* dù test gọi trực tiếp
`Gateway.proxy` trước đây PASS. `GatewayPreflight` filter đưa OPTIONS public
qua đúng policy origin/route có sẵn trước MVC; không nới origin hoặc role.

Hai regression qua server thật đã tái hiện FAIL trước sửa. Sau sửa,
Gateway **15 tests PASS**, 0 fail/error/skip, gồm hai regression đó. Verify lần
đầu vướng Maven repackage vì JAR đang được process test giữ; đã dừng đúng
Gateway test, package lại thành công với skipTests. Không tính packaging là
lượt test mới. Contract frozen hashes/63 mapping giữ PASS.

## Bàn giao Huy và gate

Gói kỹ thuật gồm [public keys TEST](../fixtures/w6-live-public-keys/README.md),
[caller/auth](../contracts/internal-auth-review-2026-10-28.md),
[Compose8090](../runbooks/khoa-w6-live.md), [lệnh HOST](../runbooks/khoa-w6-host-live.md),
[defect cần Huy sửa](2026-11-01-assessment-db-outage.md) và
[evidence tổng hợp](../evidence/khoa-w6-host-live-2026-11-01.json).
Private keys giữ tại service local; key TEST không phải production baseline.

- Checklist1–2: hoàn tất phần review, có bản sửa preflight và regression mới.
- Checklist4: gói caller/URL/public keys/commands đã có để công bố tại #25 và
  W6-02. Bàn giao kỹ thuật không có nghĩa Huy đã peer approve.
- Checklist5: đã chạy LIVE; còn **FAIL Assessment DB outage**, owner Huy. Chạy
  lại ở SHA Huy sửa; giữ incomplete đến khi cả direct503 và publish503 đạt.
- Checklist3: #42 còn chờ phản hồi thực của Huy/Khoa và Hưng ghi version,
  baseline, cặp rollout/rollback; không tự đổi proposal thành approved.
- Checklist6: PR #1 chưa có peer review; giữ Draft/Open, không merge.

Đã dừng bốn JAR và ba clusters sau kiểm thử; dữ liệu/report được giữ. Estimate
cũ không chuyển thành actual hoặc capacity. Không mở rộng W7/schema/Kafka E2E.

Các checkpoint trước giữ nguyên như lịch sử; bản này thay trạng thái
`NOT_RUN_DOCKER_ENGINE_UNAVAILABLE` bằng kết quả HOST cụ thể ở từng ca.
