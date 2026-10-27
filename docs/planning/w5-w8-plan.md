# Kế hoạch issue W5–W8 — 27/10/2026

Lê Tiến Hưng yêu cầu phân đủ bốn tuần development theo [Timeline Trello](https://trello.com/c/YR8sC8P2). Có **39 issue**, gồm **19 issue cũ được tái sử dụng và 20 issue mới #23–#42**; đủ bốn milestone. Trello chỉ đọc; bỏ CI/CD, kiểm thử thủ công và peer review trong từng tuần. Đây là kế hoạch PM có mục tiêu/remaining, chưa phải xác nhận capacity hoặc nghiệm thu thay thành viên.

Dữ liệu kiểm tra được ở [w5-w8-issues.json](w5-w8-issues.json). Trạng thái checklist mới nhất ở GitHub; JSON là snapshot lập kế hoạch, không tự đồng bộ kết quả chạy sau này.

## Phạm vi, baseline và phân công

SRS/UC-AC 0.4; DD 0.2; public/internal API approved 0.1; UI 0.6. PB-01→13, UC-01→11 và AC-01→23 giữ nguyên. Không thêm Goal, Question CSV, mastery hoặc Delete/soft-delete Course/Module/Lesson; My Vocabulary thuộc Content. Source/nullable/unique/timer/event/statistics theo contract, không lấy ví dụ Figma làm rule.

- Hưng (@LeTHung): PM và toàn bộ React Frontend, FE-01→07.
- Khoa (@KhoaTryHard): User/Content/Gateway, schema/migration/tests nguồn mình; review và hỗ trợ tích hợp.
- Huy (@Huy1188): Assessment/Progress/Kafka, schema/event/runtime tests phần mình. Repo hiện chỉ có collaborator Khoa/Hưng, nên [#23](https://github.com/KhoaTryHard/english-learning-platform/issues/23) giao Khoa xử lý quyền; Responsible Huy vẫn giữ trong body, chưa giả có Assignee.

## W5 — 19/10–25/10/2026

[Milestone W5](https://github.com/KhoaTryHard/english-learning-platform/milestone/2) giữ kế hoạch gốc, không tạo actual trong quá khứ.

| Issue | Phạm vi | Responsible | Cách tính |
|---|---|---|---|
| [#2](https://github.com/KhoaTryHard/english-learning-platform/issues/2) | W5-00 — Khởi động triển khai và kiểm tra điều kiện sẵn sàng | Hưng | Lịch sử W5 |
| [#3](https://github.com/KhoaTryHard/english-learning-platform/issues/3) | W5-01 — Khoa triển khai User, Learning Content và API Gateway | Khoa | Lịch sử W5 |
| [#4](https://github.com/KhoaTryHard/english-learning-platform/issues/4) | W5-02 — Huy tạo nền tảng Assessment, Progress và Kafka | Huy | Lịch sử W5 |
| [#5](https://github.com/KhoaTryHard/english-learning-platform/issues/5) | W5-03 — Hưng xây dựng Frontend React và luồng học cơ bản | Hưng | Bù review W6 |
| [#6](https://github.com/KhoaTryHard/english-learning-platform/issues/6) | W5-04 — Kiểm thử tích hợp lát MVP đầu tiên | Hưng | Lịch sử W5 |
| [#7](https://github.com/KhoaTryHard/english-learning-platform/issues/7) | W5-05 — Review kết quả tuần 5 và bàn giao W6 | Hưng | Lịch sử W5 |

## W6 — 26/10–01/11/2026

[Milestone W6](https://github.com/KhoaTryHard/english-learning-platform/milestone/1) là ngày đích khép phạm vi; chi tiết giờ mục tiêu (VN) ở từng issue.

| Issue | Phạm vi | Responsible | Cách tính |
|---|---|---|---|
| [#13](https://github.com/KhoaTryHard/english-learning-platform/issues/13) | W6-09 [BE-U/C/G] Chuyển cây PR #1 và khép User/Content/Gateway | Khoa | 4h remaining owner |
| [#19](https://github.com/KhoaTryHard/english-learning-platform/issues/19) | W6-00 — Rà soát baseline Drive/GitHub và ghi actual W5, kế hoạch W6 | Hưng | Đã hoàn tất |
| [#20](https://github.com/KhoaTryHard/english-learning-platform/issues/20) | W6-03 [BE-APT] Đối chiếu W3-03 data dictionary với approved API/DD | Huy | 3h remaining owner |
| [#21](https://github.com/KhoaTryHard/english-learning-platform/issues/21) | W6-04 [BE-G1] Route Assessment/Progress và Idempotency-Key qua Gateway | Khoa | 3h remaining owner |
| [#23](https://github.com/KhoaTryHard/english-learning-platform/issues/23) | W6-05 [Quyền repo] Khắc phục Assignee Huy1188 và xác nhận quy trình PR | Khoa | 1h remaining owner |
| [#24](https://github.com/KhoaTryHard/english-learning-platform/issues/24) | W6-06 [FE-03] Khép Lesson focus mode và kiểm thử nội dung học | Hưng | 2h remaining owner |
| [#25](https://github.com/KhoaTryHard/english-learning-platform/issues/25) | W6-07 [BE-A1/A2/A3] Assessment thật: Question, readiness và Exercise → Result | Huy | 18h remaining owner |
| [#26](https://github.com/KhoaTryHard/english-learning-platform/issues/26) | W6-08 [QA/PM] Smoke W6, peer review và bàn giao W7 | Hưng | 3h remaining owner |
| [#42](https://github.com/KhoaTryHard/english-learning-platform/issues/42) | W6-10 [Contract/Security] Khép internal auth User/Gateway để Assessment/Progress tích hợp | Khoa | 3h remaining owner + Huy1h review |

## W7 — 02/11–08/11/2026

[Milestone W7](https://github.com/KhoaTryHard/english-learning-platform/milestone/3) là ngày đích khép phạm vi; chi tiết giờ mục tiêu (VN) ở từng issue.

| Issue | Phạm vi | Responsible | Cách tính |
|---|---|---|---|
| [#9](https://github.com/KhoaTryHard/english-learning-platform/issues/9) | W7-08 [FE-04] Tích hợp Exercise/Quiz/Test/Attempt/Result — code từ W6 | Hưng | 4h remaining owner |
| [#14](https://github.com/KhoaTryHard/english-learning-platform/issues/14) | W7-09 [BE-C3/C4] Review/retest My Vocabulary và Import atomic | Khoa | 4h remaining owner |
| [#15](https://github.com/KhoaTryHard/english-learning-platform/issues/15) | [Theo dõi BE-A1→A4][W6–W7] Assessment, Attempt/Result và deadline | Huy | Theo dõi; effort ở child |
| [#27](https://github.com/KhoaTryHard/english-learning-platform/issues/27) | W7-01 [BE-A3/A4] Quiz/Test, server deadline, resume và chấm điểm an toàn | Huy | 12h remaining owner |
| [#28](https://github.com/KhoaTryHard/english-learning-platform/issues/28) | W7-02 [FE-06] Admin User và quản lý Course/Module/Lesson/Content | Hưng | 8h remaining owner |
| [#29](https://github.com/KhoaTryHard/english-learning-platform/issues/29) | W7-03 [FE-06] Admin import Vocabulary XLSX và Lesson DOCX | Hưng | 4h remaining owner |
| [#30](https://github.com/KhoaTryHard/english-learning-platform/issues/30) | W7-04 [BE-K1] Kafka broker/topic và Outbox relay AssessmentCompleted | Huy | 7h remaining owner |
| [#31](https://github.com/KhoaTryHard/english-learning-platform/issues/31) | W7-05 [BE-U/C] Kiểm chứng owner APIs cho Progress và Statistics | Khoa | 4h remaining owner |
| [#32](https://github.com/KhoaTryHard/english-learning-platform/issues/32) | W7-06 [FE-05] Khép My Vocabulary: collection, unavailable và đổi Course | Hưng | 2h remaining owner |
| [#33](https://github.com/KhoaTryHard/english-learning-platform/issues/33) | W7-07 [QA/PM] Tích hợp W7 Quiz/Test/Admin và review kế hoạch W8 | Hưng | 3h remaining owner |

## W8 — 09/11–15/11/2026

[Milestone W8](https://github.com/KhoaTryHard/english-learning-platform/milestone/4) là ngày đích khép phạm vi; chi tiết giờ mục tiêu (VN) ở từng issue.

| Issue | Phạm vi | Responsible | Cách tính |
|---|---|---|---|
| [#8](https://github.com/KhoaTryHard/english-learning-platform/issues/8) | [Theo dõi FE-03][W6–W8] Lesson focus mode và Learner Dashboard | Hưng | Theo dõi; effort ở child |
| [#10](https://github.com/KhoaTryHard/english-learning-platform/issues/10) | [Theo dõi FE-05][W7–W8] My Vocabulary, Progress và History | Hưng | Theo dõi; effort ở child |
| [#11](https://github.com/KhoaTryHard/english-learning-platform/issues/11) | [Theo dõi FE-06][W7–W8] Admin A-01→A-07 | Hưng | Theo dõi; effort ở child |
| [#12](https://github.com/KhoaTryHard/english-learning-platform/issues/12) | W8-08 [FE-07] Responsive, accessibility và UI regression toàn MVP | Hưng | 4h remaining owner |
| [#16](https://github.com/KhoaTryHard/english-learning-platform/issues/16) | [Theo dõi BE-K1/K2/P1][W7–W8] Outbox → Kafka → Progress idempotent | Huy | Theo dõi; effort ở child |
| [#17](https://github.com/KhoaTryHard/english-learning-platform/issues/17) | [Theo dõi BE-P2/Architecture][W6–W8] Progress/History/Statistics và dữ liệu | Huy | Theo dõi; effort ở child |
| [#34](https://github.com/KhoaTryHard/english-learning-platform/issues/34) | W8-01 [BE-K2/P1] Consumer idempotent và LessonCompletion/Progress thật | Huy | 8h remaining owner |
| [#35](https://github.com/KhoaTryHard/english-learning-platform/issues/35) | W8-02 [BE-P2] Progress, History và Learner Dashboard APIs | Huy | 6h remaining owner |
| [#36](https://github.com/KhoaTryHard/english-learning-platform/issues/36) | W8-03 [BE-P2] Sáu nhóm Statistics qua owner APIs và filter Course/Level | Huy | 5h remaining owner |
| [#37](https://github.com/KhoaTryHard/english-learning-platform/issues/37) | W8-04 [FE-06] Admin Exercise/Quiz/Test và Question management | Hưng | 5h remaining owner |
| [#38](https://github.com/KhoaTryHard/english-learning-platform/issues/38) | W8-05 [FE-06] Admin Statistics sáu nhóm metric và trạng thái dữ liệu | Hưng | 4h remaining owner |
| [#39](https://github.com/KhoaTryHard/english-learning-platform/issues/39) | W8-06 [FE-03/05] Nối Dashboard, Progress và History với backend thật | Hưng | 3h remaining owner |
| [#40](https://github.com/KhoaTryHard/english-learning-platform/issues/40) | W8-07 [QA/PM] Smoke toàn MVP và bàn giao Development cho W9 | Hưng | 3h remaining owner |
| [#41](https://github.com/KhoaTryHard/english-learning-platform/issues/41) | W8-09 [BE-U/C/G] Regression bảo mật, persistence và runbook môi trường W9 | Khoa | 4h remaining owner |

## Trình tự và đầu ra từng tuần

**W5 (19–25/10):** giữ sáu issue [#2](https://github.com/KhoaTryHard/english-learning-platform/issues/2), [#3](https://github.com/KhoaTryHard/english-learning-platform/issues/3), [#4](https://github.com/KhoaTryHard/english-learning-platform/issues/4), [#5](https://github.com/KhoaTryHard/english-learning-platform/issues/5), [#6](https://github.com/KhoaTryHard/english-learning-platform/issues/6), [#7](https://github.com/KhoaTryHard/english-learning-platform/issues/7) trong milestone W5 để thấy đúng baseline và phần quá hạn. [#2](https://github.com/KhoaTryHard/english-learning-platform/issues/2) đã đóng ở phạm vi báo cáo readiness. Actual code/test bổ sung 27/10 không được đổi thành đã làm 25/10. W5 46h (Hưng16/Khoa15/Huy15) là estimate lịch sử, không phải actual và không cộng vào remaining mới.

**W6 (26/10–01/11):** xử lý quyền [#23](https://github.com/KhoaTryHard/english-learning-platform/issues/23), schema/engine [#20](https://github.com/KhoaTryHard/english-learning-platform/issues/20), chuyển cây backend [#13](https://github.com/KhoaTryHard/english-learning-platform/issues/13), internal auth/compatibility [#42](https://github.com/KhoaTryHard/english-learning-platform/issues/42), Exercise thật [#25](https://github.com/KhoaTryHard/english-learning-platform/issues/25) và Gateway [#21](https://github.com/KhoaTryHard/english-learning-platform/issues/21). Hưng bù review [#5](https://github.com/KhoaTryHard/english-learning-platform/issues/5) (2h), khép Lesson [#24](https://github.com/KhoaTryHard/english-learning-platform/issues/24) và review code/MOCK FE-04 [#9](https://github.com/KhoaTryHard/english-learning-platform/issues/9) (1h). [#26](https://github.com/KhoaTryHard/english-learning-platform/issues/26) smoke/review/handoff cả nhóm, gồm xem lại báo cáo [#6](https://github.com/KhoaTryHard/english-learning-platform/issues/6)/[#7](https://github.com/KhoaTryHard/english-learning-platform/issues/7). Chỉ các bước dùng service chưa sẵn mới BLOCKED, không bắt UI/unit tests độc lập chờ toàn bộ.

**W7 (02–08/11):** Huy khép Quiz/Test/deadline [#27](https://github.com/KhoaTryHard/english-learning-platform/issues/27) và Outbox relay/Kafka [#30](https://github.com/KhoaTryHard/english-learning-platform/issues/30). Khoa rà owner source APIs [#31](https://github.com/KhoaTryHard/english-learning-platform/issues/31), review/rerun import/My Vocabulary [#14](https://github.com/KhoaTryHard/english-learning-platform/issues/14). Hưng nối FE-04 thật [#9](https://github.com/KhoaTryHard/english-learning-platform/issues/9) (3h còn lại), Admin User/Content [#28](https://github.com/KhoaTryHard/english-learning-platform/issues/28), Import [#29](https://github.com/KhoaTryHard/english-learning-platform/issues/29), My Vocabulary [#32](https://github.com/KhoaTryHard/english-learning-platform/issues/32). [#33](https://github.com/KhoaTryHard/english-learning-platform/issues/33) kiểm thử/review và ghi remaining W8.

**W8 (09–15/11):** Huy consumer/completion [#34](https://github.com/KhoaTryHard/english-learning-platform/issues/34), Progress/History/Dashboard API [#35](https://github.com/KhoaTryHard/english-learning-platform/issues/35) và Statistics [#36](https://github.com/KhoaTryHard/english-learning-platform/issues/36). Hưng Admin Assessment [#37](https://github.com/KhoaTryHard/english-learning-platform/issues/37), Statistics [#38](https://github.com/KhoaTryHard/english-learning-platform/issues/38), Progress/Dashboard/History thật [#39](https://github.com/KhoaTryHard/english-learning-platform/issues/39), responsive/accessibility [#12](https://github.com/KhoaTryHard/english-learning-platform/issues/12). Khoa regression/security/persistence/runbook [#41](https://github.com/KhoaTryHard/english-learning-platform/issues/41). [#40](https://github.com/KhoaTryHard/english-learning-platform/issues/40) smoke toàn MVP, trace và bàn giao development cho W9; không tuyên bố W9/W10 đã hoàn tất.

Các ngày đích cụ thể ở issue là **mục tiêu lập kế hoạch theo giờ Việt Nam**. Milestone GitHub lưu ngày cuối tuần, không thay giờ review trong issue. Nếu owner báo thiếu capacity, PM cập nhật remaining/mốc và lý do; không sửa actual hay giả Done.

## Dependency và tránh vòng chờ

Chuỗi chính: [#20](https://github.com/KhoaTryHard/english-learning-platform/issues/20)/[#42](https://github.com/KhoaTryHard/english-learning-platform/issues/42) → [#25](https://github.com/KhoaTryHard/english-learning-platform/issues/25) → [#21](https://github.com/KhoaTryHard/english-learning-platform/issues/21) → [#26](https://github.com/KhoaTryHard/english-learning-platform/issues/26); [#25](https://github.com/KhoaTryHard/english-learning-platform/issues/25) → [#27](https://github.com/KhoaTryHard/english-learning-platform/issues/27) → [#9](https://github.com/KhoaTryHard/english-learning-platform/issues/9) → [#33](https://github.com/KhoaTryHard/english-learning-platform/issues/33). API Exercise được Huy kiểm thử trực tiếp trước; Gateway route/Idempotency-Key xác minh sau, FE E2E ở QA tuần. Không buộc Assessment phải chờ Gateway rồi Gateway lại chờ Assessment để bắt đầu code.

Chuỗi event/data: [#25](https://github.com/KhoaTryHard/english-learning-platform/issues/25) (Result+Outbox atomic) → [#30](https://github.com/KhoaTryHard/english-learning-platform/issues/30) (relay/broker) → [#34](https://github.com/KhoaTryHard/english-learning-platform/issues/34) (consumer/dedupe) → [#35](https://github.com/KhoaTryHard/english-learning-platform/issues/35) (APIs) → [#36](https://github.com/KhoaTryHard/english-learning-platform/issues/36)/[#39](https://github.com/KhoaTryHard/english-learning-platform/issues/39) → [#38](https://github.com/KhoaTryHard/english-learning-platform/issues/38)/[#40](https://github.com/KhoaTryHard/english-learning-platform/issues/40). User/Content sources [#31](https://github.com/KhoaTryHard/english-learning-platform/issues/31) hỗ trợ Progress/Statistics; chỉ gọi owner API, không cross-service SQL/FK.

Trong JSON, dependsOn là điều kiện khép phần integration; related là phối hợp/tham chiếu. Có thể viết code/unit/MOCK độc lập trước. Parent [#8](https://github.com/KhoaTryHard/english-learning-platform/issues/8), [#10](https://github.com/KhoaTryHard/english-learning-platform/issues/10), [#11](https://github.com/KhoaTryHard/english-learning-platform/issues/11), [#15](https://github.com/KhoaTryHard/english-learning-platform/issues/15), [#16](https://github.com/KhoaTryHard/english-learning-platform/issues/16), [#17](https://github.com/KhoaTryHard/english-learning-platform/issues/17) chỉ tổng hợp phạm vi và giữ AC; effort riêng bằng 0, không cộng parent + child cho cùng việc. Một task liên quan nhiều parent (ví dụ [#39](https://github.com/KhoaTryHard/english-learning-platform/issues/39)) vẫn chỉ tính effort một lần.

## Remaining đề xuất và capacity

Đơn vị là giờ công **most-likely** cho phần còn lại, gồm hỗ trợ QA đã ghi trong phân bổ. O/M/P chi tiết của task mới nằm trong issue/JSON. Parent tổng hợp, báo cáo đã hoàn tất và code đã có không tính lại.

| Tuần | Hưng | Khoa | Huy | Tổng |
|---|---:|---:|---:|---:|
| W6 | 8h | 13h | 23h | 44h |
| W7 | 20h | 9h | 20h | 49h |
| W8 | 19h | 5h | 20h | 44h |
| Tổng remaining W6–W8 | 47h | 27h | 63h | 137h |

[#9](https://github.com/KhoaTryHard/english-learning-platform/issues/9) tổng remaining 4h được chia W6 1h + W7 3h. [#5](https://github.com/KhoaTryHard/english-learning-platform/issues/5) giữ milestone W5 nhưng 2h review thực hiện W6. Internal auth [#42](https://github.com/KhoaTryHard/english-learning-platform/issues/42) là 3h Khoa + 1h Huy. QA [#26](https://github.com/KhoaTryHard/english-learning-platform/issues/26)/[#33](https://github.com/KhoaTryHard/english-learning-platform/issues/33)/[#40](https://github.com/KhoaTryHard/english-learning-platform/issues/40) mỗi task 3h Hưng + 1h Khoa + 1h Huy; schema [#20](https://github.com/KhoaTryHard/english-learning-platform/issues/20) là 3h Huy + 1h Khoa. Không cộng thêm hỗ trợ mặc định ở các task đã bao gồm trong owner work.

Huy là điểm nghẽn (23/20/20h), chưa xác minh khả năng đáp ứng. Nếu chỉ giả sử mỗi người rảnh 16h/tuần, Huy thiếu khoảng 15h trong ba tuần; Hưng vượt 16h ở W7/W8 dù tổng ba tuần là 47h. Đây chỉ là scenario, không phải capacity thực của nhóm. Ở review W6 [#26](https://github.com/KhoaTryHard/english-learning-platform/issues/26) cần đối chiếu lịch thật, kéo sớm UI/Admin độc lập khi có thời gian và bố trí hỗ trợ test/review, giữ service owner đã chốt. Nếu vẫn thiếu, ghi replan công khai và ảnh hưởng mốc W8; không tự bỏ AC để làm đẹp lịch.

PERT roadmap 288,3h và W5 46h là dự toán ở các thời điểm/cấp khác; không cộng thêm với 137h remaining như ba khối công việc độc lập.

## Coverage PB và kiểm thử

| PB | Chức năng | Issue thực thi/khép phạm vi |
|---|---|---|
| PB-01 | Auth/Account | [#5](https://github.com/KhoaTryHard/english-learning-platform/issues/5), [#13](https://github.com/KhoaTryHard/english-learning-platform/issues/13), [#26](https://github.com/KhoaTryHard/english-learning-platform/issues/26), [#41](https://github.com/KhoaTryHard/english-learning-platform/issues/41), [#42](https://github.com/KhoaTryHard/english-learning-platform/issues/42) |
| PB-02 | Profile | [#5](https://github.com/KhoaTryHard/english-learning-platform/issues/5), [#13](https://github.com/KhoaTryHard/english-learning-platform/issues/13), [#26](https://github.com/KhoaTryHard/english-learning-platform/issues/26) |
| PB-03 | Course/Level setup | [#5](https://github.com/KhoaTryHard/english-learning-platform/issues/5), [#13](https://github.com/KhoaTryHard/english-learning-platform/issues/13), [#31](https://github.com/KhoaTryHard/english-learning-platform/issues/31) |
| PB-04 | Course/Lesson/4 khối học liệu | [#13](https://github.com/KhoaTryHard/english-learning-platform/issues/13), [#24](https://github.com/KhoaTryHard/english-learning-platform/issues/24) |
| PB-05 | My Vocabulary | [#14](https://github.com/KhoaTryHard/english-learning-platform/issues/14), [#32](https://github.com/KhoaTryHard/english-learning-platform/issues/32) |
| PB-06 | Exercise | [#25](https://github.com/KhoaTryHard/english-learning-platform/issues/25), [#9](https://github.com/KhoaTryHard/english-learning-platform/issues/9) |
| PB-07 | Quiz/Test/Result | [#27](https://github.com/KhoaTryHard/english-learning-platform/issues/27), [#9](https://github.com/KhoaTryHard/english-learning-platform/issues/9) |
| PB-08 | Progress/History/Dashboard | [#34](https://github.com/KhoaTryHard/english-learning-platform/issues/34), [#35](https://github.com/KhoaTryHard/english-learning-platform/issues/35), [#39](https://github.com/KhoaTryHard/english-learning-platform/issues/39) |
| PB-09 | Admin User/Access | [#13](https://github.com/KhoaTryHard/english-learning-platform/issues/13), [#28](https://github.com/KhoaTryHard/english-learning-platform/issues/28) |
| PB-10 | Admin Content | [#13](https://github.com/KhoaTryHard/english-learning-platform/issues/13), [#28](https://github.com/KhoaTryHard/english-learning-platform/issues/28) |
| PB-11 | Admin Assessment | [#25](https://github.com/KhoaTryHard/english-learning-platform/issues/25), [#27](https://github.com/KhoaTryHard/english-learning-platform/issues/27), [#37](https://github.com/KhoaTryHard/english-learning-platform/issues/37) |
| PB-12 | Admin Statistics | [#31](https://github.com/KhoaTryHard/english-learning-platform/issues/31), [#36](https://github.com/KhoaTryHard/english-learning-platform/issues/36), [#38](https://github.com/KhoaTryHard/english-learning-platform/issues/38) |
| PB-13 | Import XLSX/DOCX | [#14](https://github.com/KhoaTryHard/english-learning-platform/issues/14), [#29](https://github.com/KhoaTryHard/english-learning-platform/issues/29) |

Unit/API/contract/migration/security/error tests thuộc AC của từng owner task. [#26](https://github.com/KhoaTryHard/english-learning-platform/issues/26), [#33](https://github.com/KhoaTryHard/english-learning-platform/issues/33), [#40](https://github.com/KhoaTryHard/english-learning-platform/issues/40) điều phối incremental integration/evidence/review từng tuần; [#12](https://github.com/KhoaTryHard/english-learning-platform/issues/12) kiểm tra responsive/accessibility; [#41](https://github.com/KhoaTryHard/english-learning-platform/issues/41) regression User/Content/Gateway và runbook. Mỗi PASS có command/SHA/env/expected/actual, MOCK/LIVE rõ. BLOCKED có dependency/owner/điều kiện gỡ. Không tính số checkbox thành phần trăm effort hoặc số suite thành số business case đạt.

W9 **16–22/11** giữ full integration/regression/security/import/event acceptance và bug fixing; W10 **23–29/11** deployment/demo/report/closing. Phân đủ issue W5–W8 không có nghĩa các chức năng đã code/test/merge hoặc capacity đã được thành viên ký nhận.

## Điều chỉnh so với kế hoạch W6 trước

[#8](https://github.com/KhoaTryHard/english-learning-platform/issues/8) chuyển thành tracking FE-03 đến W8; Lesson W6 tách [#24](https://github.com/KhoaTryHard/english-learning-platform/issues/24), Dashboard thật ở [#39](https://github.com/KhoaTryHard/english-learning-platform/issues/39). [#9](https://github.com/KhoaTryHard/english-learning-platform/issues/9) giữ phần code/MOCK đã làm ở W6 và chuyển đích real integration sang W7 (06/11) theo backend [#27](https://github.com/KhoaTryHard/english-learning-platform/issues/27), thay mốc đề xuất 01/11 cũ. [#10](https://github.com/KhoaTryHard/english-learning-platform/issues/10)/[#11](https://github.com/KhoaTryHard/english-learning-platform/issues/11)/[#15](https://github.com/KhoaTryHard/english-learning-platform/issues/15)/[#16](https://github.com/KhoaTryHard/english-learning-platform/issues/16)/[#17](https://github.com/KhoaTryHard/english-learning-platform/issues/17) được tách task tuần, không còn chỉ ghi Backlog W6+.

[#2](https://github.com/KhoaTryHard/english-learning-platform/issues/2) và [#19](https://github.com/KhoaTryHard/english-learning-platform/issues/19) giữ CLOSED; các checklist code/tài liệu/test đã đạt được giữ nguyên. Không tự merge PR #1/#18/#22, đổi Trello hoặc ký review thay người khác.
