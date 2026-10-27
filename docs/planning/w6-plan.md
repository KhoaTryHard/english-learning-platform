# W6 — 26/10–01/11/2026

Lê Tiến Hưng (PM/Frontend) lập replan ngày 27/10 theo yêu cầu bắt đầu code. W5 19–25/10 đã qua; không sửa hạn gốc hoặc backdate tiến độ. [Audit](../reviews/2026-10-27-project-audit.md), [Milestone GitHub](https://github.com/KhoaTryHard/english-learning-platform/milestone/1).

## Mục tiêu và phạm vi

Bù nền tảng FE W5 (#5), có Course/Lesson focus mode (#8) và luồng Assessment/Attempt/Result W6 (#9) theo API v0.1/Figma v0.6. Đưa code thực vào PR review được, chạy frontend độc lập và thử với owner APIs khi sẵn. Không yêu cầu Hưng viết lại backend của Khoa/Huy; không đặt full MVP Done trong W6.

Nhánh `feature/w6-hung-learner-frontend` kế thừa `chore/monorepo-structure-task-plan` (PR #18). PR frontend trỏ nhánh nền đó để chỉ review phần mới; review/merge #18 trước rồi retarget PR con về develop, kiểm tra diff và chạy thủ công các kiểm thử bị ảnh hưởng. Không merge #1 hoặc #18 thay nhóm.

Quyết định của Hưng ngày 27/10: bỏ CI/CD. Workflow Frontend và Verify owner services đã tắt; frontend workflow được gỡ khỏi nhánh này. Giữ kiểm thử thủ công và peer review; bằng chứng CI đã chạy chỉ giữ như lịch sử đúng SHA. Owner backend gỡ workflow cũ khi chuyển cây PR #1.

## Phân công, thứ tự và mốc đề xuất

| Ưu tiên | Issue / trace | Responsible | Đầu ra / điều kiện | Mốc review đề xuất (VN) |
|---|---|---|---|---|
| 1 | #19, #2/#7; WBS 1.8 | Hưng | Audit, source register, actual/remaining W5, W6 selection; không giả chữ ký | 28/10 21:00 |
| 1 | #5 FE-01/02; PB-01/02/03; UC-01/02; AC-01/02/03/05/06 | Hưng | React/Auth/Profile/Course+Level, cookie/CSRF/errors, build/QA; peer review | 29/10 21:00 |
| 2 | #8 FE-03; PB-04/08; UC-03/05; AC-07/08 | Hưng, Khoa API | Course→Module→Lesson + learning content; Dashboard aggregate thật phụ thuộc Progress | 30/10 21:00 |
| 2 | #9 FE-04; PB-06/07; UC-04; AC-11→14 | Hưng, Huy API | Start/resume/save/submit/timeout/Result/retake; mock có nhãn, real integration phụ thuộc #15/#21 | 01/11 16:00 |
| 1 | #20 + #4/#15; BE-APT | Huy; Khoa review | Dictionary/nullable/unique/cardinality; migration và tests khi có code | Triage 28/10, owner xác nhận estimate |
| 1 | #21 + #13; BE-G1 | Khoa; Huy endpoints | Gateway Assessment/Progress routes + Idempotency-Key/CORS + tests | Phối hợp 29/10, owner xác nhận estimate |
| 3 | #6 W5-04 carryover; SYS-T-01/02/04/05/10 | Hưng điều phối; Khoa/Huy phần mình | Report thật đúng SHA/env; không gộp mock thành backend E2E | 01/11 20:00 |
| 3 | #7 review/handoff | Hưng + nhóm | Actual/remaining/risks và ưu tiên W7 | 01/11 21:00 |

GitHub milestone lưu hạn **theo ngày** 01/11; các giờ trên là mốc làm việc đề xuất. Chưa có xác nhận lịch/capacity Khoa/Huy, không biến cột này thành cam kết đã ký.

## Remaining sau khi có code trong phiên này

Các con số 6h (#8), 8h (#9), 12h (#5 W5) là estimate trước khi code/kiểm kê; không cộng lại thành actual. Dự trù **remaining frontend/PM 15h** cho sinh viên tự review, hiểu code, sửa theo peer review và phối hợp môi trường: #5 3h, #8 2h, #9 4h, #6 3h, #19/#7 3h. Đây là scenario lập kế hoạch, cần Hưng điều chỉnh theo lịch thực; không tính thời gian chạy agent thành giờ làm của thành viên.

Không bao gồm effort backend của Khoa/Huy; hai owner ước lượng phần mình sau triage. Nếu backend chưa ready, giữ UI/tests độc lập ở Review/Test và ghi BLOCKED cho integration; không đẩy tất cả sang Doing hoặc mở thêm Admin để che blocker.

## Readiness và DoD

- READY độc lập: SRS/UC-AC 0.4, DD 0.2, public API 0.1, UI 0.6; FE types từ approved spec; script demo hiển thị MOCK. React không cần chờ migration Huy để bắt đầu UI.
- Integration READY: owner APIs chạy, Gateway route/header, cookie Origin thống nhất, DB/seed, Assessment/Progress broker/event tùy case. Ghi blocker+owner cho phần chưa đủ.
- Review/Test: có PR/commit, build/test và ảnh app thực; peer reviewer còn phải xác nhận. Không đóng #5/#8/#9/#10/#12 hoặc PB cha từ mock PASS.
- Done code theo nhóm: đủ AC, review, checks và integration evidence đúng scope; merge khi nhóm chấp nhận. Phần backend chưa có vẫn phải OPEN/BLOCKED.

## Chuyển W7+

Trang Progress/History/My Vocabulary đã có client/UI cơ bản để kiểm tra điều hướng, là foundation của #10; không phải nghiệm thu đầy đủ. #12 còn accessibility/coverage các trạng thái và màn chưa có; #11 Admin giữ backlog.

W7 **02–08/11**: ưu tiên khép first slice/Assessment integration, sau đó Admin/Import/My Vocabulary theo readiness. W8 **09–15/11**: Kafka/Progress, History/Statistics, regression và hoàn thiện. W9 **16–22/11**: integration/security/event/import acceptance. W10 **23–29/11**: deployment/demo/report/closing. Các ngày suy từ baseline W5, cần đối chiếu lịch học/đề cương; không tự tạo deadline từng gói W7+.

## Công việc đã thực hiện trong phiên

Đã cập nhật các issue sẵn có, tạo #19/#20/#21, lập audit và source inventory, thêm code frontend/contract types/tests. Phiên audit ban đầu chỉ đọc Drive; phiên đồng bộ tiếp theo sửa trực tiếp tài liệu theo yêu cầu Hưng. Trello/Figma chỉ đọc. Kết quả lệnh và phạm vi runtime ghi riêng trong [evidence](../evidence/w6-frontend.md).
