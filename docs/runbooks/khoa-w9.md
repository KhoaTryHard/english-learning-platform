# User/Content/Gateway: runbook kiểm thử và bàn giao W9

Chuẩn: Java 21, PostgreSQL 17, Maven Wrapper, Docker Engine/Compose. Không CI/CD. Checkout SHA của PR; ghi SHA và ngày thực của mỗi lần chạy. Assessment/Progress thật do Huy cung cấp; demo WireMock phải ghi MOCK.

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

Mỗi evidence gồm command, SHA từng service, môi trường, seed/fixture, expected/actual, PASS/FAIL/BLOCKED/NOT_RUN, MOCK/LIVE và log/ảnh. Không backdate hoặc lấy lần CI cũ làm lần chạy mới. Peer review và Huy integration/QA vẫn là điều kiện nghiệm thu riêng.
