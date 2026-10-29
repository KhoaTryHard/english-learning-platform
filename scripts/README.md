# Tooling

Repository helpers có hướng dẫn chạy và owner; migration PR #1 kiểm tra toàn bộ relative paths. Không đưa business logic/shared entities vào scripts.

`verify-readiness-live.ps1` (Khoa) gọi acceptance driver Java/JUnit bằng `TokenCodec` hiện có. Chỉ opt-in theo pha với manifest TEST và report path mới; hướng dẫn fixture/handoff, các pha online/service-down/database-down/recovery ở [runbook](../docs/runbooks/khoa-w9.md). JUnit mặc định skip `AssessmentReadinessLiveTest`; `ReadinessAcceptanceTest` chỉ kiểm tra driver với HTTP stubs, không phải Assessment LIVE.
