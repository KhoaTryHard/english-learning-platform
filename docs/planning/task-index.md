# Task index — 20/10/2026

Nguồn: W4-03 và Trello t4-c live; W3-05 UI v0.6 mới cập nhật. 6 gói W5 + 10 gói remaining Backlog; W5 không tương đương hoàn thành toàn PB.

| Issue | Code | Responsible | Giai đoạn |
|---|---|---|---|
| [#2](https://github.com/KhoaTryHard/english-learning-platform/issues/2) | W5-00 | Hưng | Kế hoạch W5 |
| [#3](https://github.com/KhoaTryHard/english-learning-platform/issues/3) | W5-01 | Khoa | Kế hoạch W5 |
| [#4](https://github.com/KhoaTryHard/english-learning-platform/issues/4) | W5-02 | Huy | Kế hoạch W5 |
| [#5](https://github.com/KhoaTryHard/english-learning-platform/issues/5) | W5-03 | Hưng | Kế hoạch W5 |
| [#6](https://github.com/KhoaTryHard/english-learning-platform/issues/6) | W5-04 | Hưng | Kế hoạch W5 |
| [#7](https://github.com/KhoaTryHard/english-learning-platform/issues/7) | W5-05 | Hưng | Kế hoạch W5 |
| [#8](https://github.com/KhoaTryHard/english-learning-platform/issues/8) | FE-03 | Hưng | Backlog W6+, chưa cam kết lịch |
| [#9](https://github.com/KhoaTryHard/english-learning-platform/issues/9) | FE-04 | Hưng | Backlog W6+, chưa cam kết lịch |
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
- BE-UT/BE-APT là migration/test/CI/docs trong AC từng owner task, không chuyển cho PM.
- Huy chưa xác minh username GitHub; Responsible vẫn Nguyễn Quang Huy, Assignee để trống.

## Dependency
W5-00 #2 theo dõi readiness, không chặn việc độc lập. W5-01 #3 / W5-02 #4 / W5-03 #5 triển khai theo contract; phần cần APIs/DB/broker chưa sẵn ghi BLOCKED. W5-04 #6 tích hợp, W5-05 #7 tổng hợp actual.
Content cần Assessment readiness; Result → Outbox/Kafka → Progress; frontend gọi owner APIs qua Gateway.

## Lấy việc
Mọi thành viên đề xuất; Hưng tổng hợp/ưu tiên và cùng owner kiểm tra AC/estimate/capacity/dependency. Chọn vào kế hoạch rồi Responsible bắt đầu Doing. Không tự đặt deadline Backlog, thay service owner hoặc đóng PB chỉ vì lát W5 xong. Trello status không tự đồng bộ GitHub.
