# Bàn giao Assessment và Progress — W5-02

Owner: Huy1188. Phạm vi #4/#16/#17/#20/#25; [Draft PR #43](https://github.com/KhoaTryHard/english-learning-platform/pull/43)
vào develop. Mã ứng dụng đã kiểm thử: `2825b85` ngày 31/10/2026.

## Thành phần đã triển khai

- [Assessment](../../backend/services/assessment-service/README.md): Question/readiness,
  Attempt/snapshot/answers/deadline, Result và Outbox; Result/Outbox commit atomic.
- [Progress](../../backend/services/progress-service/README.md): History/Completion,
  Progress/Dashboard API, ProcessedEvent và consumer chống trùng; commit DB trước ACK.
- [Kafka foundation](../../infra/kafka/README.md): relay chỉ mark SENT sau broker ACK;
  retry giữ eventId/payload, consumer có retry/DLT/replay. Adapter mặc định tắt.
- Hai service build độc lập, Java 25/Spring Boot 4.0.8, PostgreSQL 17 riêng từng service;
  không shared business JAR hoặc SQL xuyên database.

## Kiểm chứng

[Báo cáo kiểm thử chính](../evidence/w5-02-foundation-2026-10-31.json) ghi lệnh,
môi trường, source manifest và từng suite: **Assessment 31 + Progress 26 = 57 tests**,
không failure/error/skip tại checkpoint đã ghi.

Transaction/HTTP/listener tests dùng PostgreSQL 17.11; Boot/wiring tests dùng H2.
User/Content, publisher và ConsumerRecord/ACK được ghi rõ khi dùng test doubles.
Kafka broker E2E, Docker runtime và tích hợp Gateway/User/Content thật chưa chạy.
JAR Progress chạy với PostgreSQL thật trả health UP; request thiếu identity trả 401.

Chạy `mvnw.cmd -B verify` từ mỗi service; xem README để chọn test JDBC URL.
Kiểm schema canonical/standalone bằng `python scripts/check-huy-contracts.py`.
Chỉnh lý tài liệu không được tính là một lượt chạy test mới.

## Các điểm cần review và tích hợp

1. #20: đối chiếu [Assessment mapping](../architecture/assessment-data-mapping.md),
   [Progress mapping](../architecture/progress-data-mapping.md) và
   [Content ERD](../architecture/diagrams/content-physical-erd.md).
   Mapping JSON/count projection khác cách phân rã logical và còn chờ quyết định.
2. #42: review signed Gateway assertion và breaking session request; xem
   [đối chiếu schema/auth](../reviews/2026-10-31-huy-schema-auth-review.md).
   Giữ approved v0.1; proposal chưa tự trở thành baseline.
3. #25/#21/#26: trao public keys, chuẩn bị seed/URL và chạy readiness/Exercise
   qua Gateway/frontend thật, ghi SHA/image/env/actual.
4. #30/#34: kiểm Kafka broker, duplicate/retry, DLT/replay và restart thật.
5. #31/#35/#36/#41: Statistics/source APIs/repair và security/persistence đầy đủ
   tiếp tục theo AC từng issue. Peer review bởi người khác tác giả trước merge.

Kế hoạch, workload và báo cáo tuần được quản lý trên Trello/Drive. Repository giữ
mã nguồn, tài liệu kỹ thuật và evidence kiểm thử; self-test chưa thay nghiệm thu nhóm.
