# W6-02 — phương án LIVE trên host khi Docker engine không sẵn sàng

Phương án TEST dùng JAR thật của User/Content/Gateway/Assessment và ba cluster
PostgreSQL17 riêng. Không dùng H2/WireMock cho các luồng LIVE. Không thay cho
nghiệm thu Docker image/network/deployment; giữ kết quả đó ở NOT_RUN nếu engine
chưa hoạt động. Source và fixture được ghi riêng cho từng lần chạy.

Checkpoint cuối01/11: [W6-02 đã nghiệm thu](../reviews/2026-11-01-khoa-w6-completion.md)
với Assessment fix413a929 và baseline internal0.1.1. Lỗi DB được mô tả dưới đây
là lịch sử trước sửa; phiên fixture mới đã PASS cả direct503 và publish503.

## Chuẩn bị và chạy

JDK25 có sẵn trong `.tools/jdk/jdk-25.0.5+8`. Đặt PostgreSQL17 portable tại
thư mục test riêng; binary ngày01/11 lấy qua HTTPS từ EDB:
`https://get.enterprisedb.com/postgresql/postgresql-17.11-1-windows-x64-binaries.zip`.
SHA-256 archive: `6eabdf00d2893713b75db4336a23c3fdf505f056e217ec6e2e95d901750cfea3`.
Không cần cài Windows service hoặc thay cluster có sẵn.

Build JAR ở root Khoa và Assessment của checkout Huy đã pin. Khi build lại,
dừng process đang giữ JAR trước khi Maven repackage trên Windows. Chạy test
cần thiết trước; packaging sau test có thể dùng `-DskipTests` và báo riêng.

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools/jdk/jdk-25.0.5+8').Path
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
$env:LIVE_TEST_NAMESPACE = 'khoa-w6-host-<fresh-suffix>'
$taskArea = Join-Path '.tools' $env:LIVE_TEST_NAMESPACE
$taskHostArgs = @('--namespace', $env:LIVE_TEST_NAMESPACE,
    '--pg-bin', '.tools/pgsql-17.11/pgsql/bin', '--huy-root', '.tools/huy-w6-source')
python scripts/w6-host-runtime.py start @taskHostArgs
```

Helper chỉ nhận namespace `khoa-w6-host-*`, yêu cầu PostgreSQL17, từ chối reuse
thư mục đã khởi tạo hoặc port app đã có process. Ba cluster chỉ nghe loopback:
User15541, Content15542, Assessment15543; app User8081, Content8082,
Assessment8090, Gateway18080. Mỗi DB chỉ có role của service sở hữu. User và
Content migrate bằng migration role, chạy bằng runtime role; Assessment theo
deployment foundation của Huy (role service chạy Flyway). Kafka tắt.

Password chỉ lưu trong thư mục `.tools`/`.secrets` bị ignore; trao public keys,
private key chỉ nạp cho service tương ứng. Init/seed dùng chính owner code;
Exercise/Question sau đó tạo qua Gateway, không ghi SQL xuyên service.

`runtime.json` ghi PID/start time/executable/port/hash JAR, source SHA,
PostgreSQL version và Flyway history. Manifest ghi `runtimeMode=HOST`,
`hostRuntimeEvidence` và loopback URLs. Wrapper HOST kiểm các identity này trước
khi chạy và không gọi Docker. `assessment.image=NOT_APPLICABLE_HOST_JAR` được
ghi rõ; không dùng hash JAR như Docker digest.

```powershell
$taskManifest = Join-Path $taskArea 'manifest.json'
$taskPrepared = "$taskManifest.prepared.json"
./scripts/verify-readiness-live.ps1 -RuntimeMode HOST -Phase prepare `
    -Manifest $taskManifest -Report (Join-Path $taskArea 'prepare.json')
$env:RUN_LIVE_ASSESSMENT = '1'
$env:FRONTEND_ORIGIN = 'http://127.0.0.1:5173'
python scripts/verify-assessment-live.py --phase seed --manifest $taskPrepared `
    --report (Join-Path $taskArea 'seed.json')
./scripts/verify-readiness-live.ps1 -RuntimeMode HOST -Phase online `
    -Manifest $taskPrepared -Report (Join-Path $taskArea 'online.json')
```

## Lỗi, phục hồi và retry

```powershell
try {
    python scripts/w6-host-runtime.py stop-assessment @taskHostArgs
    ./scripts/verify-readiness-live.ps1 -RuntimeMode HOST -Phase service-down `
        -Manifest $taskPrepared -Report (Join-Path $taskArea 'service-down.json')
} finally {
    python scripts/w6-host-runtime.py start-assessment @taskHostArgs
}
try {
    python scripts/w6-host-runtime.py stop-assessment-db @taskHostArgs
    ./scripts/verify-readiness-live.ps1 -RuntimeMode HOST -Phase database-down `
        -Manifest $taskPrepared -Report (Join-Path $taskArea 'database-down.json')
} finally {
    python scripts/w6-host-runtime.py start-assessment-db @taskHostArgs
}
./scripts/verify-readiness-live.ps1 -RuntimeMode HOST -Phase recovery `
    -Manifest $taskPrepared -Report (Join-Path $taskArea 'recovery.json')
python scripts/verify-assessment-live.py --phase retry --manifest $taskPrepared `
    --report (Join-Path $taskArea 'retry.json')
```

Kiểm exit code mỗi native command, giữ report FAIL và dùng report path mới.
Sau pha lỗi, chỉ chạy tiếp khi nguồn đã được phục hồi; các pha độc lập PASS
không xóa gate đã FAIL. Ngày01/11 `database-down` phát hiện owner trả500 thay
503; source Huy `c1a2a074` vẫn cần sửa trước khi nghiệm thu đầy đủ. Gateway503
và draft preservation được kiểm riêng, không thay điều kiện direct readiness.

Nếu sửa/build lại Gateway: `stop-api`, build/package, cập nhật source SHA và
provenance đúng phiên bản rồi `start-api`. Giữ snapshot manifest/runtime trước
khi thay metadata. Không đổi SHA trong evidence cũ thành SHA mới; ghi source
riêng cho mỗi service. `stop` chỉ dừng PID đã đối chiếu start time/executable
và các cluster đúng data directory, giữ dữ liệu để tái hiện.

## Session fault và kết thúc

```powershell
$env:GATEWAY_URL = 'http://127.0.0.1:18080'
$env:HOST_RUNTIME_EVIDENCE = (Resolve-Path (Join-Path $taskArea 'runtime.json')).Path
$env:HOST_PG_BIN = (Resolve-Path '.tools/pgsql-17.11/pgsql/bin').Path
$env:ALLOW_TEST_FAULT_INJECTION = '1'
$env:SESSION_REPORT = Join-Path $taskArea 'session.json'
# Dùng Python và pylibs cùng ABI; bộ pylibs hiện tại dành cho Python3.12.
python scripts/verify-session-outage.py
python scripts/w6-host-runtime.py stop @taskHostArgs
```

Session fault tạm thu hồi SELECT của runtime role trên bảng session trong DB
User test, khôi phục ở `finally`. Report kiểm503, CSRF/session403, không ghi
Course và recovery. Không suy thành full network/pool matrix. Khi kết thúc,
giữ reports/volumes, loại credentials/JWT/cookie khỏi artifact công bố.

Tests không thay quyết định #42, peer review PR #1 hoặc ký nhận bàn giao Huy.
