# Frontend — Hưng

React + TypeScript + Vite, Axios, CSS tokens theo Figma Learning Studio v2. Một ứng dụng responsive dùng chung state/API trên desktop và mobile. Dùng Node 24 LTS và lockfile.

## Chạy ngay

```powershell
cd frontend
npm ci
npm run demo
```

Mở URL Vite in ra (mặc định `http://127.0.0.1:5173`). Tài khoản mẫu: `hung.demo@example.com` / `LearningDemo2026!`; có thể tự đăng ký trong demo. Banner **MOCK / DEMO** luôn hiện. MSW giữ dữ liệu mẫu trong sessionStorage của tab để thử reload/resume. Đây không phải bằng chứng backend/DB/Kafka chạy.

## Gateway thật

```powershell
$env:GATEWAY_URL = 'http://127.0.0.1:8080'
npm run dev
```

Trình duyệt gọi `/api/v1`; proxy giữ nguyên Origin/cookie. Gateway phải đặt `FRONTEND_ORIGIN=http://127.0.0.1:5173`, `SECURE_COOKIES=false` cho HTTP local. Không trộn localhost và 127.0.0.1. Production dùng HTTPS/secure cookies, reverse proxy `/api` tới Gateway và SPA fallback về index.html. `npm run build` tạo dist, không tự publish.

Live mode không tự rơi về mock. JWT chỉ ở HttpOnly cookie; frontend không đọc/lưu JWT. CSRF lấy trước mutation, đổi sau login/logout; không tự retry write. 401 quay về Login và giữ URL để tiếp tục; 403/404/503/network có trạng thái riêng. Logout chỉ kết thúc sau phản hồi backend thành công.

## Đã có code

- FE-01/02: Register/Login/Logout/Profile/protected routes; không implicit login sau register, Profile chỉ sửa fullName/null, không Goal khi chọn Course/Level.
- FE-03: Course/Module/Lesson, Vocabulary/Grammar/Listening/Reading, save/remove từ và resource unavailable.
- FE-04: Exercise/Quiz/Test, start/resume Attempt, SINGLE_CHOICE/TRUE_FALSE, serialized full answer-set saves, ACK trước submit, server timer/timeout, Result/retake.
- Trang đọc Progress/History/My Vocabulary và responsive cơ bản để điều hướng; không đóng toàn #10/#12 hoặc nghiệm thu backend từ giao diện.

Admin A-01→A-07 (#11) giữ backlog W7+. API v0.1 chưa trả correct-answer/explanation/số câu đúng-sai/thời gian làm bài hoặc tỷ lệ theo Module: không bịa các dữ liệu minh họa đó trên Figma. Frontend không tự chấm điểm live hoặc tăng progress khi GET Lesson.

## Kiểm tra

```powershell
npm run check
npm run types:api
npx playwright install chromium
npm run test:e2e
```

`check`: lint, 14 unit/UI tests, typecheck/build, kiểm tra hash approved contract và loại demo khỏi production. Browser suite mặc định là **MOCK**, desktop/mobile. GitHub Actions lưu screenshot/report/trace trong artifact frontend-mock-browser-evidence.

Suite thật yêu cầu provision User/Content/Gateway, DB và nội dung công bố trước:

```powershell
$env:RUN_LIVE_INTEGRATION = '1'
$env:LIVE_COURSE_NAME = 'Demo English'
$env:LIVE_LESSON_TITLE = 'W6 — Greetings'
npx playwright test --config playwright.live.config.ts
```

Suite tạo Learner thử, sửa Profile, chọn Course/Level, học Lesson, save/remove từ và logout. Nếu Assessment readiness là mock, ghi riêng; suite này không kiểm thử Assessment/Kafka/Progress. Chỉ chạy vào môi trường test.

## Hợp đồng và giới hạn

Types sinh từ [approved public v0.1](../contracts/http/public-v0.1.yaml), không sửa generated types bằng tay. `src/mocks` chỉ import trong DEV demo, bị loại khỏi production; production không chứa demo credentials/fixtures/worker.

Start dùng Idempotency-Key bền qua retry trong tab, scoped user+assessment, xóa sau ACK; retake key mới. Answers chỉ khôi phục từ server, không lưu localStorage. Full replacement saves được serialize trong một editor; boolean false được giữ đúng. Save200 trả terminal Attempt phải chuyển Result. Ghi đồng thời từ nhiều tab vẫn thuộc API-11; retention key do backend quyết định.

Assessment/Progress/Gateway readiness theo #4/#15–#17/#21. Không sửa backend owner hoặc approved contract trong PR frontend. Xem [evidence](../docs/evidence/w6-frontend.md), [audit](../docs/reviews/2026-10-27-project-audit.md), [kế hoạch W6](../docs/planning/w6-plan.md).

src/app: bootstrap/router/layouts; src/features: nghiệp vụ; src/shared: API/UI/hooks/styles/assets; feature tests đặt cạnh code, tests/e2e là browser mock, tests/live là browser gọi API thật.
