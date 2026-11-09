# W6-02 — hoàn tất ngày01/11/2026

**DONE — nghiệm thu W6 trên HOST, với phản hồi Huy/Hưng do Khoa chuyển lại.**
Phạm vi Done là sáu checklist W6-02; production deployment và merge workflow
của các PR được giữ riêng theo quyết định nhóm.

## Sáu mục nghiệm thu

| Checklist | Bằng chứng hoàn tất |
|---|---|
| 1. Review User/Content/Gateway sau chuyển cây | Ba owner-only builds,63 route mapping, runtime historical29/10 và LIVE mới được ghi đúng source/môi trường |
| 2. Route/role/header/CORS/401/403/503 | Gateway15 tests PASS sau preflight fix85462cf; LIVE credentialed preflight, RBAC, CSRF, BLOCKED, replay logout và session fault PASS |
| 3. Compatibility/version/rollout auth | [Baseline0.1.1](../contracts/internal-v0.1.1.yaml), breaking token+requestMethod/CSRF, pair User/Gateway; Huy đồng ý và Hưng chốt W6/HOLD-W10, do Khoa chuyển lại trong phiên |
| 4. Bàn giao cho Huy | [Public keys TEST/caller/URL/commands](../fixtures/w6-live-public-keys/README.md), runbooks và reports đã công bố; private keys giữ local |
| 5. Publish/readiness/retry/retake thật | Fixture mới, Assessment đã sửa413a929: online30, service-down7, database-down7, recovery16, retry/security40 checks PASS; DB ngừng direct503/publish503 và draft được giữ |
| 6. Peer review PR #1/nghiệm thu | Khoa xác nhận đang chuyển phản hồi thực của Huy: `approval` cho PR #1; [phiếu quyết định](../contracts/internal-auth-decision-2026-11-01.md) ghi nguồn relay, không giả native GitHub approval |

## Lỗi DB đã sửa và kiểm lại

Assessment không map `CannotCreateTransactionException`, nên lỗi mở transaction
trước query trả500. PR #44 chỉ bổ sung exception này vào handler dependency503,
giữ constraint409 và rollback khác500. Bốn regression HTTP: trước sửa1 FAIL,
sau sửa Assessment **35 PASS/0 fail/error/skip** (verify dùng H2/test doubles).

Source: Khoa `b8bdcfe350989e7c98075fa004890cc76a48b7f2`, application Gateway
fix85462cf; Assessment fix `413a9297d4d63f61b0ad4b81a0c1f1e7a9370abd` dựa trên
Huyc1a2a074. Lượt LIVE mới dùng JAR thật, ba PostgreSQL17.11 clusters và FlywayV1;
không WireMock/H2 trong các luồng LIVE. Mỗi JAR/process/source identity nằm ở
[evidence cuối](../evidence/khoa-w6-final-2026-11-01.json).

Report FAIL trước sửa giữ tại `w6-host-live-2026-11-01/database-down.json`;
report PASS mới tại [database-down cuối](../evidence/w6-final-2026-11-01/database-down.json).
Recovery không được dùng để xóa ca FAIL cũ; phiên bản mới có lượt kiểm lại riêng.

## Nguồn xác nhận và giới hạn

Khoa chuyển phản hồi Huy `đồng ý / approval / chấp nhận em sửa`, rồi xác nhận
**“Anh chuyển phản hồi thực tế của Huy”**. Sau khi trình version0.1.1,
breaking compatibility, pair release/rollback và W6 HOST acceptance/HOLD
production W10, Khoa chuyển xác nhận **“Hưng đã xác nhận, anh chuyển lại”**.
Đây là nguồn nghiệm thu nhóm được ghi lại; GitHub chưa có native review của
Huy/Hưng và không tạo review dưới tài khoản họ.

PR #44 là bản sửa Khoa chuẩn bị theo sự chấp nhận sửa do Huy chuyển qua Khoa;
không coi approval PR #1 trước đó là native approval cho PR #44. Hai PR tiếp
tục quy trình review/merge riêng; chưa merge trong phiên. W6 Done không tuyên
bố production hoặc Kafka/Progress E2E đã nghiệm thu.

Docker engine chưa sẵn nên image/digest/rehearsal deployment được HOLD W10.
Cặp User/Gateway TEST đã xác minh có hash trong phiếu baseline; rollout sau
phải đổi và rollback cùng cặp, không hạ CSRF/token-only. Approved v0.1 và
proposal giữ nguyên; bản0.1.1 mới chỉ thay metadata trạng thái/version so với
shape được review. `check-contracts.py` xác minh điều này và frozen hashes.

Bốn app/ba clusters đã dừng, dữ liệu/report giữ lại. Actual hours/capacity không
tự ghi thay owner; deadline30/10 giữ lịch sử, completion ghi ngày01/11 thực tế.
