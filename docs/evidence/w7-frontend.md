# Hưng W7 — code, review và kiểm thử 03/11/2026 (UTC+7)

Frontend được kiểm tra tại `52f799107eb90c1951610fde587f71183f4d7f93` trên `feature/hung-w7`, kế thừa W6 `f386170` và scaffold/develop `9eff189`. Đây là implementation/self-QA của Hưng, chưa phải peer acceptance hoặc kết luận toàn tuần đã Done. W7 là 02–08/11.

Follow-up `b97517a` chỉ bổ sung assertion UI unit: Remove content=null lỗi 503 giữ quan hệ, retry 204 thì collection rỗng. Full check và MOCK được chạy lại; application và LIVE suite giữ nguyên từ `52f7991`, không gọi bổ sung unit là browser LIVE mới.

## Kết quả và phạm vi

| Lệnh/nhóm | Kết quả | Phạm vi thực tế |
|---|---|---|
| `npm run check` | PASS, 46 tests/11 files; lint, TypeScript/Vite build, contract/production guard | Unit/React UI/API client, MSW/mock upload ở unit; không phải 46 nghiệp vụ LIVE |
| `npm run test:e2e` | 8 PASS | Chromium desktop/mobile, MOCK: Learner regression + Admin CRUD/RBAC/import navigation |
| `npx playwright test -c playwright.live.config.ts w7.spec.ts`, bật hai cờ W7 | 6 PASS | 3 case × desktop/mobile; Gateway/User/Content/Assessment/Progress/PostgreSQL/Kafka thật |
| Self-review source, ảnh desktop/mobile, `git diff --check` | PASS | [Review và lỗi đã sửa](../reviews/2026-11-03-hung-w7.md), chưa có peer approval |

[Manifest có SHA, môi trường, thời gian, kết quả và hash](w7/manifest.json). File này chỉ công bố kết quả sau khi kiểm tra report PASS; raw traces/cookies/password không đưa vào Git.

LIVE Admin thực hiện tìm/sửa/chặn/mở Learner; tạo/sửa/publish Course/Module/Lesson; Lesson publish nhận 409 trước khi có Exercise ready và giữ trạng thái draft, sau đó mới publish được. Vocabulary sửa từ import; Grammar/Reading/Listening tạo/sửa/publish bằng payload theo loại. Kiểm tra template tải được; XLSX hợp lệ tạo 2 từ; duplicate/invalid bị từ chối; DOCX thiếu Title bị từ chối, file leading-empty hợp lệ tạo Lesson draft. Fixture gốc giữ nguyên, số bản ghi đích không tăng sau reject. UI 503/readiness failure, lỗi kết nối và import summary 413/415 có unit evidence, không gọi chúng là LIVE fault injection.

LIVE Learner thực hiện RBAC 403 tại API lẫn UI; save đồng thời không nhân đôi; nguồn Vocabulary sửa được phản ánh trong collection; unpublish parent/source vẫn resolve qua saved-only API, đổi Course/Level không mất collection và Remove thành công. Exercise start đã commit rồi cố ý làm mất response ở browser: UI không tự retry, bấm lại dùng cùng Idempotency-Key và cùng attempt. Exercise 0 điểm ghi completion qua producer Outbox→Kafka→Progress; retake tạo history mới nhưng completedLessons vẫn 1. QUIZ và TEST đều giữ false answer/deadline khi reload, timeout do server trả điểm 1/1, retake key/result mới và không có đáp án thì 0/1. Không tự chấm hoặc tăng progress ở frontend.

## Môi trường và tái hiện

Windows/PowerShell, Node 24.18.0, Chromium của Playwright; desktop 1440×1024, mobile iPhone 13 emulation 390×844. Vite thường trên 127.0.0.1:5173, không MSW, proxy Gateway 127.0.0.1:18080. Docker TEST riêng `khoa-backlog-hung-w7-20261103`; 4 DB PostgreSQL 17 tách biệt; Java 25 service images và Apache Kafka 4.0.0. Không dùng production hoặc thay dữ liệu nhóm.

- User/Content/Gateway: `feature/khoa-backlog-w7-w8` tại `c3e5e2f97cf771a7e3d1f6e0d2ab1ec0b957ff98` (PR #45).
- Assessment/Progress: `fix/khoa-assessment-db-503` tại `413a9297d4d63f61b0ad4b81a0c1f1e7a9370abd` (PR #44, dựa trên nhánh Huy). Không sửa source backend.
- Hai cây source được export riêng để build, không checkout/ghi đè nhánh của Khoa/Huy. Secret tạo cục bộ và giữ trong `.secrets`, không có trong manifest.
- Seed Course/Level theo runbook Khoa: Demo English/A1 và Backlog Course change TEST/A2. Fixture dưới `docs/fixtures`: vocabulary-valid.xlsx, vocabulary-invalid.xlsx, lesson-missing-title.docx, lesson-leading-empty.docx. Tài khoản Learner/nội dung mỗi test có hậu tố ngẫu nhiên.

Trước tiên provision stack theo `docs/runbooks/khoa-backlog-w7-w8.md` ở đúng SHA Khoa: đặt OpenSSL trên PATH, chạy `khoa-backlog-env.ps1` với HuySourceRoot/project/image tag riêng, build/up 5 service và DB, bootstrap Admin TEST `admin.hung.w7@example.com`, seed Course/Level. Giữ password trong `.secrets/admin_password`, không in ra log. Runbook gốc để Kafka disabled; **sau đó** thêm [overlay W7](../../frontend/tests/live/compose.w7-kafka.yaml) để bật broker, producer và consumer:

```powershell
# Trong cửa sổ PowerShell đã nạp env TEST và COMPOSE_FILE theo runbook Khoa.
$env:COMPOSE_FILE += ';<repo-Hung>/frontend/tests/live/compose.w7-kafka.yaml'
docker compose up -d kafka assessment-live progress-live
# Chờ health của Gateway và 5 services; broker topic/consumer phải sẵn.
```

Overlay dùng volume Kafka riêng; init container chỉ đặt owner của volume, broker chạy user 1000 và không mở cổng host. Không dùng root cho broker. Dữ liệu test cũ trong volume có thể được consumer xử lý khi bật Kafka; mỗi Learner test mới có collection/history riêng.

Trong `frontend`:

```powershell
npm ci
npx playwright install chromium
npm run check
npm run test:e2e
$env:GATEWAY_URL = 'http://127.0.0.1:18080'
$env:W7_STACK_ROOT = '<checkout-Khoa-TEST>'
$env:RUN_W7_LIVE = '1'
$env:RUN_W7_KAFKA = '1'
npx playwright test -c playwright.live.config.ts w7.spec.ts
```

Không bật RUN_W7_KAFKA nếu runtime vẫn disabled. Cờ mặc định skip bảo vệ khỏi ghi nhầm vào môi trường khác; LIVE chỉ được chạy vào stack TEST đã provision. Sau kiểm thử stop đúng project, giữ volumes và báo cáo; không dùng `down -v`.

## Giới hạn và trạng thái issue

- #28/#29: code W7, unit/MOCK/LIVE đã có; Khoa review và merge còn mở. Backend failure giữa transaction/rollback là evidence owner Khoa trước đó, không rerun thành browser LIVE mới trong đợt này.
- #32: source/update/hidden-parent/setup/unique LIVE đã chạy; paging 101 và `content=null`/Remove failure là UI unit. LIVE seed có 2 từ; không tháo FK/xóa source để dựng dữ liệu không có public API.
- #9: luồng Exercise/Quiz/Test và completion/retake qua Kafka có LIVE; vẫn cần Huy/Khoa review. Không suy thành broker outage/replay/DLQ/restart acceptance hoặc đóng toàn backend #30/#34/#35.
- #33: integration/self-review/evidence và rà remaining W8 đã có; peer review/merge và capacity/actual owner chưa xác nhận. Thời gian test là elapsed, không quy đổi thành giờ công actual.
- #10/#11 là tracking; Admin Assessment/Statistics và UI Progress/History/Dashboard toàn phần giữ W8 (#37/#38/#39/#12/#40). PR #22 đã retarget develop sau #18 merge; PR W7 nối trên W6, chỉ retarget develop sau khi nền merge và rerun regression bị ảnh hưởng. Không tự merge hoặc ký review thay người khác.

Không đổi Trello/Drive/approved contracts/CI-CD trong lượt code này. [Kế hoạch W5–W8](../planning/w5-w8-plan.md) giữ dates/estimates lịch sử và checkpoint mới tách riêng.
