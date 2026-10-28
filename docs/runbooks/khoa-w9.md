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
