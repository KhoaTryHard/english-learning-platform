# Huy — bàn giao Assessment, 31/10/2026

Nguồn công việc: W5-02/#4, schema #20, Exercise #25; Trello W5-H01/H02/H04/H05.
Nhánh local: `feature/huy-assessment-foundation`, base develop `9eff189`.
Code đang ở working tree, chưa có implementation commit/PR được xuất bản.

## Đã triển khai

- Assessment độc lập Java 25/Spring Boot 4.0.8 và PostgreSQL 17 do Huy chọn.
- Admin Assessment/Question theo public API v0.1; SINGLE_CHOICE/TRUE_FALSE.
- Readiness chỉ đọc Assessment DB, cho caller Content; không callback Content.
- Exercise start/retry/retake/resume/save/submit/Result. Snapshot và key bền vững.
- Deadline/finalizer Quiz/Test, giữ answers server nhận trước hạn; không reset khi resume.
- Result + Outbox + transition atomic; unique submission, row lock chống finalize trùng.
- Authentication adapter khớp cơ chế signed owner handoff của Khoa; RBAC/ownership.
- Migration, Maven Wrapper, Dockerfile/Compose/env mẫu và hướng dẫn seed qua Gateway.

## Bằng chứng thực tế

Xem [report JSON](../evidence/huy-assessment-2026-10-31.json) để lấy source manifest,
base SHA, môi trường, lệnh và phạm vi kiểm tra. Base SHA không đại diện code mới;
source manifest xác định working tree đã kiểm tra.

- `verify`: 28 tests, 0 failure/error/skip.
- 13 service transaction tests và 11 HTTP tests chạy trên PostgreSQL 17.11 thật.
  Owner Content trong các case này là stub; signed HTTP test gọi controller trực
  tiếp qua MockMvc, không dùng Gateway/User thật.
- 3 owner-client tests dùng HTTP stub kiểm service identity/response/error.
- 1 Spring Boot startup/Flyway test dùng H2.
- Event được kiểm theo bản sao không sửa của AssessmentCompleted JSON Schema
  2020-12; negative case Exercise thiếu lessonId bị từ chối.
- Build từ copy service độc lập không có root/sibling modules: PASS.
- Chạy JAR thật với PostgreSQL database test riêng: health UP; public/internal
  request thiếu identity bị 401. Công cụ tạo key riêng cho Assessment chạy đạt.
- Docker Compose/image: NOT_RUN do môi trường không có Docker.

Không cộng những lần chạy lại thành số test mới. Logs/Surefire nằm trong target,
không commit secret, key hay database test.

## Khoa/Hưng cần để tích hợp

1. Review mapping [Assessment data](../architecture/assessment-data-mapping.md) với
   W3-03/#20, đặc biệt Question/options/answers được lưu JSON TEXT trong V1.
2. Trao public keys: Assessment nhận `api.public.pem`; Khoa nhận
   `assessment.public.pem` để User/Content tin cậy internal caller Assessment.
3. Cấu hình USER_URL/CONTENT_URL, ASSESSMENT_URL của Gateway và caller identity.
4. Review #42: adapter sử dụng runtime assertion của nhánh Khoa `bf183461`;
   không đổi approved v0.1 hoặc ghi proposal v0.1.1 đã được nhóm duyệt.
5. Chuẩn bị Learner/Admin, Course/Level/Module/Lesson thật tại owner; seed Exercise
   qua Admin API theo [README](../../backend/services/assessment-service/README.md).
6. Chạy bốn pha readiness, retry/retake và smoke Frontend → Gateway → owner thật.
   Ghi SHA/images/env/expected/actual và kết luận peer review riêng.

## Phần còn lại và trạng thái

- W5-H01/H02: code và self-test Assessment đã có; peer review/tích hợp nhóm còn mở.
- W5-H03: Progress skeleton/schema/formula chưa thực hiện trong thay đổi này.
- W5-H04: Outbox và event contract test đã có; relay #30, consumer/ProcessedEvent
  và Progress #34/#35 chưa triển khai, Kafka E2E NOT_RUN.
- W5-H05: lựa chọn PostgreSQL và mapping đã ghi; review/reconcile W3-03 chung còn mở.
- Chưa xác nhận quyền GitHub Assignee/accept invitation thay tài khoản Huy1188.
- Không tự đóng #4/#20/#25, ký thay reviewer hoặc cập nhật trạng thái Trello.

Đây là bàn giao code/self-test của Assessment; chưa là biên bản nghiệm thu W5/W6
hoặc full MVP. Gắn implementation SHA/PR sau khi code được commit và xuất bản.
