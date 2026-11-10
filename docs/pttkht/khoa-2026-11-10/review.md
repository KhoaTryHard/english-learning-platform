# Biên bản thực hiện phần việc Khoa

Ngày actual: 10/11/2026 (Việt Nam). Khoa thực hiện với hỗ trợ Codex theo kế hoạch
KO1–KO5; không ghi 30 giờ ước lượng thành giờ đã làm.

## Kết quả có thể review

1. Đã biên soạn Phần1/9/10/11/13 và ghép nội dung có căn cứ vào đủ 13 phần.
2. Đã lập responsibility.csv cho 13 phần, 5 thành viên; Khoa accountable.
3. Đã lập uc-crosswalk.csv: 13 alias cũ sang 11 UC baseline và 3 nhóm thiếu.
4. Đã đối chiếu User/Content/Gateway, dictionary, API và candidate Huy/Frontend.
5. Đã sửa ghi chú lỗi thời tại docs/operations.md: internal0.1.1 đã baselined,
   không tiếp tục gọi proposal-only; dẫn runbook TEST hiện hành.
6. Đã lập sơ đồ nguồn Graphviz cho ranh giới kiến trúc và event để sửa được.
7. Đã chạy lại static/contract và frontend; tái hiện #49 trên head PR47 hiện tại.

## Phiên bản được kiểm

| Phạm vi | SHA |
| --- | --- |
| User Content Gateway | af92253a95cf39fbdbfcbada2cfdd47fd413534d |
| Hưng W8 PR47 | ec373bdca31d10c9b1fe29e99b8137b776427d05 |
| Huy candidate có fix44 | 413a9297d4d63f61b0ad4b81a0c1f1e7a9370abd |
| Evidence/handoff trước đó PR50 | bb8d64317b6625e9b168d7136c270eae40b27299 |

## Review tích hợp

**#49 chưa có patch để nghiệm thu.** AttemptPage.tsx ở PR22/46/47 cùng blob
7dd9f0d46c6152bae9b49b4cf198047dbc232eaf. Regression tái sử dụng từ PR50 chạy
ngày10/11 trên PR47: FAIL1/1, UI báo đã tải/lưu sau GET thành công dù PUT503 và
server chưa lưu. Suite nền55 test vẫn PASS; không biến happy path thành fix.
Hưng cần patch dirty/ACK/error và kiểm bấm resync/visibilitychange/retry/terminal.

**Statistics chưa có implementation trong candidate Progress đã đọc.** Chỉ có
controller /me/progress, /me/history, /me/dashboard; contract/Gateway có route
/admin/statistics không đồng nghĩa owner đã triển khai. Huy xử lý #36; source
User/Content đã sẵn. Không cần chờ #31 Closed để tiếp tục consumer.

**#31 giữ OPEN.** Chờ consumer acceptance #35/#36 và review thực tế của Huy.
Giữ ma trận expected/actual tại [handoff09/11](https://github.com/KhoaTryHard/english-learning-platform/blob/bb8d64317b6625e9b168d7136c270eae40b27299/docs/handoffs/khoa-consumer-acceptance-2026-11-09.md)
trong PR50; tham chiếu branch PR50 nếu tệp chưa có trên develop. Không tự đóng issue.

**PR gates đọc được ngày10/11.** #22/#46/#43 có CHANGES_REQUESTED của Khoa ngày09/11
tại đúng head hiện tại. #44 chưa có review; #47 chưa có review. Các PR đều Draft.
Thứ tự xử lý: Huy review44 → khép43; Hưng sửa49 ở nền22 → khép22 → retarget46
→ khép46 → retarget47 và kiểm lại phần chịu ảnh hưởng. Không merge Draft.

**Các khác biệt tài liệu cần chủ trì xác nhận.** UC08 local có denominator
published-only; UC12 có tạo Vocabulary thủ công; UC11/12 mô tả preview như luồng
riêng; local còn thiếu coverage rõ cho Profile, Course/Level và Statistics.
Crosswalk ghi đề xuất chỉnh theo baseline hiện có, không đổi public API.

## Trạng thái theo kế hoạch

| Gói | Trạng thái | Còn cần |
| --- | --- | --- |
| KO1 | READY FOR REVIEW | Khang review Phần1; Khang/Tùng xác nhận crosswalk và khảo sát |
| KO2 | READY FOR REVIEW | Huy review Phần9/10; các kiểm vận hành LIVE đúng môi trường |
| KO3 | PARTIAL WAITING HANDOFF | Patch49 của Hưng, Statistics/consumer của Huy, physical mapping review |
| KO4 | PARTIAL BLOCKED RUNTIME | Docker cho fault/restore mới; peer review PR44/43/22/46/47 |
| KO5 | DRAFT ASSEMBLED WAITING OWNERS | Bản cuối/review4thành viên, tập báo cáo, đóng góp và nghiệm thu thật |

Không đánh dấu gói Done khi gate của kế hoạch chưa đạt. Phần tài liệu hoàn thành
được chuẩn bị sớm; các cuộc họp/nghiệm thu không được mô phỏng thành đã diễn ra.

## Nguồn và cách tái hiện

- [SRS](https://docs.google.com/document/d/1GcHdmljWlC5P4o7V3UMh9OkKSThlYrPaxWFWdIlI8w0/edit)
- [Use Case AC](https://docs.google.com/document/d/14OKvB16aG1HgfH-aSe5fsp_3xm2jVDqmiNl482YWALs/edit)
- [Design Decisions](https://docs.google.com/document/d/1OepZRaVWvEQJOIQcPRPsOq6Ld_4LK5FA5rmnUD2Vv3A/edit)
- [API](https://docs.google.com/document/d/1VQKbmWOJae3Kj3S92ea08pUb-bswQNyDtuBGH_QTPBc/edit)
- [Runbook TEST](../../runbooks/khoa-w9.md)
- [Evidence mới](../../evidence/khoa-pttkht-2026-11-10/)

Unit reproduction được dùng tạm tại frontend/src/features/assessment; sau khi
chạy đã xóa đúng tệp tạm do lượt review tạo. Worktree của Hưng sạch; không sửa
application hay force-push nhánh của thành viên khác.

## Kết quả backend mới

Focused verify với JDK25: 49 lượt JUnit PASS, 0 failure/error/skip; ba JAR package thành công. User13, Content21, Gateway15. Shared core lặp theo module. Không chạy Testcontainers PostgreSQL, Docker/LIVE/restore. Các lượt lỗi cấu hình trước đó giữ cùng evidence; không ghi lượt lỗi thành PASS.
