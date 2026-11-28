# Bàn giao kỹ thuật của Khoa ngày28/11/2026

User/Content/Gateway đã merge qua PR1/45/48; foundation Assessment/Progress qua
PR43 tại `7119946b49d63ea43e98adac64917aa0679267bf`, gồm fix44. Gói Docker demo mới
ở [PR54](https://github.com/KhoaTryHard/english-learning-platform/pull/54), SHA
`7e510250fe70d9f4e1bb919d7f0cabd82c4ca6aa`. PR50 chứa hồ sơ/fixture/evidence,
không chứa thay đổi ứng dụng. [#53](https://github.com/KhoaTryHard/english-learning-platform/issues/53)
theo dõi phần final còn lại; chưa coi toàn dự án đủ điều kiện final.

## Kết quả mới

| Phạm vi | Kết quả ngày28/11 | Bằng chứng |
|---|---|---|
| W9 User/Content/Gateway | PASS Docker thật: auth/role/CSRF/owner APIs, import atomic, saved vocabulary, source faults, readiness409/503/recovery, session, backup/restore/restart | [Manifest](../evidence/khoa-final-2026-11-28/manifest.json) |
| Build/tests backend | 63 Maven PASS,1 LIVE opt-in skip; các pha LIVE chạy riêng;9 tooling tests | maven-summary trong manifest |
| Frontend | 56 unit/UI PASS, lint/build/contract PASS,8 browser MOCK PASS | frontend.json |
| Browser W7 | 6 LIVE desktop/mobile PASS: Admin User/Content/import, register/setup/vocabulary, Exercise/Quiz/Test, response-loss retry, event thật và retake | browser-w7-live.json |
| Fix52 |2 LIVE desktop/mobile PASS: sửa Question qua API giữ points0.001 và false | browser-regressions-live.json |
| Fix49 |2 desktop/mobile PASS với fault injection PUT503 tại browser; GET/tab return giữ unsaved; retry ACK và submit thật | browser-regressions-live.json |
| Demo | Start/status/stop/start, seed lặp giữ IDs; fixture có Admin/Learner/Lesson/Exercise/Vocabulary | demo-runtime.json |

Các assertion và lượt chạy lại không được cộng thành số nghiệp vụ độc lập.
Hai lỗi môi trường W9 (JAVA_HOME, Docker address pools) giữ trong environment-history;
demo có lỗi volume Kafka và MIME import đã sửa, không xóa dữ liệu cũ. Các report
`PASS_REVIEW_REQUIRED` giữ nguyên nhãn nguồn; PASS kỹ thuật không thay review người.

## Phần Khoa theo PB

| PB | Phần Khoa đã có bằng chứng | Phần còn lại |
|---|---|---|
|01/02/03|Auth/Profile/Setup, role/session/CSRF, persistence; browser thật|Hưng kết luận QA tổng|
|04/10|Content/Admin Content, publish/readiness và lỗi503; browser/import|Phạm vi PB đầy đủ và review nhóm|
|05/13|Saved Vocabulary không trùng/cô lập/nguồn cập nhật; XLSX/DOCX atomic; browser thật|Không tạo lại coding nếu không có lỗi mới|
|09|Admin User/role/block và quyền backend; browser thật|Hưng QA tổng|
|06/07/11|Gateway/auth, Idempotency-Key, foundation43/44; retry/retake; review UI49/52|Huy nghiệp vụ Assessment/concurrency/retention; Hưng QA|
|08|Owner APIs và Progress/History với event thật; retake History tăng, completion không trùng|Huy hoàn tất consumer34/35, broker-fault/replay/DLT và review|
|12|Nguồn User/Content PASS, cohort BLOCKED/global count/filter/denominator draft/source503|Statistics36 chưa implementation; không claim Statistics PASS|

[#31](https://github.com/KhoaTryHard/english-learning-platform/issues/31) giữ Open
theo quyết định Khoa, dù source và đường Progress bình thường đã PASS. Source API
không cần chờ31 Closed mới được dùng. Không seed Progress SQL xuyên owner.

## Hướng dẫn trình diễn và phục hồi

Checkout PR54/version trên, PowerShell7 và Docker Linux containers; OpenSSL có trên
PATH. Chạy `pwsh -NoProfile -File scripts/khoa-local-demo.ps1 -Action start`, mở
http://127.0.0.1:5173. Runbook đầy đủ ở
[PR54](https://github.com/KhoaTryHard/english-learning-platform/blob/7e510250fe70d9f4e1bb919d7f0cabd82c4ca6aa/docs/runbooks/khoa-local-demo.md).
Đọc `.tools/khoa-backlog-demo-local-fixture.json` để lấy Learner email và passwordFile;
Admin password trong `.secrets/admin_password`. Không đưa credentials vào báo cáo.

Demo chỉ publish loopback. Dừng bằng cùng script `-Action stop`, dữ liệu/volumes giữ
nguyên. [Runbook W9](../runbooks/khoa-w9.md) có backup/restore TEST DB mới và rollback
User/Gateway theo cặp; không suy rộng restore cùng cluster thành phục hồi đa máy.

## Đóng góp và quyết định

Khoa phụ trách User/Content/Gateway, thiết kế contract/owner APIs, import/My Vocabulary,
integration/security/persistence regression, review interface và gói demo local;
Codex hỗ trợ triển khai/kiểm thử/tổng hợp. Huy vẫn sở hữu Assessment/Progress/Statistics;
Hưng sở hữu frontend và QA40. Bằng chứng chính: PR1/45/48/43/50/54 và manifest mới.
Không suy ra tỷ lệ đóng góp, giờ thực tế hoặc điểm đánh giá từ số commit/test.

Giữ publicv0.1, internal0.1.1, V1. Khoa đã chấp nhận physical PostgreSQL/V1/JSON/count
projection cho foundation; #20 tiếp tục dictionary/ERD của Huy. #49/#52 đã sửa;
W3-04 ô gộp auth/retry/retention giữ pending vì retention/concurrency cần Huy.
Waiver review14 không tự áp dụng các PR mới hoặc hồ sơ SRS/UC.

## Điều kiện còn mở

- PR54 cần review thật Huy/Hưng trên SHA bàn giao trước merge; đã gửi review request.
- PR50 cần xử lý review hồ sơ theo điều kiện hiện hành; không self-approve.
- W9 card gốc RvxYVJF4 trả401; chưa cập nhật, không tạo thẻ trùng.
- Báo cáo DOCX đã chuẩn bị phần kỹ thuật nhưng công cụ render thiếu LibreOffice;
  bản Drive chưa ghi đè trước khi kiểm tra bố cục. Nhật ký W9 được cập nhật riêng.
- #20, #31, #30/#34–36 và #40 giữ trạng thái theo owner/phạm vi. Kafka thông thường
  PASS không chứng minh broker-fault/replay/DLT; chưa nghiệm thu toàn MVP/production.

Production HOLD. Phần kỹ thuật Khoa đã đạt trong phạm vi trên; final toàn nhóm vẫn
phụ thuộc Statistics, review và QA của Huy/Hưng cùng hồ sơ còn chờ.
