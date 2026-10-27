# Task index — cập nhật 27/10/2026

W6 **26/10–01/11** theo [plan](w6-plan.md), [milestone](https://github.com/KhoaTryHard/english-learning-platform/milestone/1). Board hiện hành: [t5d](https://trello.com/b/2sSouPca/t5d), chỉ đọc. Snapshot bên dưới ngày 20/10 là lịch sử tạo issue, không phải current status.

| Issue | Lát W6 / carryover | Owner | Trạng thái quản lý |
|---|---|---|---|
| #5 | FE-01/02 bù W5 | Hưng | Có code mới/PR, chờ peer review; không đóng |
| #8 | W6-01 FE-03 Lesson/Dashboard | Hưng | Chọn W6; Progress aggregate phụ thuộc Huy |
| #9 | W6-02 FE-04 Attempt/Result | Hưng | Chọn W6; integration Assessment/Gateway còn owner gate |
| [#19](https://github.com/KhoaTryHard/english-learning-platform/issues/19) | W6-00 Audit/actual/W6 plan | Hưng | Có báo cáo trong repo, chưa thay ký nhận nhóm |
| [#20](https://github.com/KhoaTryHard/english-learning-platform/issues/20) | W6-03 Schema/dictionary | Huy | Responsible trong body; assignability Huy1188 vẫn 404 |
| [#21](https://github.com/KhoaTryHard/english-learning-platform/issues/21) | W6-04 Gateway/header | Khoa | Có assignee; phối hợp Huy cung cấp APIs |
| #6/#7 | Test evidence và review/handoff W5 carryover | Hưng điều phối | Bổ sung actual, không backdate |

# Snapshot tạo issue — 20/10/2026

Nguồn: W4-03 và Trello t4-c live; W3-05 UI v0.6 mới cập nhật. 6 gói W5 + 10 gói remaining Backlog; W5 không tương đương hoàn thành toàn PB.

| Issue | Code | Responsible | Giai đoạn |
|---|---|---|---|
| [#2](https://github.com/KhoaTryHard/english-learning-platform/issues/2) | W5-00 | Hưng | Kế hoạch W5 |
| [#3](https://github.com/KhoaTryHard/english-learning-platform/issues/3) | W5-01 | Khoa | Kế hoạch W5 |
| [#4](https://github.com/KhoaTryHard/english-learning-platform/issues/4) | W5-02 | Huy | Kế hoạch W5 |
| [#5](https://github.com/KhoaTryHard/english-learning-platform/issues/5) | W5-03 | Hưng | Kế hoạch W5 |
| [#6](https://github.com/KhoaTryHard/english-learning-platform/issues/6) | W5-04 | Hưng | Kế hoạch W5 |
| [#7](https://github.com/KhoaTryHard/english-learning-platform/issues/7) | W5-05 | Hưng | Kế hoạch W5 |
| [#8](https://github.com/KhoaTryHard/english-learning-platform/issues/8) | FE-03 | Hưng | Đã chọn W6 ngày 27/10; xem plan |
| [#9](https://github.com/KhoaTryHard/english-learning-platform/issues/9) | FE-04 | Hưng | Đã chọn W6 ngày 27/10; xem plan |
| [#10](https://github.com/KhoaTryHard/english-learning-platform/issues/10) | FE-05 | Hưng | Backlog W6+, chưa cam kết lịch |
| [#11](https://github.com/KhoaTryHard/english-learning-platform/issues/11) | FE-06 | Hưng | Backlog W6+, chưa cam kết lịch |
| [#12](https://github.com/KhoaTryHard/english-learning-platform/issues/12) | FE-07 | Hưng | Backlog W6+, chưa cam kết lịch |
| [#13](https://github.com/KhoaTryHard/english-learning-platform/issues/13) | BE-U2/C1/C2/G1 | Khoa | Backlog W6+, chưa cam kết lịch |
| [#14](https://github.com/KhoaTryHard/english-learning-platform/issues/14) | BE-C3/C4 | Khoa | Backlog W6+, chưa cam kết lịch |
| [#15](https://github.com/KhoaTryHard/english-learning-platform/issues/15) | BE-A1/A2/A3/A4 | Huy | Backlog W6+, chưa cam kết lịch |
| [#16](https://github.com/KhoaTryHard/english-learning-platform/issues/16) | BE-K1/K2/P1 | Huy | Backlog W6+, chưa cam kết lịch |
| [#17](https://github.com/KhoaTryHard/english-learning-platform/issues/17) | BE-P2/Architecture | Huy | Backlog W6+, chưa cam kết lịch |

## Coverage và phối hợp
- Hưng: FE-01/02 + phần đầu FE-03/07 ở W5-03; FE-03→07 hoàn thiện qua Backlog; PM điều phối W5-00/04/05.
- Khoa: BE-U1/U2/C1/C2/G1 ở W5-01 và gói remaining User/Content/Gateway; BE-C3/C4 ở My Vocabulary/Import. Audit PR #1 trước estimate để không làm trùng.
- Huy: BE-A1→A4/P1/P2/K1/K2 foundation W5-02; đầy đủ qua ba gói Assessment, Kafka/Progress, Progress/Stats/Architecture.
- BE-UT/BE-APT là migration/kiểm thử thủ công/tài liệu trong AC từng owner task, không chuyển cho PM. Từ 27/10/2026 bỏ CI/CD, giữ log và kết quả theo SHA trong PR.
- Huy đã xác nhận tài khoản @Huy1188 ngày 21/10/2026. Responsible vẫn Nguyễn Quang Huy. GitHub chưa ghi nhận Assignee khi gán #4/#15–#17; owner kiểm tra quyền cộng tác/lời mời, sau đó gán lại.

## Dependency
W5-00 #2 theo dõi readiness, không chặn việc độc lập. W5-01 #3 / W5-02 #4 / W5-03 #5 triển khai theo contract; phần cần APIs/DB/broker chưa sẵn ghi BLOCKED. W5-04 #6 tích hợp, W5-05 #7 tổng hợp actual.
Content cần Assessment readiness; Result → Outbox/Kafka → Progress; frontend gọi owner APIs qua Gateway.

## Lấy việc
Mọi thành viên đề xuất; Hưng tổng hợp/ưu tiên và cùng owner kiểm tra AC/estimate/capacity/dependency. Chọn vào kế hoạch rồi Responsible bắt đầu Doing. Không tự đặt deadline Backlog, thay service owner hoặc đóng PB chỉ vì lát W5 xong. Trello status không tự đồng bộ GitHub.
