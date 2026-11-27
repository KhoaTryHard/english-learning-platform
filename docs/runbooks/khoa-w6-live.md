# W6-02 — ghép Gateway/User/Content và Assessment thật

Điểm xuất phát 01/11/2026: Khoa PR #1 `bf183461e5647ad83c7ec16dc6c7d1953379b10e`;
Huy PR #43 `c1a2a074a4cc6c0d3d0bdf04ef77540a530c5930`. Hai checkout riêng,
không merge để chạy. Application source chưa đổi; các helper/report mới ghi hash
riêng. Dùng JDK25, Docker Desktop Linux engine và PostgreSQL17.

Khi Docker engine chưa sẵn sàng, [phương án HOST](khoa-w6-host-live.md) chạy JAR
và PostgreSQL17 thật với provenance riêng. Kết quả HOST không chứng minh Docker
deployment; xem checkpoint kết quả mới để biết source Gateway đã sửa preflight.

## Cấu hình và khởi động

Chạy tại root checkout Khoa trong PowerShell. Ví dụ dưới dùng checkout Huy đã
pin ở `.tools/huy-w6-source`; nếu chưa có, fetch PR #43 rồi tạo checkout riêng
tại đúng SHA. Không reset nhánh hoặc copy application code giữa hai service.

```powershell
$taskRoot = (Get-Location).Path
$taskHuyService = Join-Path $taskRoot '.tools/huy-w6-source/backend/services/assessment-service'
$taskReports = Join-Path $taskRoot '.tools/w6-live-20261101'
$taskAssessmentProject = 'khoa-assessment-w6-20261101'
$env:JAVA_HOME = Join-Path $taskRoot '.tools/jdk/jdk-25.0.5+8'
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
$env:COMPOSE_PROJECT_NAME = 'khoa-w6-live-20261101'
$env:KHOA_BACKEND_NETWORK = "$($env:COMPOSE_PROJECT_NAME)_backend"
$env:IMAGE_TAG = 'khoa-w6-live-bf183461'
$env:ASSESSMENT_IMAGE_TAG = 'huy-w6-live-c1a2a074'
$env:GATEWAY_PORT = '18080'
$env:GATEWAY_URL = 'http://127.0.0.1:18080'
$env:FRONTEND_ORIGIN = 'http://127.0.0.1:5173'
$env:ASSESSMENT_URL = 'http://assessment-live:8090'
Remove-Item Env:COMPOSE_PROFILES -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $taskReports | Out-Null
./scripts/init-dev.ps1
docker compose config --quiet
docker compose build user content api
docker compose up -d --no-build --wait user content api
./scripts/bootstrap-admin.ps1 -Email admin@example.com
./scripts/seed-content.ps1
```

Mỗi private key chỉ mount vào service tương ứng. Lần TEST này dùng identity
Assessment cục bộ trong `.secrets/keys/assessment.private.pem`, không phải key
production hoặc bằng chứng Huy đã nhận bàn giao. Không thay key đã có. Public
key `assessment` phải khớp private key này; public keys `api`/`content` cần có
trong thư mục chỉ đọc của Assessment. Không đưa key bytes/password vào report.

```powershell
$taskPasswordFile = Join-Path $taskReports 'assessment-db-password'
if (-not (Test-Path -LiteralPath $taskPasswordFile)) {
    [IO.File]::WriteAllText($taskPasswordFile, [Guid]::NewGuid().ToString('N'))
}
$env:DB_PASSWORD = [IO.File]::ReadAllText($taskPasswordFile)
$env:DB_USER = 'assessment'
$env:DB_PORT = '5543'
$env:PORT = '8090'
$env:PUBLIC_KEYS_DIR = Join-Path $taskRoot '.secrets/keys/public'
$env:PRIVATE_KEY_FILE = Join-Path $taskRoot '.secrets/keys/assessment.private.pem'
$taskHuyCompose = Join-Path $taskHuyService 'compose.yaml'
$taskOverride = Join-Path $taskRoot 'infra/compose/assessment-live.override.yaml'
docker compose -p $taskAssessmentProject -f $taskHuyCompose -f $taskOverride config --quiet
docker compose -p $taskAssessmentProject -f $taskHuyCompose -f $taskOverride up --build -d
```

Override gắn Assessment vào cả mạng DB riêng và backend của Khoa, alias
`assessment-live`. Assessment gọi User `http://user:8081`, Content
`http://content:8082`. Host probe `http://127.0.0.1:8090/actuator/health` phải UP.
Chờ app migrate xong trước khi tạo fixture. Kafka tắt; event sau submit có thể
ở PENDING, không kết luận Progress đã đồng bộ.

## Manifest và fixture

Copy `docs/fixtures/readiness-live.example.json` vào thư mục report, đặt
`gatewayUrl=http://127.0.0.1:18080`, backend SHA bằng `git rev-parse HEAD`,
Assessment source SHA đầy đủ, tag/image ID thật, PostgreSQL version, migration
`V1__assessment_foundation.sql` và link/path runtime evidence. Runtime evidence
ghi image ID, health, DB version, Flyway history và public-key fingerprints;
không dùng metadata tự khai thay bằng chứng DB thực.

```powershell
$taskManifest = Join-Path $taskReports 'manifest.json'
# Điền manifest trước lệnh prepare; file đầu vào chưa có fixtures.
./scripts/verify-readiness-live.ps1 -Phase prepare -Manifest $taskManifest `
    -Report (Join-Path $taskReports 'prepare.json')
$taskPrepared = "$taskManifest.prepared.json"
$env:RUN_LIVE_ASSESSMENT = '1'
python scripts/verify-assessment-live.py --phase seed --manifest $taskPrepared `
    --report (Join-Path $taskReports 'assessment-seed.json')
```

`prepare` tạo sáu Lesson draft. `seed` chỉ dùng Admin APIs qua Gateway, reconcile
mapping đang có khi chạy tiếp sau lỗi; không tạo Exercise cho `missingMapping`.
`notReady` có Exercise draft không câu hỏi; bốn ca còn lại có một TRUE_FALSE,
đáp án false, 2 điểm và ready=true. Prepared manifest được cập nhật sau từng
Exercise; giữ các file report cũ, dùng tên mới khi chạy lại. Không chạy seed
lại trên Lesson đã publish; tạo bộ fixture mới khi cần nghiệm thu lại.

## Readiness, lỗi, phục hồi và retry

```powershell
./scripts/verify-readiness-live.ps1 -Phase online -Manifest $taskPrepared `
    -Report (Join-Path $taskReports 'online.json')
try {
    docker compose -p $taskAssessmentProject -f $taskHuyCompose -f $taskOverride stop assessment
    ./scripts/verify-readiness-live.ps1 -Phase service-down -Manifest $taskPrepared `
        -Report (Join-Path $taskReports 'service-down.json')
} finally {
    docker compose -p $taskAssessmentProject -f $taskHuyCompose -f $taskOverride start assessment
}
# Chờ Assessment healthy. Chỉ dừng DB của project TEST vừa tạo.
try {
    docker compose -p $taskAssessmentProject -f $taskHuyCompose -f $taskOverride stop db
    ./scripts/verify-readiness-live.ps1 -Phase database-down -Manifest $taskPrepared `
        -Report (Join-Path $taskReports 'database-down.json')
} finally {
    docker compose -p $taskAssessmentProject -f $taskHuyCompose -f $taskOverride start db
}
# Chờ DB và Assessment healthy trước pha recovery.
./scripts/verify-readiness-live.ps1 -Phase recovery -Manifest $taskPrepared `
    -Report (Join-Path $taskReports 'recovery.json')
python scripts/verify-assessment-live.py --phase retry --manifest $taskPrepared `
    --report (Join-Path $taskReports 'attempt-retry.json')
```

Mỗi native command phải có exit0; dừng chuỗi nếu khác0 hoặc report FAIL. Không
tiếp tục pha phụ thuộc sau lỗi online. Direct readiness service-down phải mất
kết nối; database-down phải trả HTTP503 khi app còn chạy. Các ca publish lỗi
giữ draft. Chỉ chạy recovery sau khi cả nguồn và DB khôi phục.

Retry tạo Learner test mới, cùng key giữ attemptId/snapshot; key mới tạo attempt
mới. Sau save ACK, submit chấm false đúng100%; retry sau submit giữ attempt cũ.
Script kiểm CORS, RBAC, CSRF, BLOCKED và replay cookie đã logout. Không lưu
password/cookie/Idempotency-Key vào report. Script không tự dừng service/DB.

No-callback cần đọc `AssessmentService.readiness` tại đúng SHA: chỉ query DB
Assessment; test `readinessAllowsOnlyContentAndDoesNotCallContent` kiểm bằng
Content test double từ chối callback. Source/test này cộng report HTTP LIVE;
không suy chỉ từ mã200 rằng đã chứng minh mọi hành vi nội bộ.

Chạy session-store fault bằng `verify-session-outage.py` trên project TEST nếu
cần kiểm lại 503 User; giữ report path mới và quyền SELECT được phục hồi trong
`finally`. Unit Gateway kiểm request bị từ chối không tới owner; kiểm HTTP
session không tự chứng minh toàn bộ network/pool fault matrix.

## Kết thúc và nghiệm thu

Lưu commands, thời gian, source SHA, script hashes, image IDs và manifest hash.
Đối chiếu mỗi checklist với report thực. `PHASE_PASS_REVIEW_REQUIRED` hoặc
`PASS_REVIEW_REQUIRED` chưa là quyết định nhóm. #42 vẫn cần Khoa/Huy review và
Hưng ghi baseline; PR #1 cần peer review trên SHA cuối trước merge.

```powershell
docker compose -p $taskAssessmentProject -f $taskHuyCompose -f $taskOverride stop
docker compose stop user content api user-db content-db
Remove-Item Env:DB_PASSWORD,Env:RUN_LIVE_ASSESSMENT -ErrorAction SilentlyContinue
```

Giữ volumes/evidence để tái hiện; không dùng `down -v`. #20/schema, W7 và
Kafka/Progress E2E có gate riêng. Ghi actual thời gian phiên thực, không biến
estimate W6 cũ thành actual của Khoa/Huy.
