# W5-02 — Bàn giao foundation của Huy, 31/10/2026

Responsible: Huy1188. Nguồn: Trello W5-02, GitHub #4/#20/#25/#16/#17.
Nhánh: `feature/huy-assessment-foundation`, target `develop`.
Assessment checkpoint đã push: `2f5288a`. Commit kế tiếp bổ sung Progress/relay,
mapping và evidence; PR sẽ liên kết report này. Không backdate thành công việc W5.

## Checklist có bằng chứng ở phạm vi owner

| Trello | Kết quả thực hiện | Phần tách riêng |
|---|---|---|
| H01 Assessment/Question/readiness | Code/migration/test có ở Assessment | Gateway/Content LIVE và peer review |
| H02 Attempt/Result/snapshot/deadline | Code/self-test có, chuyển tiếp đã ghi | Exercise thật qua frontend/Gateway |
| H03 Progress schema/completion/formula | Progress migration, History, Completion unique, formula/API tests | Full repair/Statistics và nghiệm thu #34/#35 |
| H04 Event/Outbox/consumer/ProcessedEvent | Approved schema, relay, consumer, dedupe/ACK/rollback/contract tests | Broker/Kafka E2E NOT_RUN; không tự nghiệm thu #30/#34 |
| H05 DB và W3-02/03 | Mapping Markdown, appendix Word local và backup có bằng chứng | Reviewer xác nhận ERD/mapping/version #20/#42 |
| Gắn evidence/remaining | Report, source manifest, commands, PR refs | Reviewer/merge và kết luận nghiệm thu nhóm |

Tick những phần code/tài liệu/test đúng bằng chứng sau khi người phụ trách kiểm
tra PR. H05 là cập nhật local và mapping của owner, không phải Khoa/Hưng đã duyệt
hoặc Drive online đã được đồng bộ. Toàn thẻ W5-02 chưa tự chuyển Done.

## Phạm vi đã có

- Hai service chạy/build độc lập, PostgreSQL/migration riêng, không shared business JAR.
- Progress event validation, ProcessedEvent + History + Completion + count atomic.
- Event/submission trùng không nhân dữ liệu; conflict rollback, không ghi đè.
- Zero-score Exercise complete, retake không cộng trùng, Quiz/Test chỉ History.
- First-completion snapshot giữ lần sớm nhất nếu event đến lệch thứ tự.
- Progress denominator từ Content gồm hidden Lessons, empty/null và source503 phân biệt.
- History/Dashboard owner scoped, giữ Course/Level cũ khi đổi setup.
- Relay broker-ACK-before-SENT; crash window giữ eventId/payload để replay.
- Consumer commit DB trước ACK, retry 1s/5s/30s, DLT/replay giữ payload/key.
- Kafka enabled/disabled wiring tests; adapter mặc định tắt khi chưa có broker.

## Bằng chứng và cách chạy

Xem `docs/evidence/w5-02-foundation-2026-10-31.json` cho source manifest, commands,
suite counts và phần NOT_RUN. Mỗi service có `mvnw.cmd -B verify`; test mặc định
dùng H2, thêm property test JDBC URL theo README để chạy PostgreSQL thật.

Test doubles được ghi rõ: Content/User trong service/API tests, publisher trong
relay test, ConsumerRecord/ACK trong listener test. Không gọi đó là Kafka E2E.
Hai schema standalone và canonical được đối chiếu bằng `scripts/check-huy-contracts.py`.

## Bàn giao Khoa/Hưng

1. Review [Assessment mapping](../architecture/assessment-data-mapping.md) và
   [Progress mapping](../architecture/progress-data-mapping.md), cập nhật #20.
2. Trao public keys cho caller assessment/progress; không trao private keys.
3. Review signed Gateway assertion/internal compatibility #42, giữ v0.1 frozen.
4. Seed tại User/Content/Assessment owner, cấu hình URL và chạy bốn pha readiness.
5. Chạy frontend/Gateway/owner smoke và Kafka replay/DLT/restart persistence thật.
6. Ghi reviewer, SHA/image/env/results và remaining. Chưa có approval thay người khác.

Full Statistics/source APIs/repair tiếp tục #31/#34/#35/#36; security/persistence
deployment tiếp tục #41. Không cộng effort issue cha và task con hai lần.
