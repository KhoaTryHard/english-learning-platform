# Quy trình làm việc

Từ 27/10/2026, dự án không dùng CI/CD theo quyết định của Hưng. Kiểm thử chạy thủ công và lưu bằng chứng trong PR. Hai workflow GitHub Actions hiện có đã được tắt; các lần chạy cũ chỉ là lịch sử.

PR cần ít nhất một thành viên khác review trước merge. Với PR xếp chồng, merge PR nền trước, đổi base PR con về develop rồi kiểm tra diff và chạy lại các kiểm thử bị ảnh hưởng. Không force-push nhánh của thành viên khác.

Frontend dùng Node 24, `npm ci`, `npm run check` và `npm run test:e2e` trong frontend. Evidence ghi rõ MOCK/API thật/SHA/BLOCKED; không backdate commit/test hoặc biến estimate thành giờ actual.

- main: ổn định/demo; develop: tích hợp; feature/<issue>-<slug>, fix/<issue>-<slug>.
- Phần Hưng theo yêu cầu PM dùng một nhánh/PR mỗi tuần, liên kết các issue trong tuần: W6 giữ `feature/w6-hung-learner-frontend`; khi bắt đầu W7/W8 dùng `feature/hung-w7`/`feature/hung-w8`. Nhánh mới lấy từ develop đã tích hợp, hoặc nối nhánh tuần trước với base/dependency PR ghi rõ nếu nền chưa merge. Không tạo lại nhánh W5 hoặc backdate phần bù.
- Mỗi PR trỏ issue, task code FE/BE/PB và nguồn Trello; nêu actual test, mock và blocker.
- Mở PR vào develop, thay đổi vừa phạm vi; không tự merge PR Draft. Ghi lệnh kiểm thử thủ công, kết quả, SHA, môi trường và phần chưa chạy; test pass vẫn cần review.
- Task mới: mọi người đề xuất; Hưng tổng hợp, kiểm tra trùng và ưu tiên. Chọn Backlog → To Do khi đủ owner/AC/estimate/capacity/dependency. Owner kéo Doing khi bắt đầu. Việc độc lập UI/mock/skeleton có thể làm trước dependency runtime; phần bị chặn ghi rõ.
- Done: AC đạt và evidence phù hợp, không do hết hạn. W5 chỉ first slice; remaining đã phân theo [kế hoạch W5–W8](docs/planning/w5-w8-plan.md). Ngày/effort là mục tiêu PM, chưa là actual/capacity đã xác nhận. Parent tracking không cộng effort trùng task con; W5 quá hạn giữ ngày gốc và liên kết phần bù.
- Không commit secrets/.env thật. Dùng .env.example không có password thật.
- Desktop/mobile dùng chung API/state. Service chỉ đọc/ghi DB của mình. Không sửa approved contract hoặc thêm MVP scope chỉ theo nút Figma.
- Source of truth: Trello cho kế hoạch tuần; GitHub cho implementation/PR/bugs; Drive cho baseline. Không tự đồng bộ trạng thái Done giữa công cụ.
