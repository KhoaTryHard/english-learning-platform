# User/Content/Gateway: runbook kiểm thử và bàn giao W9

Chuẩn: Java 25, PostgreSQL 17, Maven Wrapper, Docker Engine/Compose. Không CI/CD. Checkout SHA của PR; ghi SHA và ngày thực của mỗi lần chạy. Assessment/Progress thật do Huy cung cấp; demo WireMock phải ghi MOCK.

## Chuẩn bị và chạy

1. `bash scripts/init-dev.sh` tạo dữ liệu development cục bộ và giữ credentials đã có. `.secrets` và `.secrets/keys` được bảo vệ mode 700; chỉ mount public keys và private key của đúng service. Trên Windows dùng init-dev.ps1 và kiểm tra ACL thư mục secrets trước khi chia sẻ máy.
2. Đặt FRONTEND_ORIGIN chính xác; ASSESSMENT_URL/PROGRESS_URL tới runtime Huy; MEDIA_HOSTS theo nguồn đã được phép. Không để production dùng mock mặc định. HTTPS + SECURE_COOKIES phải được review trước deployment W10.
3. `docker compose config --quiet`; `docker compose build user content api`; `docker compose up -d user content api`. Migrations one-shot dùng tài khoản migration; runtime không chạy DDL. Chỉ dùng `--profile demo` cho kiểm thử có gắn nhãn MOCK.
4. Bootstrap/seed theo scripts hiện có. Chỉ seed dữ liệu test, không ghi đè môi trường dùng thật. Gateway mặc định 8080; User 8081, Content 8082 ở private network; không public expose internal APIs hoặc DB.
5. Root `bash mvnw verify` yêu cầu Docker cho Testcontainers. Build độc lập từng thư mục: `bash mvnw -B verify`; không cần parent repository/sibling JAR. Chạy `python scripts/check-service-layout.py --build` để kiểm owner-only JAR.

## Kiểm thử tăng dần

- #42: service JWT audience/issuer/signature/expiry/allowlist; User JWT không thay service identity; cookie/CSRF rotation, revoked JWT, BLOCKED, RBAC, lỗi 401/403/503. Gate compatibility v0.1/v0.1.1 phải có quyết định nhóm.
- #21: routes bốn owner, Idempotency-Key nguyên vẹn, credentialed CORS/preflight; retry cùng key giữ attemptId, retake key mới. Chạy LIVE sau khi Assessment #25 sẵn.
- #14 / T-09: XLSX valid/invalid/duplicate/race, DOCX title/order duplicate, atomic rollback createdCount=0; My Vocabulary source-update/hidden-parent/UNAVAILABLE/idempotent remove. File-boundary unit tests không thay DB rollback test.
- #31: source APIs đúng caller, paging/filter/invalid UUID, denominator hidden lessons, global/cohort counts, 503 source unavailable; Huy nhận URL/SHA/fixture/results. Không cross-service SQL hoặc fake zero.
- #41: chạy lại các case bị ảnh hưởng sau chuyển cây/merge, health/restart persistence, backup/restore riêng, key/DB least privilege. LIVE UI/Auth/Content không tự chứng minh Kafka/Progress/Assessment.

## Dừng, backup và restore an toàn

`docker compose stop user content api` giữ volumes. Không dùng `down -v` hoặc xóa volume để khắc phục lỗi. Trước migration/rollout lưu backup vào kho bảo mật ngoài Git: `docker compose exec -T user-db pg_dump -U postgres -d user_db -Fc > user.dump`, tương tự Content. Binary redirect trên PowerShell cần 7.4+ hoặc dump trong container rồi docker cp.

Restore vào database test mới, tên riêng, không ghi đè source DB. So sánh row counts, FK/unique/CHECK, Flyway history, role grants và schema validation; kết thúc chỉ xóa database restore do phiên test tạo. `python scripts/verify-operations.py` thực hiện round-trip/restart trên môi trường test đã chuẩn bị. Giữ log đã loại credentials/cookie/token.

### Chạy độc lập trên Windows/Java 25

Đặt `JAVA_HOME` tới JDK 25 và thêm `$env:JAVA_HOME\bin` vào PATH của phiên PowerShell trước khi chạy `./mvnw.cmd -B verify`. Java hệ thống có thể khác Java của dự án. Dockerfiles dùng Temurin 25 cho cả build và runtime.

Ví dụ cấu hình môi trường **test riêng**, dùng cùng các biến này cho mọi lệnh Compose/bootstrap/seed/test/stop:

```powershell
$env:COMPOSE_PROJECT_NAME = 'khoa-alignment-20261028'
$env:IMAGE_TAG = 'khoa-audit-20261028'
$env:GATEWAY_PORT = '18080'
$env:GATEWAY_URL = 'http://localhost:18080'
$env:FRONTEND_ORIGIN = 'http://127.0.0.1:5173'
./scripts/init-dev.ps1
docker compose build user content api
docker compose --profile demo up -d --no-build --wait
./scripts/bootstrap-admin.ps1 -Email admin@example.com
./scripts/seed-content.ps1
$env:SMOKE_REPORT = '.tools/smoke-current.json'
python scripts/smoke.py
$env:SESSION_REPORT = '.tools/session-current.json'
$env:ALLOW_TEST_FAULT_INJECTION = '1'
python scripts/verify-session-outage.py
Remove-Item Env:ALLOW_TEST_FAULT_INJECTION
$env:OPS_REPORT = '.tools/operations-current.json'
python scripts/verify-operations.py
docker compose --profile demo stop
```

Session regression tạm thu hồi SELECT trên bảng session trong DB test rồi khôi phục trong `finally`: xác minh 503, CSRF/session sai 403, không tạo Course và request hợp lệ thành công sau phục hồi. Đây là lỗi quyền truy cập kho session, không thay test network partition/pool exhaustion. Chỉ bật fault injection trên project test cô lập.

Operations script so sánh digest/row count của **mọi bảng public**, definitions constraints, table/sequence owners và ACLs, gồm Flyway/session; sau đó restart cả DB và app. Restore vào database mới trong **cùng cluster test**, chưa chứng minh disaster recovery sang máy mới. Không chạy browser/smoke đồng thời vì dữ liệu thay đổi sẽ làm so sánh không nhất quán. Report-path variables giữ các báo cáo lịch sử khỏi bị ghi đè.

Frontend PR #22: đặt `GATEWAY_URL=http://127.0.0.1:18080` trong phiên frontend; origin phải khớp cấu hình trên. Chạy suite `playwright.live.config.ts` với `RUN_LIVE_INTEGRATION=1`, Course/Level và Lesson/Vocabulary published có sẵn. Lát này là LIVE User/Content/Gateway/PostgreSQL; readiness vẫn MOCK khi dùng profile demo.

Mỗi evidence gồm command, SHA từng service, môi trường, seed/fixture, expected/actual, PASS/FAIL/BLOCKED/NOT_RUN, MOCK/LIVE và log/ảnh. Không backdate hoặc lấy lần CI cũ làm lần chạy mới. Peer review và Huy integration/QA vẫn là điều kiện nghiệm thu riêng.

## Nghiệm thu readiness với Assessment thật — #25/#42

Hiện repo chỉ có scaffold Assessment; chưa có URL, image, migrations/seed hoặc SHA implementation của Huy. **LIVE readiness: NOT_RUN / WAITING_HUY_RUNTIME.** Bộ chạy dưới đây chuẩn bị sẵn cho lần bàn giao; không tự cung cấp Assessment giả. [Phiếu review auth và rollout/rollback](../contracts/internal-auth-review-2026-10-28.md) chờ phản hồi thực của Huy và ghi nhận baseline của Hưng.

### Huy bàn giao trước khi chạy

- PR/branch + SHA đầy đủ, image tag/digest hoặc lệnh build/run, DB engine/version, migrations, seed command và log runtime không có secret.
- URL từ máy Khoa để probe và URL từ container Content, cổng và mạng Docker. Mặc định dùng project test riêng; service thật tên `assessment-live`, gắn `<COMPOSE_PROJECT_NAME>_backend`. Tên/cổng thực tế phải theo deployment Huy bàn giao, không dùng service `assessment` WireMock trong root Compose.
- Assessment giữ private key của chính nó; Khoa nhận public key. Assessment nhận public keys `content`, `api` và các caller thực sự cần. Các key của caller dùng probe phải là cùng key Content/Gateway của stack test, không thay private key của nhau.
- Endpoint `GET /internal/v1/lessons/{lessonId}/exercise-readiness`: chỉ caller Content; thiếu mapping trả200/ready=false, đúng một Exercise ready và >=1 câu hợp lệ mới ready=true; DB lỗi503; không callback sang Content. Các fixture không hợp lệ do Huy tạo theo schema thật, không thêm đường seed/SQL xuyên service.
- `ServiceGuard` SDK hiện chỉ có allowlist endpoint nội bộ của User/Content; readiness chưa nằm trong allowlist. Huy có thể reuse `TokenCodec`, nhưng nếu reuse guard phải bổ sung mapping readiness → caller `content` và tests trong Assessment; copy guard hiện tại nguyên trạng sẽ từ chối cả caller Content hợp lệ.

### Kết nối và tạo fixture Content

Chọn project test riêng (ví dụ `khoa-readiness-live`), cổng Gateway `18080`, Java25. Không đặt `COMPOSE_PROFILES=demo`; không dùng `--profile demo`. Nếu project đã từng chạy mock thì dừng riêng container mock trước. Huy khởi động Assessment và DB thật bằng deployment của mình rồi kết nối vào network backend của project. Nếu chạy trên host thay vì Docker thì `ASSESSMENT_URL` dùng `host.docker.internal:<cổng thực>`; `localhost` trong Content container không trỏ tới máy host.

```powershell
$env:COMPOSE_PROJECT_NAME = 'khoa-readiness-live'
$env:IMAGE_TAG = 'khoa-audit-20261028' # hoặc tag đã build từ SHA hiện hành
$env:GATEWAY_PORT = '18080'
$env:GATEWAY_URL = 'http://localhost:18080'
$env:FRONTEND_ORIGIN = 'http://127.0.0.1:5173'
$env:ASSESSMENT_URL = 'http://assessment-live:8080' # sửa theo bàn giao thực
./scripts/init-dev.ps1
docker compose config --quiet
docker compose up -d --no-build --wait user content api
./scripts/bootstrap-admin.ps1 -Email admin@example.com
./scripts/seed-content.ps1
# Sau khi thay URL/key, tái tạo consumer để nhận cấu hình mới:
docker compose up -d --no-deps --force-recreate --wait content api
```

Kiểm tra config từ `docker compose config` và image/deployment của Assessment, xác nhận URL đích đúng service thật. Không công bố output chứa secret. Nếu key đã được nạp trước đó, restart/recreate consumer sau khi trao public keys; không sinh lại key đã dùng.

Copy [manifest mẫu](../fixtures/readiness-live.example.json) vào `.tools/readiness-live/manifest.json`. Điền `backendSha` từ `git rev-parse HEAD`, URL thật, Course/Level test đã seed. `assessmentUrl` là URL truy cập từ máy chạy test; `assessmentUrlFromContent` phải bằng `ASSESSMENT_URL`. Sau khi Huy bàn giao, điền SHA/image/DB/migration/seed/runtime evidence thật trong `assessment`. Không đưa password/token/private-key bytes vào manifest.

```powershell
New-Item -ItemType Directory -Force .tools/readiness-live | Out-Null
Copy-Item docs/fixtures/readiness-live.example.json .tools/readiness-live/manifest.json
# Điền manifest như hướng dẫn rồi chạy:
./scripts/verify-readiness-live.ps1 -Phase prepare `
  -Manifest .tools/readiness-live/manifest.json -Report .tools/readiness-live/prepare.json
```

`prepare` chỉ tạo một Module published và sáu Lesson draft, xuất `manifest.json.prepared.json` với IDs; Course test sẽ được published. Chỉ chạy vào dữ liệu test. Gửi danh sách IDs cho Huy seed Assessment: `missingMapping` không gán Exercise; `notReady` có Exercise chưa ready/không có câu hỏi hợp lệ; `ready`, `serviceDown`, `databaseDown`, `recovery` có Exercise ready và câu hỏi hợp lệ. Huy điền exerciseId/questionCount tương ứng trong bản prepared manifest cùng provenance runtime. Không sửa lessonId hoặc reuse Lesson giữa các ca.

### Các pha kiểm thử, lỗi và phục hồi

Với JDK25 trên JAVA_HOME/PATH, chạy từ root repo, dùng bản prepared manifest đã điền. `online` yêu cầu Assessment/DB thật đang hoạt động. Các pha sau thao tác **chỉ trên service/DB test Huy đã xác định**; bộ test không tự dừng process/DB.

```powershell
$taskManifest = '.tools/readiness-live/manifest.json.prepared.json'
./scripts/verify-readiness-live.ps1 -Phase online -Manifest $taskManifest -Report .tools/readiness-live/online.json
# Huy dừng service Assessment test; giữ DB và User/Content/Gateway hoạt động.
./scripts/verify-readiness-live.ps1 -Phase service-down -Manifest $taskManifest -Report .tools/readiness-live/service-down.json
# Huy khôi phục Assessment, sau đó dừng/mất kết nối DB Assessment test; process vẫn trả HTTP503.
./scripts/verify-readiness-live.ps1 -Phase database-down -Manifest $taskManifest -Report .tools/readiness-live/database-down.json
# Huy khôi phục cả service/DB; xác nhận healthy.
./scripts/verify-readiness-live.ps1 -Phase recovery -Manifest $taskManifest -Report .tools/readiness-live/recovery.json
```

Mỗi report path phải mới; không ghi đè report cũ. Khi rerun `online`/`recovery` sau publish thành công, tạo bộ Lesson/Exercise test mới bằng manifest fresh. Report lỗi giữ tên ca/status, không ghi token/password/raw body. Wrapper khôi phục biến môi trường process khi kết thúc. Bộ test dùng `TokenCodec` hiện có để ký JWT, không tự triển khai crypto.

| Pha/ca | Điều kiện cần chứng minh |
|---|---|
| online/missingMapping, notReady | Direct readiness200/ready=false; publish409 ASSESSMENT_NOT_READY; GET Admin xác nhận Lesson vẫn draft |
| online/ready | Đúng lessonId/exerciseId/questionCount; publish200/published=true; Learner thật chọn Course/Level và GET Lesson200 có đúng exerciseId |
| online/auth | Service JWT caller api bị403; JWT Content chữ ký hỏng bị401; không trả readiness nghiệp vụ |
| service-down | Probe kết nối Assessment thất bại; publish503 DEPENDENCY_UNAVAILABLE; Lesson vẫn draft |
| database-down | Direct Assessment trả503 DEPENDENCY_UNAVAILABLE; publish503 và Lesson vẫn draft |
| recovery | Các Lesson bị lỗi trước vẫn draft; Lesson recovery publish200 và Learner GET200 sau khi nguồn hoạt động trở lại |

Kết quả chuẩn bị là `FIXTURES_CREATED_LIVE_NOT_RUN`; mỗi pha đạt chỉ là `PHASE_PASS_REVIEW_REQUIRED`. Không có nhãn tự động full LIVE PASS. Cần **cả bốn report pha**, cùng manifest SHA/fixture IDs và deployment log của Huy xác nhận dùng DB thật; Huy/Khoa review evidence, gồm việc Content thực sự gọi đúng URL từ container và readiness không callback Content. Report HTTP không tự chứng minh schema/DB/no-callback; Huy bổ sung source/runtime test phía mình.

Sau nghiệm thu: bổ sung link report ở #3/#13 và #25/#26. #42 chỉ khép sau quyết định contract và handoff/review của nhóm. Retry/retake giữ ở #21/#25/#26; Kafka/Progress chưa thuộc bộ readiness. PR #1 còn Draft tới khi blocker tương ứng và peer review được xử lý; không đóng #3 chỉ vì wrapper đã chuẩn bị.
