# Tooling

`verify-khoa-w9.ps1` chạy regression #41 trên Docker TEST riêng: Maven/contracts,
User/Content/import, owner APIs, Gateway, network/pool/session faults, readiness
bốn pha, retry/retake, ACL, backup/restore và HTTP sau restart. Tạo report directory
mới dưới `.tools`, seed qua API, dừng đúng stack trong finally và giữ volumes.
`test_runtime.py` kiểm opt-in, project/container labels, Gateway loopback/port và DB
không public trước session fault/restore/restart. Hướng dẫn hiện hành tại
[runbook W9](../docs/runbooks/khoa-w9.md).

Repository helpers có hướng dẫn chạy và owner; migration PR #1 kiểm tra toàn bộ relative paths. Không đưa business logic/shared entities vào scripts.

`verify-readiness-live.ps1` (Khoa) gọi acceptance driver Java/JUnit bằng `TokenCodec` hiện có. Chỉ opt-in theo pha với manifest TEST và report path mới; hướng dẫn fixture/handoff, các pha online/service-down/database-down/recovery ở [runbook](../docs/runbooks/khoa-w9.md). JUnit mặc định skip `AssessmentReadinessLiveTest`; `ReadinessAcceptanceTest` chỉ kiểm tra driver với HTTP stubs, không phải Assessment LIVE.

`verify-assessment-live.py` bổ sung hai pha qua Gateway thật: `seed` tạo/reconcile Exercise/Question theo sáu Lesson trong prepared manifest; `retry` kiểm cùng key/cùng attempt, key mới/retake, submit, CORS và session/RBAC. Cần `RUN_LIVE_ASSESSMENT=1`, project TEST và report path mới. Không dùng DB SQL để seed; không log credentials hoặc response bodies. Xem [runbook W6 LIVE](../docs/runbooks/khoa-w6-live.md).

`w6-host-runtime.py` quản lý bốn JAR và ba PostgreSQL17 TEST clusters riêng khi Docker engine chưa sẵn. `verify-readiness-live.ps1 -RuntimeMode HOST` kiểm PID/start time/port/JAR hash theo provenance; `verify-session-outage.py` hỗ trợ namespace HOST với psql loopback. Mặc định wrapper vẫn DOCKER. Xem [runbook HOST](../docs/runbooks/khoa-w6-host-live.md); không dùng kết quả HOST thay Docker deployment hoặc team approval.
