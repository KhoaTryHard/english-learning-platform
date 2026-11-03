# Checkpoint W6 của Hưng — 27/10/2026 (UTC+7)

Hôm nay vẫn trong W6 **26/10–01/11/2026**. Phần frontend đã triển khai và có kiểm thử; chưa đủ điều kiện tuyên bố cả tuần đã nghiệm thu. Báo cáo này rà phần Hưng (PM + FE), không thực hiện thay User/Content/Gateway của Khoa hoặc Assessment/Progress/Kafka của Huy.

Nhánh tuần hiện tại: `feature/w6-hung-learner-frontend`, PR #22. Base trước checkpoint `bb81db9`; chỉ thêm test Lesson và báo cáo/checklist, không đổi code nghiệp vụ production. W5 carryover có ngày thực, không ghi lùi tiến độ. Chiến lược nhánh của Hưng: một nhánh/một PR cho phần việc từng tuần, liên kết nhiều issue; W7/W8 chỉ tạo khi bắt đầu làm.

## Kiểm thử vừa chạy

| Kiểm tra | Kết quả | Phạm vi |
|---|---|---|
| LessonPage.test.tsx, Vitest threads maxWorkers=1 | 8 PASS | React UI + API client + MSW fault responses |
| npm run check | PASS: 22 unit/UI tests trong 6 file, lint, TypeScript/Vite production build, contract/production guard | Frontend; không chạy backend/Kafka |
| Prettier file test, git diff --check | PASS | Format/whitespace phần thay đổi |

[Kết quả có cấu trúc](w6/hung-checkpoint-results.json) ghi base SHA và SHA-256 của file test. Các 6 browser MOCK và 2 browser LIVE trong [evidence trước](w6-frontend.md) là kết quả đã chạy trước trong ngày, **không rerun ở checkpoint này**. LIVE trước chỉ User/Content/Gateway/PostgreSQL17, readiness Assessment là WireMock.

Tám case bổ sung:

1. Owner API trả 403: không lộ nội dung Lesson hay nút lưu/làm bài.
2. Owner API trả 404: hiển thị unavailable, không tiếp tục đưa nội dung học ra UI. Đây là kiểm thử client nhận lỗi, **chưa chứng minh backend đã lọc cha unpublished**.
3. Vocabulary/content rỗng hiện trạng thái thiếu; chỉ xem Lesson không tạo attempt/result/completion trong simulator.
4. Content trả 503 có trace: hiện lỗi, chỉ retry khi Learner yêu cầu, không tự biến lỗi thành dữ liệu rỗng.
5. Listening UNAVAILABLE không render audio source dù response còn URL.
6. Audio phát lỗi: hiển thị resource error rõ, không để control hỏng như đang hoạt động.
7. Save vocabulary lỗi: không đổi UI thành đã lưu, không tự retry mutation.
8. Remove lỗi: giữ quan hệ đã lưu/UI để Learner có thể thử lại.

Đã tự đối chiếu source AuthProvider/client/Profile/AssessmentPage/AnswerQueue: cookie/CSRF rotation, Profile fullName whitelist, không lưu token/password/answers vào localStorage; sessionStorage của start chỉ giữ transport key; serialize full answer-set, giữ false và chờ ACK trước submit. Đây là self-review frontend cùng tests, không phải peer review hoặc security acceptance của backend.

## Trạng thái issue của Hưng

| Issue | Đã làm và đánh dấu | Còn mở |
|---|---|---|
| #5 W5 carryover trong W6 | FE-01/02/first slice, evidence và self-review | Thành viên khác review, sửa feedback nếu có, merge theo thứ tự PR |
| #24 Lesson W6 | Code, states, 8 negative UI tests và bàn giao evidence | Peer review; backend visibility/readiness thật còn gate owner |
| #9 checkpoint FE-04 W6 | Code/MOCK/unit tests và self-review | Assessment/Gateway thật; toàn FE-04 theo mốc W7 |
| #26 QA/PM W6 | Rà PR/dependency, ghi evidence/remaining, self-review FE, giữ BLOCKED đúng owner | Smoke Exercise thật, peer review/merge, capacity và nghiệm thu nhóm |
| #8 parent FE-03 | Ghi thêm negative test coverage client | Dashboard/Progress thật W8, các backend/error paths còn thiếu và peer acceptance |
| #2/#19 | Báo cáo quản lý đã hoàn tất, giữ CLOSED | Không dùng việc đóng báo cáo để đóng task backend |

GitHub là nguồn checklist mới nhất. Manifest W5–W8 giữ snapshot lúc lập kế hoạch; không suy số checkbox thành phần trăm effort hoặc capacity đã xác nhận.

## Gate còn thiếu để khép W6

Đọc lại GitHub ở checkpoint: PR #1 và #22 OPEN/DRAFT, #18 OPEN; cả ba chưa có review submissions và chưa merge. Chỉ Hưng tự review không thay xác nhận của thành viên khác. Theo CONTRIBUTING, PR cần peer review trước merge.

- Khoa: #13 migration, #21 owner routes/Idempotency-Key, #42 internal auth/compatibility; #23 quyền Huy1188 còn mở.
- Huy: #20 technical schema/ERD/engine review và #25 Assessment Exercise thật chưa có evidence triển khai mới.
- Hưng: dùng #26 phối hợp smoke Auth→Lesson→Exercise→Result thật khi các gate sẵn; nếu chưa sẵn giữ BLOCKED. Capacity Khoa/Huy và kết luận nghiệm thu nhóm vẫn chưa xác nhận.

Kết luận tại 27/10: **frontend W6 đã có code và self-QA để review; W6 chưa được nghiệm thu hoàn tất**. Không tự đóng #5/#24/#26, ghi peer acceptance hoặc merge PR thay nhóm chỉ từ MOCK PASS. Không sửa Trello và không bắt đầu code W7 trong checkpoint này.
