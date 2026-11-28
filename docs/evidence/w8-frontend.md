# Hưng — W8 frontend checkpoint, 09/11/2026

W8 diễn ra 09–15/11. Đây là code/self-test đầu tuần, chưa nghiệm thu cả tuần. Nhánh `feature/hung-w8` nối W7 `1e11c6d` vì PR #46 và PR nền #22 còn Draft/Open; một nhánh/PR phần Hưng mỗi tuần theo CONTRIBUTING. Không đổi backend, approved contract hoặc CI/CD.

Source checkpoint: `33b16ef7b44e9dcdc5027f0e3d47d864d6c571c3`. Evidence được commit sau source, không đổi code ứng dụng.

## Phạm vi đã triển khai

| Issue / trace | Kết quả tại checkpoint |
|---|---|
| #37 — FE-06, PB-11, UC-09, AC-17 | Admin list/filter/create/read/PATCH Assessment; POST/PUT Question. SINGLE_CHOICE 2–6 options, UUID ổn định và một đáp án đúng; TRUE_FALSE giữ `false`, điểm dương. Exercise không timer, Quiz/Test yêu cầu giây nguyên dương. Create draft, PATCH whitelist không đổi type/course/lesson. Readiness do server xác nhận; 409 giữ form và trạng thái danh sách. Không thêm DELETE, import Assessment hoặc giới hạn lượt làm. |
| #38 — FE-06, PB-12, UC-10, AC-18, DD-11 | Sáu nhóm Statistics; Course/Level query gửi owner, global total không tính lại theo filter. Phân biệt null với 0, generatedAt/eventual, error/retry; không giữ số cũ sau source failure. Course đổi xóa Level filter. |
| #39 — FE-03/05, PB-08, UC-05, AC-12/15 | Reuse Dashboard/Progress/History client nền, thêm refresh; History hiển thị Level snapshot và giữ retake/khóa cũ, tải hết trang. Progress dùng completed/total/percent từ server, không tự tăng hoặc suy completion từ điểm. |
| #12 — FE-07 | Browser Chromium desktop 1440×1024/mobile iPhone13 emulation; kiểm tra overflow, labels, keyboard submit, false answer và lỗi/retry ở màn mới; regression Learner/Admin cũ. Chưa là toàn bộ a11y/đa trình duyệt. |
| #40 — QA/PM | Có code, tests, ảnh và danh sách blocker cho W9. Full MVP LIVE, owner regression #41 và peer acceptance chưa hoàn tất. |

## Kiểm thử thực tế

Windows, Node 24.22.1, Chromium Playwright. Lệnh chạy thủ công trong `frontend`, không CI/CD:

```powershell
npm run check
npm run test:e2e
npx playwright test -c playwright.w8.config.ts
```

Môi trường phiên chạy dùng Node bundled trên PATH; đã gọi trực tiếp các entrypoint tương đương lint, Vitest, TypeScript build, Vite build và production guard. **55 unit/UI PASS**, lint/typecheck/build/approved-contract/production guard PASS. [Vitest JSON](w8/unit.json).

**8 browser MOCK regression PASS**: các luồng Auth/Setup/Lesson/Exercise/retake/Vocabulary, Quiz deadline/false sau reload/relogin, Admin User/Content/import/RBAC và responsive đã có. [Report](w8/browser-regression.json).

**2 browser MOCK contract PASS** (cùng case desktop/mobile): Admin Assessment đọc detail, thêm TRUE_FALSE=false bằng CSRF client, reload question list, keyboard Enter; Statistics filter, null/0, source503/retry và không overflow. Suite dùng Vite thường cổng4180 và Playwright route fixtures, không MSW hoặc backend. [Report](w8/browser-contract.json). Lỗi locator `getByLabel` exact với select đã sửa sang role/accessible name rồi chạy lại PASS; không khai lượt fail đầu là PASS.

Unit thêm kiểm create Exercise payload không ready/timer; POST→PUT giữ false/questionId; readiness409 và khóa điều hướng trong lúc ghi; SINGLE_CHOICE bắt chọn đáp án, 2–6 lựa chọn và UUID; filter Statistics không gọi Learner-only API; Progress null/0/503/retry và History paging/retake/snapshot khóa cũ.

Ảnh MOCK đã mở kiểm tra: [Assessment desktop](w8/assessment-desktop.png), [mobile](w8/assessment-mobile.png), [Statistics desktop](w8/statistics-desktop.png), [mobile](w8/statistics-mobile.png). Không dùng các ảnh này làm evidence LIVE.

## Phát hiện và sửa

- Admin không được gọi `/courses/{id}/levels`: ServiceGuard backend Khoa chỉ cho Learner ở public Content paths. Dùng `/admin/courses` cho chọn Course; Level optional nhập UUID, yêu cầu chọn Course trước, server kiểm belongs-to. Không thêm endpoint hoặc nới quyền. Hạn chế UX này cần owner bàn giao nếu muốn danh sách Level cho Admin sau này.
- Hai chat đã cùng thêm Assessment route; giữ một implementation `AssessmentsPage.tsx`, một route và một navigation item. Task Trello do chat khác xử lý; lượt này không tự đổi trạng thái card.
- Toolbar dạng form kế thừa CSS column làm desktop lệch: thêm class scoped, checkbox/radio reuse `admin-checkbox`, đã chụp lại desktop/mobile.
- Dashboard đang tải không hiện lịch sử rỗng giả; lỗi nguồn hiển thị error, refresh dữ liệu từ owner.

## LIVE / review / bàn giao W9

**LIVE mới: BLOCKED_ENVIRONMENT.** Docker CLI có trên máy nhưng engine không hoạt động. Đã thử khởi động Docker Desktop; daemon báo `Docker Desktop is unable to start`, sau đó `docker info` trả HTTP500. Không có lượt backend/Kafka/DB mới được chứng minh trong W8. Không thay bằng fixture hoặc số liệu demo.

Khi engine hoạt động: provision stack TEST theo runbook Khoa tại PR #45 `c3e5e2f`, Assessment/Progress PR #44 `413a929`, 4 PostgreSQL riêng, bật overlay Kafka theo [hướng dẫn W7](w7-frontend.md). Không dùng production/shared data; không xóa volumes. Chạy lại W7 LIVE trên bản FE mới, bổ sung browser W8 thật cho create/edit/readiness/Question, Statistics sáu nhóm/filter/null/source failures và Progress/History sau Exercise0/retake/đổi setup. Suite `playwright.w8.config.ts` hiện chỉ MOCK; không đổi tên thành LIVE.

Remaining có owner:

- Hưng: W8 LIVE Admin Assessment/Statistics/Progress/History; full smoke #40, responsive/a11y remaining #12 và evidence tại đúng source SHA.
- Huy: review Assessment/Question semantics; consumer/replay/DLT/completion #34, APIs #35 và non-empty Statistics aggregation #36 theo owner evidence.
- Khoa: User/Content/Gateway security/persistence/runbook #41, fixture Level và review tích hợp nguồn.
- Nhóm: peer review theo dependency #22 → #46 → PR W8, merge nền rồi retarget develop và rerun phần ảnh hưởng. Không tự merge Draft hoặc ghi approval thay người khác.

W7 LIVE ngày03/11 vẫn là [evidence kế thừa](w7-frontend.md), không tính thành lượt W8. #8/#10/#11 là parent tracking, không cộng effort lặp. Actual/capacity chưa được owner xác nhận; không chuyển estimate thành actual. W9 16–22/11 giữ integration/regression/security/bug fixing; W10 23–29/11 giữ deploy/demo/closing.

Nguồn đọc ngày09/11: [board t8-d](https://trello.com/b/MUzA9KLr/t8-d), PB-11/PB-12 và checklist W7-07; board chưa có card W8 tại thời điểm đọc. [Nhật ký Drive W7](https://docs.google.com/document/d/1jZkPfrPddA3K_w_WIII1-nLA6sOneQHv-qHipf6bDhg/edit) còn snapshot01/11, trong khi repo có evidence03/11: giữ đúng ngày nguồn, không dùng log cũ suy rằng code mới chưa tồn tại. Kế hoạch W8 ở [planning](../planning/w5-w8-plan.md) và issues #37/#38/#39/#12/#40.
