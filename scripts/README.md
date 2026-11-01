# Tooling

Repository helpers có hướng dẫn chạy và owner; migration PR #1 kiểm tra toàn bộ relative paths. Không đưa business logic/shared entities vào scripts.

`verify-readiness-live.ps1` (Khoa) gọi acceptance driver Java/JUnit bằng `TokenCodec` hiện có. Chỉ opt-in theo pha với manifest TEST và report path mới; hướng dẫn fixture/handoff, các pha online/service-down/database-down/recovery ở [runbook](../docs/runbooks/khoa-w9.md). JUnit mặc định skip `AssessmentReadinessLiveTest`; `ReadinessAcceptanceTest` chỉ kiểm tra driver với HTTP stubs, không phải Assessment LIVE.

`verify-assessment-live.py` bổ sung hai pha qua Gateway thật: `seed` tạo/reconcile Exercise/Question theo sáu Lesson trong prepared manifest; `retry` kiểm cùng key/cùng attempt, key mới/retake, submit, CORS và session/RBAC. Cần `RUN_LIVE_ASSESSMENT=1`, project TEST và report path mới. Không dùng DB SQL để seed; không log credentials hoặc response bodies. Xem [runbook W6 LIVE](../docs/runbooks/khoa-w6-live.md).
