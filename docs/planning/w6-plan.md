# W6 — 26/10–01/11/2026, kế hoạch cập nhật 27/10

[Checkpoint code/test/issue của Hưng ngày 27/10](../evidence/w6-hung-checkpoint-2026-10-27.md): frontend có code và self-QA; peer review/real integration vẫn mở, không coi kế hoạch dưới đây là biên bản đã nghiệm thu xong tuần.

Lê Tiến Hưng yêu cầu phân đủ development W5–W8. [Kế hoạch tổng](w5-w8-plan.md) và [manifest issue](w5-w8-issues.json) thay cách ghi Backlog W6+ trước đây. [Milestone W6](https://github.com/KhoaTryHard/english-learning-platform/milestone/1).

## Mục tiêu W6

Khép first-slice Auth/Content, dựng Assessment Exercise thật và Gateway, review frontend có sẵn, smoke/test/review theo đúng runtime. Code/MOCK FE-04 được review W6; Quiz/Test real integration khép W7 theo #27/#9. Dashboard/Progress thật khép W8 #35/#39; không coi mock là hoàn tất hệ thống.

| Task | Responsible | Mục tiêu VN | Remaining owner đề xuất |
|---|---|---|---|
| [#23](https://github.com/KhoaTryHard/english-learning-platform/issues/23) Quyền Huy1188 | Khoa | 27/10 21:00 | 1h |
| [#20](https://github.com/KhoaTryHard/english-learning-platform/issues/20) Mapping/ERD/engine | Huy; Khoa review | 28/10 21:00 | Huy 3h + Khoa 1h |
| [#42](https://github.com/KhoaTryHard/english-learning-platform/issues/42) Internal auth/compatibility | Khoa; Huy review | 29/10 18:00 | Khoa 3h + Huy 1h |
| [#13](https://github.com/KhoaTryHard/english-learning-platform/issues/13) Migration User/Content/Gateway | Khoa | 29/10 21:00 | 4h |
| [#5](https://github.com/KhoaTryHard/english-learning-platform/issues/5) W5 carryover review Auth/Setup | Hưng | 29/10 21:00 | 2h; milestone gốc W5 |
| [#24](https://github.com/KhoaTryHard/english-learning-platform/issues/24) Lesson focus mode | Hưng | 30/10 21:00 | 2h QA/review; code đã có |
| [#25](https://github.com/KhoaTryHard/english-learning-platform/issues/25) Exercise/Question/readiness/Result+Outbox | Huy | 31/10 18:00 | 18h; O/M/P 12/18/30 |
| [#21](https://github.com/KhoaTryHard/english-learning-platform/issues/21) Gateway routes/header/CORS | Khoa | Code 30/10 18:00; LIVE trước QA 01/11 | 3h |
| [#9](https://github.com/KhoaTryHard/english-learning-platform/issues/9) Checkpoint FE-04 code/MOCK | Hưng | 01/11 16:00 | 1h W6; 3h real integration W7 |
| [#26](https://github.com/KhoaTryHard/english-learning-platform/issues/26) QA/review/handoff W6 | Hưng điều phối | 01/11 21:00 | Hưng3/Khoa1/Huy1h |

#19 audit/đồng bộ đã hoàn tất, không estimate lại. #6/#7 là reports W5 cần nhóm review theo #26; đợt test mới có mã W6 và ngày thực. Tổng remaining W6: Hưng8/Khoa13/Huy23h, 44h cả nhóm, không phải actual/capacity được xác nhận.

## Phụ thuộc và thực thi

#20/#42 → #25 (owner API thật) → #21 (proxy/LIVE) → #26. #42 xử lý security/compatibility giữa approved internal v0.1 và proposal/runtime của PR #1, không mặc định chấp nhận proposal. #13 chuyển cây backend theo PR nền #18; có thể test nhánh riêng trước merge. Unit/proxy tests và UI/MOCK đủ contract làm độc lập; chỉ integration chưa ready là BLOCKED. Không yêu cầu #25 đợi Gateway trước khi viết/test owner API.

PR frontend #22 vẫn base chore/monorepo-structure-task-plan. Review/merge #18 trước, retarget #22 về develop, kiểm tra diff và chạy test bị ảnh hưởng; không auto merge Draft hoặc thay backend owner.

## Test, DoD và capacity

- Kiểm thử thủ công: npm run check, browser MOCK/LIVE và backend owner test commands theo phạm vi; CI/CD đã bỏ, hai workflow vẫn tắt.
- Mỗi PASS ghi SHA/env/seed/command/expected/actual/log/ảnh; mock readiness và API thật tách rõ. Checkpoint mới có 22 unit/UI PASS; 6 browser MOCK + 2 LIVE first-slice là evidence trước checkpoint, không tự chứng minh Assessment/Kafka/Progress.
- Dấu hoàn thành phần code/test giữ nguyên; đóng task khi đủ scope AC và peer review/acceptance. Parent hoàn tất sau đủ children/related AC.
- Huy 23h là điểm nghẽn; review capacity và tiến độ tại #26 (triage sớm nếu không đạt #20/#42/#25). Thiếu giờ phải replan và nêu ảnh hưởng W7/W8, không tự bỏ AC hoặc fake Done.

Mốc 01/11 trước đây của toàn FE-04 là đề xuất trước khi phân đủ bốn tuần. Kế hoạch này tách W6 code/MOCK và W7 real Quiz/Test integration 06/11; thay đổi có ghi rõ trong #9 và master plan. [Bản W6 cũ](https://github.com/KhoaTryHard/english-learning-platform/blob/7034666/docs/planning/w6-plan.md) giữ làm lịch sử.
