# Khoa backlog — môi trường TEST và bàn giao

User/Content/Gateway ở nhánh `feature/khoa-backlog-w7-w8`; Assessment/Progress
checkout riêng có PR44 fix413a929. Java25, Docker Compose hỗ trợ `!override`,
OpenSSL và Python3.12 với PyYAML/jsonschema. Không dùng profile demo.
Giữ public/internal contracts và V1 migrations. Xem
[kết quả và owner còn lại](../reviews/2026-11-01-khoa-backlog.md).

## Chuẩn bị và build

Từ root checkout Khoa trong PowerShell, đặt JAVA_HOME/PATH tới JDK25.
Nếu chưa có Python environment: `py -3.12 -m venv .tools/backlog-venv`, rồi
`.tools/backlog-venv/Scripts/python.exe -m pip install -r scripts/requirements-backlog.txt`.
Các lệnh `python` dưới đây dùng interpreter đó; phiên01/11 tái sử dụng Python3.12
và dependencies cục bộ đã có, không cài vào Python hệ thống.

```powershell
New-Item -ItemType Directory -Force .tools/backlog | Out-Null
. ./scripts/khoa-backlog-env.ps1 -HuySourceRoot '<absolute owner checkout>'
./scripts/protect-test-secrets.ps1
./mvnw.cmd -B verify
python scripts/check-contracts.py
python scripts/check-service-layout.py
docker compose config --quiet
docker compose build user content api assessment-live progress-live
docker compose up -d --no-build --wait user content api assessment-live progress-live
./scripts/bootstrap-admin.ps1 -Email admin@example.com
./scripts/seed-content.ps1
```

Dot-source env helper trong **mỗi shell** để giữ project/image/Compose overlay.
Mặc định project `khoa-backlog-20261101`; dùng `-Project khoa-backlog-<new-id>` và
`-ImageTag <new-tag>` để tách môi trường. Env helper giữ secrets đã có. Không log
`docker compose config` hoặc `docker inspect` toàn bộ vì có DB env secrets.

Root services gọi Assessment `http://assessment-live:8090`; Gateway gọi Progress
`http://progress-live:8091`; hai owner gọi User8081/Content8082 qua backend network.
Mỗi owner chỉ mount public-key directory và private key của chính nó.

Chỉ TEST overlay publish loopback: Gateway18080, User probe18081, Content probe18082,
Assessment8090 và Progress8091. DB không publish host port, data networks internal.
Các cổng User/Content probe chỉ phục vụ acceptance tại máy test, không là cấu hình
production. Seed Course/Level qua **Content seed mode** bằng fixture riêng; Assessment
seed qua Admin API. Không chèn business rows xuyên DB của service khác.

## Chuẩn bị source probe và chạy acceptance

Probe dùng chính `TokenCodec` của ứng dụng, không tự viết ký/verify JWT. Java helper
không xuất token. Chuẩn bị classpath sau Maven build:

```powershell
$backlogCpFile = Join-Path (Get-Location).Path '.tools/backlog/user-classpath.txt'
./mvnw.cmd -B -f backend/services/user-service/pom.xml dependency:build-classpath "-Dmdep.outputFile=$backlogCpFile"
$backlogCp = (Get-Content -LiteralPath $backlogCpFile -Raw).Trim()
New-Item -ItemType Directory -Force .tools/backlog/probe-classes | Out-Null
javac -cp "backend/services/user-service/target/classes;$backlogCp" -d .tools/backlog/probe-classes scripts/OwnerSourceProbe.java
$env:ALLOW_TEST_FAULT_INJECTION = '1'
python scripts/verify-khoa-backlog.py --phase core --state .tools/backlog/state-new.json --report .tools/backlog/core-new.json
python scripts/verify-khoa-backlog.py --phase owners --state .tools/backlog/state-new.json --report .tools/backlog/owners-new.json
python scripts/verify-khoa-backlog.py --phase faults --state .tools/backlog/state-new.json --report .tools/backlog/faults-new.json
python scripts/verify-khoa-backlog.py --phase gateway --state .tools/backlog/state-new.json --report .tools/backlog/gateway-new.json
python scripts/verify-khoa-backlog.py --phase audit --state .tools/backlog/state-new.json --report .tools/backlog/audit-new.json
$env:SESSION_REPORT = '.tools/backlog/session-new.json'
python scripts/verify-session-outage.py
$env:OPS_REPORT = '.tools/backlog/operations-new.json'
python scripts/verify-operations.py
Remove-Item Env:ALLOW_TEST_FAULT_INJECTION
```

Chạy **tuần tự**; operations cần dữ liệu ổn định. Mỗi lượt core mới dùng state/report
mới vì có publish và tạo fixture. Các pha sau dùng đúng state đã core PASS.
State credentials nằm trong ignored `.tools/backlog/*.credentials.json`; không
commit/upload state credentials, secrets hoặc raw headers/cookies. Evidence xuất bản
chỉ chứa fixture IDs và kết quả đã loại bí mật.

Fault phase chỉ chọn project `khoa-backlog-*`, kiểm container label, disconnect/reconnect
data network với đúng DNS alias trong finally. Pool test giữ table lock22s rồi tự
rollback; không ghi dữ liệu trong transaction lock. Chờ phase kết thúc và kiểm health
trước thao tác tiếp theo. Session test thu hồi SELECT trong DB test rồi khôi phục
finally. Không chạy các lệnh này trên shared/production environment.

Mỗi report cần SHA runner/service, image ID, DB/migration, fixture, command,
expected/actual, thời gian và PASS/FAIL/BLOCKED/NOT_RUN. HTTP checks/assertions không
phải JUnit case; SDK repeated suites không là nghiệp vụ mới. Bộ root verify skip
opt-in readiness không thay kết quả LIVE riêng.

## Dừng và khôi phục

```powershell
docker compose stop api user content assessment-live progress-live user-db content-db assessment-db progress-db
```

Giữ volumes, credentials, fixtures và report. Khi khởi động lại, dot-source cùng
project/tag/owner checkout và `up -d --no-build --wait` như trên. Không dùng `down -v`.
Backup/restore script tạo DB đích ngẫu nhiên cùng cluster test, đối chiếu mọi bảng,
constraints/grants/Flyway và chỉ bỏ DB restore/dump do chính lượt đó tạo.

User/Gateway internal0.1.1 release/rollback theo cặp. Đây là stack TEST với local
image IDs; chưa có registry digest hoặc rehearsal production được duyệt. Production
tiếp tục HOLD W10. Kafka tắt, không tuyên bố event delivery/Progress completion E2E.
Statistics consumer và frontend acceptance theo #36/#28/#29/#32/#38/#39/#40.
