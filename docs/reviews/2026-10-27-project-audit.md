# Rà soát dự án — 27/10/2026 (Asia/Saigon)

Người điều phối: **Lê Tiến Hưng — LeTHung**. Nguồn: [Trello t5d](https://trello.com/b/2sSouPca/t5d), [Drive nhóm](https://drive.google.com/drive/folders/1_mG-XznoqDQku_86nEmsBntTLxNYts_h), [GitHub](https://github.com/KhoaTryHard/english-learning-platform). Trello và Drive chỉ đọc trong phiên này.

## Kết luận

**Phạm vi và ownership chính đã khá nhất quán; hồ sơ chưa đồng nhất hoàn toàn và quy trình triển khai chưa khép kín. Có thể code frontend ngay theo contract, nhưng chưa thể nghiệm thu toàn hệ thống.** Điểm chặn thực tế là code còn tách trên PR chưa review/merge, Assessment/Progress/Kafka chưa có implementation trên các nhánh được thấy, schema còn lệch và evidence W5 còn là mẫu.

W5 được Drive xác định **19–25/10/2026**. Vì vậy hôm nay là **W6, 26/10–01/11/2026**. Đây là phép tính từ baseline W5, không lấy ngày tạo repo làm tuần 1. Trello định hướng W5–W8 development, W9 integration/testing, W10 deployment/closing; không có yêu cầu phải viết xong toàn MVP ở W6.

## Phạm vi kiểm tra và nguồn hiện hành

- [Danh mục 36 file Drive](drive-inventory-2026-10-27.md): KICK-OFF 1, W1 2, W2 3, W3 20, W4 6, W5 4; gồm tài liệu, YAML/JSON/CSV, mẫu và fixture import. Đã kiểm tra 10 ảnh Architecture/Database W3, đọc raw XLSX để tránh text preview bỏ dòng.
- Trello trả 60 thẻ, 6 danh sách, không còn trang tiếp theo trong kết quả đọc. Đã đọc mô tả toàn board. Không đổi thẻ/due/checklist/list/status.
- GitHub: nhánh/commits, PR #1/#18, reviews, issues #2–#17, workflow backend, code Gateway và baseline public contract. Không thay backend hoặc merge thay nhóm.
- Figma: [Learning Studio v2 page 49:2](https://www.figma.com/design/uBp0WuSq9ti9gcDrFZagIc/Untitled?node-id=49-2), context/screenshot các màn frontend triển khai và hai mẫu mobile. Không chỉnh Figma.
- Rà soát này tập trung consistency/traceability/readiness. Không xác nhận chữ ký thành viên, chấm điểm theo rubric giảng viên chưa được cung cấp, hoặc kiểm định hình thức từng trang in của 36 file.

| Loại | Nguồn áp dụng |
|---|---|
| Requirements | W2 SRS/UC-AC v0.4, FR-01→21, UC-01→11, AC-01→23, PB-01→13 |
| Design decisions | W3-00 DD-01→13 DECIDED v0.2 |
| API | Public/internal W3-04 v0.1 APPROVED; nhãn PROPOSED trong file gốc là lịch sử, đọc cùng companion W3-04/06 |
| UI | W3-05 **v0.6**, Learning Studio v2; 23 mã logic ≠ 28 frame desktop + 2 mobile + state |
| Ownership | Hưng FE/PM; Khoa User/Content/Gateway; Huy Assessment/Progress/Kafka |
| Planning | W4-03 phân công, W4-01 PERT/schedule, W5 handoff; kế hoạch W6 mới trong repo |

## Các phần đã nhất quán

No Goal; Course/Level thuộc Content, lựa chọn hiện tại thuộc User. My Vocabulary/USER_VOCABULARY thuộc Content; note đầu W1-05/06 đã supersede ownership/Goal cũ, không cần xóa bản nháp lịch sử.

Lesson chỉ complete sau Exercise hợp lệ, không thêm ngưỡng điểm, GET không complete; Exercise không timer; Quiz/Test dùng server deadline; retake không giới hạn. Result thuộc Assessment, History/Completion thuộc Progress. Outbox + Kafka + consumer idempotent, không cross-service SQL/FK. Import chỉ Vocabulary XLSX và Lesson DOCX, atomic; không Delete/soft-delete Course/Module/Lesson.

W4 đã có owner, dependency, PERT, risk, test matrix và quy tắc chọn task tuần khác PB cha. Các tổng đề xuất W4 **37,5h**, W5 **46h**, PERT W5+ **288,3h** cộng đúng ở mức đã ghi; W5 là phần phân bổ, không cộng thêm 46h lần nữa.

Public OpenAPI Drive và bản trong PR #1 giống nguyên byte: SHA-256 `800a25b92e7272a2e02ea1d08b29612ad58d28d0db85dea9743c25257537e22c`. Frontend giữ nguyên baseline và sinh types từ bản này.

## Các điểm cần xử lý

| ID | Phát hiện, bằng chứng và tác động | Owner / action |
|---|---|---|
| A-01 | W3-05 đã v0.6, nhưng Charter/W3-06/W4/W5 và một số card vẫn nói UI v0.5, 23 màn hoặc node 0:1. Người code dễ dùng frame cũ. | Hưng: thêm chỉ dẫn hiện hành v0.6 ở đầu tài liệu, giữ lịch sử thiết kế. #19 |
| A-02 | Drive W4/W5 và issue cũ còn link t4-c/shortLink cũ, trong khi board được yêu cầu là t5d. Kết quả đọc hiện tại W4 Done; Khoa/Hưng W5 Doing; Huy/test/review To Do. Snapshot 20/10 ghi Doing/To Do là lịch sử, không phải trạng thái live 27/10. | Hưng: top-level current source/status note; không rewrite snapshot hoặc suy Done kỹ thuật từ list. #19 |
| A-03 | Due W4 trên board khác ngày trong W4-00/01/05. Xem bảng dưới. | Hưng: phân biệt schedule baseline và replan/actual, ghi nguyên nhân có nguồn. #19 |
| A-04 | W5-04 vẫn TEMPLATE, T-01→10 chưa có hàng actual; W5-05 vẫn TBD, chưa là biên bản review 25/10. Chưa thấy W5-00 trong 60 thẻ trả về của t5d, dù issue #2 và Drive còn. | Hưng + owners: điền ngày thực sự chạy/review, evidence và phần chưa làm; đối chiếu vị trí W5-00. #6/#7/#19 |
| A-05 | main/develop `879a0de` chỉ README. Code Khoa `56450fc` ở PR #1 Draft; scaffold `11702c8` ở PR #18. Cả hai **không có submitted review** trong lần đọc. | Khoa/Huy review đúng scope, không merge tự động. Frontend PR xếp chồng trên #18; sau merge nền đổi base về develop. #13 |
| A-06 | #4/#15–#17 chưa có assignee thật. Kiểm tra assignability Huy1188 ngày 27/10 trả 404; Responsible trong mô tả vẫn là Huy. | Owner repo kiểm tra collaborator/invitation/username; sau đó gán lại, không chuyển owner sang Hưng. #20 cũng ghi Responsible Huy |
| A-07 | W3-03 unique Vocabulary chỉ `(lesson_id, normalized_word)`, trong DD-05/API dùng cả normalizedMeaning. display_name NN khác API fullName nullable. lesson_id/snapshot NN khác Quiz/Test/event có null. Chưa có dictionary LearningContent GRAMMAR/LISTENING/READING, MODULE.published; length/mapping cần đối chiếu. | Huy sửa dictionary/migration tương ứng, Khoa review; không tự sửa approved API. [#20](https://github.com/KhoaTryHard/english-learning-platform/issues/20) |
| A-08 | Gateway PR #1 chỉ cấu hình User/Content; owner khác User bị chọn Content. Không forward Idempotency-Key, CORS Allow-Headers thiếu header này. Chưa thể nối startAttempt W6 đúng contract. | Khoa + Huy: route Assessment/Progress, header và integration test. [#21](https://github.com/KhoaTryHard/english-learning-platform/issues/21) |
| A-09 | Project Info còn MySQL; code Khoa dùng PostgreSQL 17. W3-03 là logical model, chưa ghi quyết định engine từng service như W4 yêu cầu. Không được suy PostgreSQL toàn bộ. | Khoa/Huy ghi decision User/Content và Assessment/Progress, driver/migration/CI impact. #2/#13/#20 |
| A-10 | Hưng FE E=101,7h chưa kể PM/QA. Nếu chỉ giả sử 16h/tuần như kế hoạch W5 thì 4 tuần development chỉ 64h. Capacity W6 thực chưa được ghi. | Hưng: đo remaining từ code mới; ưu tiên W5 carryover + FE-03/04, không hứa toàn bộ Admin cùng tuần. #7/#19 |
| A-11 | Technology Stack ghi Tailwind, scaffold chưa có dependency/style system. Code W6 hiện dùng CSS tokens thuần theo Figma, cùng React/TS/Axios. | Ghi stack thực trong README; đồng bộ Project Info sau review lựa chọn kỹ thuật, không đổi nghiệp vụ. #19 |
| A-12 | Figma có correct-count/explanation/time/module-progress và vài nút Admin chưa có contract. Những hình đó không phê duyệt thêm field/endpoint/MVP scope. | Frontend chỉ hiện dữ liệu API có; phần còn lại nêu giới hạn, không fake. #9/#10/#11 |

### Schedule W4: ngày giờ Việt Nam

| Task | Trello t5d due | W4-00/01 schedule |
|---|---|---|
| W4-00 | 15/10 23:30 | 16/10 17:00 |
| W4-01 | 16/10 19:00 | 17/10 19:00 |
| W4-02 | 17/10 17:00 | 18/10 17:00 |
| W4-03 | 16/10 17:00 | 17/10 17:00 |
| W4-04 | 17/10 19:00 | 18/10 19:00 |
| W4-05 | 17/10 21:00 | 18/10 21:00 |

Không tự chọn một cột làm actual hoặc backdate hồ sơ. W5 deadlines trong 5 thẻ còn thấy khớp lịch 23–25/10; quá hạn không có nghĩa code Done.

### Hình Architecture/ERD

W3-02 hiện mô tả đúng Gateway/4 service, DB-per-service, auth/revoke, import atomic/readiness, Result+Outbox/Kafka/idempotency/DLT/replay và statistics qua owner APIs. Đây là sơ đồ logical; hình auth rút gọn đường cookie/logout, phải đọc kèm chữ “via Gateway” và contract.

W3-03 ảnh ERD đúng owner cấp bảng nhưng chưa thể thay physical ERD/DDL: Content figure bỏ LearningContent; các nhãn 1:* không diễn tả đầy đủ trạng thái rỗng/0..* và TRUE_FALSE không có options. Cần bổ sung entity/nullable/cardinality vào cùng action #20, không diễn giải sơ đồ này thành constraint mới.

## GitHub: đủ những gì, thiếu những gì

Đã có main/develop, feature branch, commit convention, 16 issue phân công W5/backlog, templates issue/PR trong #18, CI backend trong #1 và traceability contract. Backend CI [run 38441117128](https://github.com/KhoaTryHard/english-learning-platform/actions/runs/38441117128) được đọc lại: success, SHA 56450fc; đó là **evidence kế thừa**, không phải test W6 mới.

Thiếu submitted peer review/merge, frontend runtime trước phiên này, milestone tuần, assignee Huy, actual W5 và runtime Assessment/Progress/Kafka. Repository rulesets trả danh sách rỗng; branch protection endpoint trả 404 với tài khoản không có admin, **chưa đủ kết luận có hay không có protection**. Owner repo nên xác minh require PR/review/checks, không tự thay settings trong phiên này.

Đã giữ nguyên #5 để bù W5; chọn #8/#9 cho W6; thêm #19 audit, #20 dictionary, #21 Gateway. Tạo [milestone W6](https://github.com/KhoaTryHard/english-learning-platform/milestone/1) hạn theo ngày 01/11. Hạn/giờ review chi tiết là đề xuất trong [plan](../planning/w6-plan.md), không phải xác nhận capacity thay Khoa/Huy. Không tạo lại các issue đã có hoặc đóng chúng chỉ vì có code chưa review.

## Việc nên làm tiếp cho môn học

1. Dùng một bảng evidence có Task → FR/PB/UC/AC → PR/SHA → test/env/result → reviewer. Bỏ thao tác chép trạng thái Done thủ công giữa ba công cụ.
2. W5 review bổ sung actual/remaining và blocker thật; họp ngắn để chốt DB, reviewer PR #1/#18, quyền Huy và đầu vào Assessment/Gateway. Ghi ngày họp thực, không tạo biên bản/chữ ký giả.
3. Cập nhật top-level baseline/source register thay vì viết lại W1/PROPOSED lịch sử. Giữ decision/change log cho mọi thay DD/API; bug fix đúng contract không mở rộng MVP.
4. Chỉ lấy Admin vào W7 khi first slice + Assessment sẵn; giữ W8 cho tích hợp/Progress/hoàn thiện, W9 regression/security/event/import và W10 demo/deployment theo roadmap.
5. Nếu giảng viên có rubric/đề cương, thêm mapping deliverables và đóng góp từng người. Không tìm thấy rubric trong folder đã gửi, nên báo cáo này không cam kết đủ mọi tiêu chí chấm môn.

Xem [kế hoạch W6](../planning/w6-plan.md) và [evidence frontend](../evidence/w6-frontend.md) để phân biệt code mới, test mock, API thật và phần chưa nghiệm thu.
