# W5-04 — Cập nhật evidence phía Huy ngày 31/10/2026

Mã checkpoint: E-HUY-20261031. Đây là bổ sung W6 cho công việc chuyển tiếp W5,
không phải test đã chạy vào 25/10. Giữ nguyên snapshot 27/10 và evidence Khoa 29/10.
Tài liệu này tổng hợp kết quả đã chạy ngày 31/10; không có lượt rerun mới do cập nhật prose.

Code đã kiểm thử: `2825b85b2dd57e66bbc7aa3ff17d2199ae073455`.
Publication: `0348a94`; [Draft PR #43](https://github.com/KhoaTryHard/english-learning-platform/pull/43).
Java 25.0.3, Maven 3.9.11, PostgreSQL 17.11; DB test riêng assessment_test/progress_test.

| Case W5 | Steps/Expected | Actual và status phía owner | Phần integration |
|---|---|---|---|
| T-03 Assessment foundation | Build, Flyway, cấu hình Question hợp lệ/lỗi, draft/ready; readiness không callback Content | PASS trong owner tests/PostgreSQL và API boundary test; branch/source/contract có evidence | Content/Gateway thật: NOT_RUN |
| T-04 Attempt/Result | Start/retry/retake/resume; full answer-set/false/clear; snapshot, deadline, repeat submit; Result+Outbox atomic | PASS ở service/HTTP tests; concurrent submit không nhân Result, lỗi Outbox rollback | Frontend→Gateway→owner thật: NOT_RUN; cần keys/seed/config và #42 |
| T-05 Progress | Exercise điểm 0; retake; 0/50/100; Quiz/Test không complete; owner history; source down/null; rollback/dedupe | PASS trên PostgreSQL với event/owner test doubles được ghi nhãn | Event nhận qua broker thật: NOT_RUN |
| T-06 Kafka | Contract, relay retry/crash window, ProcessedEvent, ACK-after-commit, wiring adapters | PASS ở foundation tests với publisher/ConsumerRecord/ACK doubles | Broker/replay/DLT/consumer restart thật: NOT_RUN vì chưa có Docker/broker |

Tổng 57 tests = Assessment 31 + Progress 26; không chia/cộng lại 57 vào từng case
hoặc cộng những lượt rerun thành test mới. Boot/wiring dùng H2 riêng; không nói
mọi test đều chạy PostgreSQL hoặc mọi dependency đều LIVE.

## Evidence

- [Report source/commands/env/suites](../evidence/w5-02-foundation-2026-10-31.json).
- [Publication và quyền Huy1188](../evidence/w5-02-publication-2026-10-31.json).
- [Danh sách test đã lọc properties nhạy cảm](../evidence/w5-huy-junit-summary-2026-10-31.json).
- Logs gốc tại target từng service; report chuẩn hóa đính kèm trong repository.
- Schema canonical/standalone hash đã kiểm bằng `python scripts/check-huy-contracts.py`.

## Blocker và điều kiện gỡ

| Việc | Owner/phối hợp | Điều kiện và bước kế tiếp |
|---|---|---|
| Physical mapping/dictionary #20 | Huy + Khoa | Chọn cách xử lý JSON/normalized và các khác biệt đã liệt kê trong review |
| Auth compatibility/rollout #42 | Huy review, Khoa owner, Hưng ghi baseline | Huy phản hồi đúng SHA; nhóm chốt version và cặp User/Gateway |
| Readiness/Exercise LIVE #25/#21/#26 | Huy/Khoa/Hưng | Trao public keys, seed/URL, chạy bốn pha readiness và frontend smoke |
| Kafka E2E #30/#34 | Huy, môi trường nhóm | Có broker/Docker; chạy retry, dedupe, DLT/replay/restart với actual logs |
| Peer review PR #43 | Reviewer khác tác giả | Ghi reviewer, SHA và kết luận; self-test không thay acceptance |

Không chuyển trạng thái các thẻ/issue hoặc sửa kết quả của thành viên khác.
