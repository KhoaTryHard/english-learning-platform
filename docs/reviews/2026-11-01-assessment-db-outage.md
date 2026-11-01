# Defect Assessment: database-down trả500 thay503

Owner: Huy, PR #43. Phát hiện khi Khoa/Codex chạy W6-02 LIVE trên host ngày
01/11/2026; không sửa source hoặc branch của Huy trong lần này.

## Tái hiện

- Source Assessment: `c1a2a074a4cc6c0d3d0bdf04ef77540a530c5930`.
- JAR SHA-256: `f9e37dbb119544d1c78c2d223c2c4efb6efa1ccaafa49e95aa747a1a5daea9bf`.
- Java25.0.5, PostgreSQL17.11 cluster riêng15543, Flyway V1 thành công. App8090
  giữ chạy khi DB bị dừng bằng `pg_ctl -m fast -w stop` đúng data directory test.
- Exercise ready có một TRUE_FALSE hợp lệ; direct readiness trước outage200.
- Dừng Assessment DB; gọi `GET /internal/v1/lessons/{lessonId}/exercise-readiness`
  bằng JWT caller `content`, audience `assessment`, chữ ký hợp lệ.

**Expected:** 503 `DEPENDENCY_UNAVAILABLE`. **Actual:**500. Pha
`database-down` FAIL ngay tại direct probe; report và log thất bại được giữ.
Không đổi expected hoặc coi Gateway503 là thay thế cho direct503.

Report chi tiết: [database-down](../evidence/w6-host-live-2026-11-01/database-down.json).
Gateway/Content đã kiểm riêng trong cùng outage: publish503
`DEPENDENCY_UNAVAILABLE`, Lesson giữ draft. Sau DB phục hồi, recovery publish
và Learner GET thành công. Xem [propagation](../evidence/w6-host-live-2026-11-01/database-propagation.json)
và [recovery](../evidence/w6-host-live-2026-11-01/recovery.json).

## Điểm cần Huy kiểm tra

`AssessmentService` có `@Transactional`; `Errors` chỉ map `DataAccessException`
sang503, generic exception sang500. Lỗi mở transaction/connection như
`CannotCreateTransactionException` không thuộc `DataAccessException`. Đây là
hướng điều tra từ source; chưa instrument controller để khẳng định exception
class cụ thể của request LIVE.

Đề xuất sửa có giới hạn: map lỗi không mở được transaction do dependency sang
503 đã chuẩn hóa; không map mọi business/transaction exception thành503, không
trả root SQL/JDBC message. Thêm regression HTTP qua advice và chạy lại outage
với PostgreSQL thật. Không đổi schema/V1 hoặc tắt transaction để vượt test.

## Nghiệm thu lại

1. Huy cung cấp commit/JAR mới và kết quả regression của mình.
2. Dựng TEST ở đúng source, dùng fixture draft mới; chạy online, service-down,
   database-down và recovery. Direct503 lẫn publish503 đều phải đạt.
3. Nếu thay đổi ảnh hưởng start/session, chạy lại retry/retake qua Gateway.
4. Khoa/Huy review evidence, giữ #25/W6-02 mở cho đến khi gate đạt; auth #42 và
   peer review PR #1 vẫn là quyết định riêng.
