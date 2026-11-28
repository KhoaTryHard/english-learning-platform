# Bàn giao Assessment và Progress — W5-02

Owner: Huy1188; reviewer/bàn giao tích hợp: KhoaTryHard với hỗ trợ Codex.
Phạm vi foundation #4; [PR #43](https://github.com/KhoaTryHard/english-learning-platform/pull/43)
vào `develop`. Checkpoint review 28/11/2026 dùng
`6e263f972522584a9071d80b3c32ead276ec6606`, đã gồm fix DB503 #44 và develop
`793810e1c035b143c119119f08a950b372ebce7b`.

Khoa chấp nhận PostgreSQL17, physical V1/JSON và count projection cho foundation
theo quyết định trực tiếp ngày28/11, với điều kiện review/test đạt. #20 tiếp tục
theo dõi dictionary/ERD/mapping chi tiết; #31 tiếp tục chờ consumer acceptance.
Publicv0.1 giữ nguyên; internal auth0.1.1 đã baselined tại #42.

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

[Checkpoint review và kiểm thử hiện hành](../../.forgewright/code-reviewer/2026-11-28-pr43-closeout.md)
và [manifest](../evidence/pr43-closeout-2026-11-28/manifest.json) ghi source,
PostgreSQL/H2/test doubles, JAR hash, fixture và kết quả HOST theo từng pha.
Docker deployment, Kafka broker E2E và Statistics không thuộc kết luận foundation.

[Báo cáo lịch sử31/10](../evidence/w5-02-foundation-2026-10-31.json) ghi lệnh,
môi trường, source manifest và từng suite: **Assessment 31 + Progress 26 = 57 tests**,
không failure/error/skip tại checkpoint đã ghi.

Transaction/HTTP/listener tests dùng PostgreSQL 17.11; Boot/wiring tests dùng H2.
User/Content, publisher và ConsumerRecord/ACK được ghi rõ khi dùng test doubles.
Kafka broker E2E, Docker runtime và tích hợp Gateway/User/Content thật chưa chạy
trong checkpoint31/10; kết quả sau đó dùng đúng nguồn/ngày tại manifest hiện hành.
JAR Progress chạy với PostgreSQL thật trả health UP; request thiếu identity trả 401.

Chạy `mvnw.cmd -B verify` từ mỗi service; xem README để chọn test JDBC URL.
Kiểm schema canonical/standalone bằng `python scripts/check-huy-contracts.py`.
Chỉnh lý tài liệu không được tính là một lượt chạy test mới.

## Các điểm cần review và tích hợp

1. #20: đối chiếu [Assessment mapping](../architecture/assessment-data-mapping.md),
   [Progress mapping](../architecture/progress-data-mapping.md) và
   [Content ERD](../architecture/diagrams/content-physical-erd.md).
   Mapping JSON/count projection được Khoa chấp nhận cho foundation ngày28/11;
   cập nhật dictionary/ERD và cross-review còn lại giữ tại #20.
2. #42 đã Closed: signed Gateway assertion và breaking session request dùng
   [baseline internal0.1.1](../contracts/internal-auth-decision-2026-11-01.md).
   User/Gateway triển khai và rollback theo cặp; không default requestMethod=GET.
3. #25/#21/#26: trao public keys, chuẩn bị seed/URL và chạy readiness/Exercise
   qua Gateway/frontend thật, ghi SHA/image/env/actual.
4. #30/#34: kiểm Kafka broker, duplicate/retry, DLT/replay và restart thật.
5. #31/#35/#36/#41: Statistics/source APIs/repair và security/persistence đầy đủ
   tiếp tục theo AC từng issue. Peer review bởi người khác tác giả trước merge.

Kế hoạch, workload và báo cáo tuần được quản lý trên Trello/Drive. Repository giữ
mã nguồn, tài liệu kỹ thuật và evidence kiểm thử; self-test chưa thay nghiệm thu nhóm.
