# Khoa — bàn giao backlog User/Content/Gateway, 01/11/2026

**Kết luận:** phần kỹ thuật của Khoa đạt trong môi trường TEST. Cần review bản
sửa mới, quy trình merge và nghiệm thu frontend/consumer theo owner trước khi
đóng các issue tương ứng. Không xác nhận toàn MVP hoặc thay reviewer ký duyệt.

Source xuất phát `5fe5f39bd35bc67c1bac2abcff96d798b9febdd0`; nhánh
`feature/khoa-backlog-w7-w8`. Checkout W6 giữ nguyên. Assessment/Progress dùng
checkout riêng tại `413a9297d4d63f61b0ad4b81a0c1f1e7a9370abd` (PR #44, gồm
Assessment DB503 fix); không merge PR #43/#44 để chạy. Image ID, runner SHA,
fixture, DB/migration và kết quả nằm trong [manifest](../evidence/khoa-backlog-2026-11-01/manifest.json).

## Bản sửa và giao diện

User/Content `Db.tx` trước đây chỉ bắt `DataAccessException`; lỗi mở transaction
`CannotCreateTransactionException` thoát ra HTTP500. Đã tái hiện riêng từng
service: 4 ca/service, 2 FAIL expected503/actual500. Bản sửa chỉ chuyển lỗi mở
transaction sang sanitized503 `DEPENDENCY_UNAVAILABLE`, trước khi chạy callback.
Unexpected rollback khác vẫn500; duplicate write vẫn409. Sau sửa cả8 ca PASS.

Public API v0.1, internal baseline0.1.1, 63 route mappings và V1 migrations giữ
nguyên. User/Gateway vẫn dùng token + requestMethod và CSRF cho unsafe methods.
Thay đổi này không tạo backward compatibility token-only hoặc baseline auth mới.

## Đối chiếu backlog và điều kiện bàn giao

| PB / issue | API và evidence phía Khoa | Phần còn lại / phối hợp |
|---|---|---|
| PB01/02 — #13/#41 | Auth/register/login/logout, Profile; role escalation bị400, CSRF403, revoked/BLOCKED401; [core](../evidence/khoa-backlog-2026-11-01/core.json), [gateway](../evidence/khoa-backlog-2026-11-01/gateway.json) | Huy review thay đổi mới; Hưng nghiệm thu UI |
| PB03 — #13/#31 | Course/Level write/read, sai Level409, không Goal, đổi Course giữ collection, persistence; core + post-restart | History cũ có dữ liệu thật cần event/Progress của Huy |
| PB04/09/10 — #13; FE #24/#28 | Admin User/hierarchy/content, Vocabulary/Grammar/Reading/Listening UNAVAILABLE; publish readiness thật, draft khi409, DELETE hierarchy405 | Hưng khép UI và QA; không coi Lesson GET là completion |
| PB05 — #14; FE #32 | `/me/vocabulary`: paging, isolation,8 save đồng thời không trùng, source-update, hidden parents, đổi Course, remove lặp giữ nguồn | UNAVAILABLE khi nguồn mất là unit-only; FK live vẫn giữ, không ép xóa nguồn để giả fixture |
| PB13 — #14; FE #29 | Admin XLSX/DOCX, templates; 413/415/422/409, macro giả đuôi, corrupt, rollback;2 import đồng thời có đúng1 winner/2 rows | Hưng tích hợp UI lỗi/summary; Huy review/merge #14 |
| PB08/12 — #31; Huy #34/#35/#36, FE #38/#39 | User setup/cohort + Content contexts; caller/UUID/paging/count; Progress thật đọc denominator/setup/empty history; [owners](../evidence/khoa-backlog-2026-11-01/owners.json) | Huy xác nhận semantics, Statistics consumer và event projection có dữ liệu; Hưng UI |
| PB06/07/11 — #21, hỗ trợ owner Huy | Gateway auth/role/CORS/Idempotency-Key; Exercise start/save/submit/retry/retake với Assessment thật | Quiz/Test/Admin Assessment đầy đủ vẫn theo owner, không suy từ Exercise |
| Xuyên suốt — #41, QA #40 | [faults](../evidence/khoa-backlog-2026-11-01/faults.json), [session](../evidence/khoa-backlog-2026-11-01/session.json), [operations](../evidence/khoa-backlog-2026-11-01/operations.json), [audit](../evidence/khoa-backlog-2026-11-01/audit.json) | Hưng/Huy review bản hiện hành và full QA W9; production giữ HOLD W10 |

## Kết quả quan sát

- `mvnw.cmd -B verify`: **63 PASS,1 opt-in LIVE SKIPPED**,0 failure/error.
  Gồm User/Content Testcontainers PostgreSQL với HTTP owner doubles và8 ca
  transaction fault MVC/doubles mới. SDK lặp theo service; không phải63 nghiệp vụ
  độc lập. [Chi tiết suite](../evidence/khoa-backlog-2026-11-01/maven.json).
- LIVE Docker: core153, owner APIs40, network/pool33, Gateway32 và audit29 checks
  PASS. Mỗi nhóm gồm HTTP requests/assertions; không cộng thành số test JUnit.
- Bốn PostgreSQL17.11 riêng;5 application images build từ owner contexts riêng.
  Gateway18080; Assessment8090; Progress8091. Kafka tắt, không WireMock/demo.
- DB network disconnect: source lỗi trả503, không giả Progress=0, write không
  persist; reconnect khôi phục. Với User DB lỗi, Gateway từ chối vì validate;
  probe riêng vào Progress dùng signed TEST Gateway assertion để chứng minh
  chính consumer xử lý lỗi User source, không lẫn hai đường gọi.
- Pool: lock test giữ đủ10 Content runtime connections; request tiếp theo503,
  không ghi Course; unlock khôi phục và request mới thành công.
- Session fault: CSRF/session sai403; kho session lỗi503; Course không đổi,
  khôi phục quyền và tạo Course thành công. Cookie/JWT không lưu trong report.
- Backup/restore mọi bảng User/Content sang DB test mới cùng cluster: rows/digests,
  constraints, owners/grants, Flyway PASS; restart DB/app PASS. Profile, setup và
  collection được đọc lại sau restart. Không gọi đây là disaster recovery đa máy.
- User/Content runtime không superuser/CREATEDB/CREATEROLE/schema CREATE; DB không
  publish port. App non-root, chỉ mount readonly private key của chính nó; Gateway
  không nhận DB secret. Windows directories và14 secret files có ACL giới hạn
  current user/SYSTEM/Administrators; kiểm tra tĩnh, không impersonate tài khoản khác.

Lượt core đầu FAIL do harness dùng lại CSRF sau logout: backend trả403 đúng.
Đã sửa harness refresh CSRF và chạy fixture mới đạt153 checks; giữ
[report FAIL lịch sử](../evidence/khoa-backlog-2026-11-01/historical-core-harness-fail.json).
Đây không phải lỗi nghiệp vụ được sửa. Bằng chứng W6 và deadline lịch sử giữ nguyên.

## Nghiệm thu và trạng thái

- #14: kỹ thuật PASS, chờ peer review/merge và UI #29/#32.
- #31: API nguồn và Progress reads/outages PASS; chờ Huy acceptance/Statistics;
  Kafka/event history có dữ liệu chưa được nghiệm thu.
- #41: phần test/runbook Khoa PASS; chờ review mới và QA nhóm #40.
- Huy/Hưng đã xác nhận W6 theo hồ sơ relay trước đây. Xác nhận ấy không áp dụng
  tự động cho bản sửa User/Content này hoặc PR #44.
- W6-02 được Khoa chuyển Done → Review/Test lúc17:00 ngày01/11; hiện6/6,
  dueComplete=true. Giữ trạng thái đang quan sát và evidence cũ; không tự suy ra
  lý do reopen hoặc tự chuyển Done trở lại.
- Chưa merge/deploy production, chưa tự ghi actual hours/capacity. W7/W8 vẫn là
  mốc kế hoạch; các kiểm thử trong tài liệu thực hiện ngày01/11, không backdate.

Lệnh chạy, keys/URL/fixture và cách stop/restore:
[runbook backlog](../runbooks/khoa-backlog-w7-w8.md).
