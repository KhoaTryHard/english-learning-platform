# Quy trình làm việc
- main: ổn định/demo; develop: tích hợp; feature/<issue>-<slug>, fix/<issue>-<slug>.
- Mỗi PR trỏ issue, task code FE/BE/PB và nguồn Trello; nêu actual test, mock và blocker.
- Mở PR vào develop, thay đổi vừa phạm vi; không tự merge PR Draft hoặc coi CI partial là full acceptance.
- Task mới: mọi người đề xuất; Hưng tổng hợp, kiểm tra trùng và ưu tiên. Chọn Backlog → To Do khi đủ owner/AC/estimate/capacity/dependency. Owner kéo Doing khi bắt đầu. Việc độc lập UI/mock/skeleton có thể làm trước dependency runtime; phần bị chặn ghi rõ.
- Done: AC đạt và evidence phù hợp, không do hết hạn. W5 chỉ first slice; remaining nằm Backlog W6+ chưa cam kết deadline.
- Không commit secrets/.env thật. Dùng .env.example không có password thật.
- Desktop/mobile dùng chung API/state. Service chỉ đọc/ghi DB của mình. Không sửa approved contract hoặc thêm MVP scope chỉ theo nút Figma.
- Source of truth: Trello cho kế hoạch tuần; GitHub cho implementation/PR/bugs; Drive cho baseline. Không tự đồng bộ trạng thái Done giữa công cụ.
