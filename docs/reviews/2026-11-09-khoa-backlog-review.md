# Khoa — thực hiện review và bàn giao backlog ngày 09/11/2026

Đã hoàn tất các phần Khoa có thể chủ động trong kế hoạch: chuẩn bị ma trận #31,
review PR22→46 và giao tiếp PR43/44, chạy LIVE và fault Import, rà checklist tài
liệu và chuẩn bị đồng bộ GitHub/Trello/Drive. **Kết luận review: yêu cầu sửa #49
và đưa fix #44 đã review vào phiên bản Assessment.**

- [Báo cáo review chi tiết](../../.forgewright/code-reviewer/review-report.md).
- [Ma trận consumer #31 và gói Huy cần cung cấp](../handoffs/khoa-consumer-acceptance-2026-11-09.md).
- [Manifest kết quả mới](../evidence/khoa-backlog-review-2026-11-09/manifest.json).

## Phần đã có kết luận

Khoa nghiệm thu backend Import failure/rollback tại #33: 22 HTTP/DB checks mới
PASS, gồm lỗi ở INSERT thứ hai thật và rollback dòng đầu; khôi phục import thành
công; DB outage503 và phục hồi giữ dữ liệu. Các luồng browser LIVE mới có2 PASS
trên PR22 và6 PASS trên PR46; normal Outbox→Kafka→Progress/retake đã chạy, không
thay broker fault/replay/crash recovery hoặc Statistics acceptance.

Phát hiện [#49](https://github.com/KhoaTryHard/english-learning-platform/issues/49)
gán Hưng: UI báo đã lưu sau save503/GET200 dù server chưa lưu đáp án. Test bổ sung
FAIL ở cả PR22/46; PR47 kế thừa cùng blob. Source frontend không bị sửa trong lượt
review. Hưng sửa trên nhánh owner, Khoa retest và cập nhật review trên SHA mới.

PR43 head c1a2a074 chưa có DB503 fix; runtime LIVE dùng413a929 ở PR44. Khoa là tác
giả44, nên evidence/self-verification không thay review của Huy. Giữ các PR Draft
và dependency/merge gates cho đến khi đủ điều kiện.

## Các gate giữ mở

| Gate | Owner / điều kiện gỡ |
|---|---|
| #31 Open5/7 | Huy: consumer #35/#36 và cross-review thực tế; Khoa xác nhận tương thích |
| #49 và review PR22/46 | Hưng: patch dirty/ACK + regression; Khoa retest/re-review |
| PR44 review, PR43 baseline | Huy: review fix44, tích hợp source sửa rồi test phần ảnh hưởng |
| #26/#33/#40 | Hưng điều phối: review/merge, W8UI/Statistics/accessibility, actual/capacity và acceptance nhóm |
| SRS0.4/UC-06 review bổ sung | Huy: phản hồi thật cho checklist W2-02/W2-03; không suy waiver14 thành review tài liệu |
| API11 | Hưng sửa #49; Huy xác nhận server concurrency/retention theo phạm vi còn thiếu |

W6 baseline internal0.1.1 đã được ghi theo phản hồi thực Huy/Hưng qua Khoa; #42,
#21,#23 đã Closed. PR1/45/48 đã merge; #13/#14/#3/#41 đã Closed theo các quyết định
đã lưu. Tài liệu/checkpoint cũ ghi Draft/Open/pending auth được giữ dưới dạng
lịch sử, không dùng làm trạng thái hiện tại. #14 review WAIVED_BY_KHOA chỉ áp dụng
cho #14. W5-01/W6-02/W7-05 giữ Done theo phạm vi đã nghiệm thu.

Trello W8-05 cần hiển thị lại ở Review/Test cho bàn giao/QA; W8-06 tiếp tục Doing.
PB vẫn là nhóm tính năng; ghi mức ưu tiên phần Khoa và dependency, không tự Done
cả PB vì backend PASS. Giữ public v0.1, internal0.1.1 và V1; production HOLD W10.
