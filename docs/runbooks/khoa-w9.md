# User/Content/Gateway — regression và bàn giao W9 (#41)

Runbook hiện hành ngày 09/11/2026. Khoa phụ trách User/Content/Gateway; Huy phụ
trách Assessment/Progress/Kafka; Hưng phụ trách frontend và QA #40. Public API v0.1,
internal auth 0.1.1; User/Gateway triển khai và rollback theo cặp theo
[baseline đã ghi nhận](../contracts/internal-auth-decision-2026-11-01.md).

## Chọn source và chuẩn bị

Dùng checkout sạch có PR #45 đã merge vào develop (`663b482`), hoặc nhánh #41
phát triển từ phiên bản đó. Ghi SHA đầy đủ của checkout Khoa và checkout Huy.
Assessment/Progress candidate `413a9297d4d63f61b0ad4b81a0c1f1e7a9370abd` gồm
PR #44 DB503 fix; review/merge PR #43/#44 vẫn theo owner. Không thay candidate
bằng head cũ thiếu fix khi chạy database-down.

Cần Docker Engine/Compose hỗ trợ `!override`, PowerShell 7, JDK25, OpenSSL và
Python3.12. Dùng Python venv riêng nếu chưa có dependencies:

```powershell
py -3.12 -m venv .tools/w9-venv
.tools/w9-venv/Scripts/python.exe -m pip install -r scripts/requirements-backlog.txt
$env:JAVA_HOME = '<absolute JDK25 directory>'
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
git rev-parse HEAD
git -C '<absolute Huy checkout>' rev-parse HEAD
```

Chọn project TEST riêng, image tag riêng và report directory mới cho mỗi lần chạy.
Lần đầu hoặc khi source/image thay đổi, dùng `-BuildImages`:

```powershell
./scripts/verify-khoa-w9.ps1 `
 -HuySourceRoot '<absolute Huy checkout>' `
 -Project 'khoa-backlog-w9-<run-id>' `
 -ImageTag 'khoa-w9-<run-id>' `
 -ReportDirectory '.tools/w9-<run-id>' `
 -Python '.tools/w9-venv/Scripts/python.exe' `
 -BuildImages
```

Chỉ bỏ `-BuildImages` nếu đã đối chiếu image IDs với manifest build và service tree
IDs đúng SHA đang chạy. Git merge/document commit không tự tạo image mới; ghi rõ
image kế thừa và source tương đương. Không chạy đồng thời browser/seed/regression
khác vào cùng project, vì snapshot restore cần dữ liệu ổn định.

Script dot-source `khoa-backlog-env.ps1`, giữ secrets đã tạo, bảo vệ ACL Windows,
build source probe bằng TokenCodec thật, bootstrap Admin và seed Content qua owner
seed mode. Assessment fixtures được tạo bằng Admin API với session/CSRF. Credentials
nằm trong ignored `.tools/w9-<run-id>/state.credentials.json` và `.secrets`; report
xuất bản chỉ chứa fixture IDs/status/digests, không upload raw logs/cookies/JWT/keys.

## Mạng, danh tính và quyền

| Thành phần | URL trong network TEST | Probe từ host |
|---|---|---|
| Gateway | api:8080 | http://127.0.0.1:18080 |
| User | user:8081 | http://127.0.0.1:18081 |
| Content | content:8082 | http://127.0.0.1:18082 |
| Assessment | assessment-live:8090 | http://127.0.0.1:8090 |
| Progress | progress-live:8091 | http://127.0.0.1:8091 |

DB riêng từng owner, data networks internal, không publish DB port. Các host probe
User/Content là cấu hình TEST, không mang sang public deployment. Chỉ mount public
keys và readonly private key của service sở hữu; Gateway không có DB credentials.
User/Content runtime không superuser/CREATEDB/CREATEROLE/schema CREATE, tài khoản
migration tách khỏi runtime. Không seed business SQL xuyên DB.

Internal `/internal/v1` yêu cầu caller/audience đúng; request từ Gateway có assertion
ràng buộc method/path/role. Session validation dùng `token + requestMethod`; unsafe
methods có CSRF. Giữ cookie/session/revocation/BLOCKED/RBAC theo contract, không
fallback thành GET/token-only. CORS origin TEST là http://127.0.0.1:5173.

## Regression và evidence

Runner dừng ngay khi có failure, giữ report/log để triage và stop stack trong
finally. Mỗi lần core tạo fixture/report mới; không tái publish fixture lịch sử.

| Lượt | Điều kiện cần chứng minh |
|---|---|
| tooling + Maven/static | Guard từ chối target không phải TEST trước thao tác DB/restart; Java25 verify, frozen contracts/63 routes, layout, checksum lịch sử |
| core | Auth/Profile/setup; visibility/ownership/RBAC; saved source update/concurrent save/remove; XLSX/DOCX atomic/duplicate/macro/corrupt/size/race |
| owners | Setup/cohort/context/paging/filter/count, đúng caller; denominator gồm draft/hidden, cohort gồm BLOCKED; Progress reads thật |
| gateway + retry-security | Cùng key cùng attempt, key mới retake, CSRF/Origin/session/revoked/BLOCKED; request bị từ chối không tạo business row |
| faults + session | User/Content DB network disconnect, Content pool10 đầy, session SELECT bị thu hồi; sanitized503/no-write/no fake0 và phục hồi |
| readiness | prepare/seed riêng, online → service-down → database-down → recovery; readiness false/publish409 hoặc503 giữ draft; caller api403/chữ ký sai401; Learner đọc exerciseId sau publish |
| audit | Image/private-key mount/non-root/loopback, DB role/network và Windows ACL; không impersonate tài khoản Windows khác |
| operations + post-restart | pg_dump/restore vào DB mới cùng cluster; khớp mọi bảng/digests/constraints/owners/grants/Flyway; restart DB/app và HTTP Profile/setup/saved Vocabulary/Lesson |

Readiness giữ Assessment service hoặc DB ngừng đúng pha rồi khôi phục trong finally.
Network/pool/session faults có opt-in `ALLOW_TEST_FAULT_INJECTION=1`. Script operations
và session kiểm cả project/service labels và Gateway URL tới đúng loopback container;
report cũ bị từ chối trước khi fault/backup/restart. Test lock tự rollback, quyền
SELECT được GRANT lại trong finally. Không chạy các lệnh này trên shared/production.

Maven mặc định skip `AssessmentReadinessLiveTest`; các pha opt-in chạy riêng sau đó.
Phân biệt JUnit/unit/HTTP doubles/Testcontainers với HTTP LIVE checks; không cộng
rerun hoặc assertions thành số nghiệp vụ mới. Mỗi gói evidence ghi giờ thực,
runner/owner SHA, service tree IDs, image IDs, DB/migration, fixture, command,
expected/actual và PASS/FAIL/BLOCKED/NOT_RUN. Giữ FAIL lịch sử.

## Dừng, restore và rollback

Runner chỉ stop năm app và bốn DB của project đã chọn, giữ volumes/fixtures/secrets.
Khi cần thao tác riêng, nạp lại đúng env trong shell mới:

```powershell
. ./scripts/khoa-backlog-env.ps1 -HuySourceRoot '<absolute Huy checkout>' `
 -Project 'khoa-backlog-w9-<run-id>' -ImageTag 'khoa-w9-<run-id>'
docker compose up -d --no-build --wait user content api assessment-live progress-live
$env:ALLOW_TEST_FAULT_INJECTION = '1'
$env:OPS_REPORT = '.tools/w9-<new-run-id>/operations.json'
python scripts/verify-operations.py
Remove-Item Env:ALLOW_TEST_FAULT_INJECTION
docker compose stop api user content assessment-live progress-live user-db content-db assessment-db progress-db
```

Backup script dùng custom-format dump bên trong DB container, tránh binary redirect
PowerShell5. Restore chỉ vào DB tên ngẫu nhiên `user_restore_*`/`content_restore_*`
vừa tạo, so sánh dữ liệu/schema/quyền rồi dọn đúng DB và dump của lần chạy. Source
DB/volumes giữ nguyên. Đây là restore cùng cluster, không phải khôi phục đa host.
Không dùng `down -v`, không sửa migration V1.

Rollback ứng dụng User/Gateway phải dùng cặp phiên bản cùng baseline0.1.1 và image
IDs đã kiểm chứng. Giữ Content/schema tương thích, backup trước migration mới; không
downgrade schema hoặc restore đè DB đang dùng. Recreate consumer khi đổi URL/key,
kiểm health, login/session/CSRF và request tới owner trước mở lại truy cập.

## Ranh giới nghiệm thu

Báo cáo #41 nghiệm thu phần Khoa trong TEST. Kafka tắt trong runner này; evidence
Exercise→Outbox→Kafka→Progress của Hưng ngày03/11 ở PR #46 được kế thừa đúng phạm vi,
không thay broker outage/replay/DLQ/crash recovery của Huy #30/#34. Statistics #36,
frontend W8 PR #47, full QA #40 và peer review bản #41 là các gate riêng.

Transport của lượt TEST là HTTP loopback. HTTPS termination, Secure cookie,
certificate/trust và hạn chế ingress thực tế cần được kiểm chứng ở môi trường
triển khai W10; không bật Secure cookie trên HTTP để tuyên bố đã test TLS. Production
HOLD W10. Không ghi approval, actual hours hoặc capacity thay Huy/Hưng.
