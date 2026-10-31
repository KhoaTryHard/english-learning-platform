# Huy cần trực tiếp xác nhận những gì?

Checkpoint hồ sơ 31/10/2026, refs W5-02/W5-04/W5-05 và PR #43.
Code/test foundation đã có. Các bước dưới đây cần quyết định hoặc phối hợp của
người thật; trợ lý đã chuẩn bị tài liệu, key package và nội dung phản hồi.

## 1. Kiểm tra và đưa cập nhật Word lên tài liệu chung

- W5-04: Windows từ chối thay file gốc. Mở bản hoàn chỉnh ở
  `Drive/Cap nhat Huy 2026-10-31/HUY - W5-04 — Nhật ký kiểm thử và bằng chứng.docx`.
  Kiểm tra mục `E-HUY-20261031-W5-04`. Sau khi đóng ứng dụng/preview đang giữ file,
  dùng bản này để lưu cập nhật vào đúng tài liệu chung. Không xóa bản gốc trước.
- W5-05: bản local đã có mục `E-HUY-20261031-W5-05`.
- W3-03: bản local đã có mục `E-HUY-20261031-CONTENT-ERD` và hình Content ERD mới.
- Các bản gốc có backup trong `.cache/w5-weekly-document-backups`.

Chuyển đúng phần phụ lục sang tài liệu Drive hiện có của nhóm sau khi kiểm tra,
giữ link/ID và lịch sử cũ. Đọc lại trên Drive để xác nhận bảng/hình/nội dung có đủ.
File local được sửa không đồng nghĩa Drive online đã được cập nhật.

## 2. Chọn đề xuất mapping để trả lời #20

Đọc `docs/reviews/2026-10-31-huy-schema-auth-review.md`. Chọn một hướng để trao đổi
với Khoa, chưa tự coi đã có đồng thuận:

- **Giữ physical JSON/count projection:** ghi rõ Question/Option/Answer/Result
  fields nằm trong JSON và denominator Progress derive từ Content; cập nhật
  logical/physical dictionary, ràng buộc nào ở DB và ràng buộc nào ở application.
- **Normalize theo logical model:** cần migration V2, backfill/verification và
  regression tests; không sửa V1 đã áp dụng vào database dùng chung.

Ngoài ra có khác biệt RevokedToken của User do Khoa sở hữu. Nhờ Khoa xác nhận
mapping `jti/expires_at` so với dictionary cũ; không tự sửa DB User từ nhánh Huy.
Review hình ERD mới có LearningContent và parent 1 → child 0..*.

Sau khi Huy/Khoa thực sự chốt, ghi phương án, SHA tài liệu/code, owner và phần
cần sửa vào #20. Đừng chỉ ghi “đã có code” rồi tick toàn bộ reconciliation.

## 3. Đọc và trả lời review #42 bằng quyết định của bạn

Code Huy dùng signed owner handoff phù hợp runtime Khoa đã đọc. Tuy nhiên,
request token-only approved v0.1 không tương thích proposal bắt buộc requestMethod.
Bạn cần chọn `Đồng ý hướng proposal` hoặc `Yêu cầu sửa`, kèm SHA đã xem và các điểm
còn thiếu. Mẫu phản hồi nằm cuối báo cáo review; thay phần trong ngoặc bằng ý kiến
thực tế, không gửi như approval nếu chưa đọc.

Khoa cần xác nhận version/cặp User-Gateway/public keys; Hưng ghi baseline/rollout
sau quyết định nhóm. Không tự chuyển proposal thành approved hoặc merge PR #1.

## 4. Trao public keys và xác nhận môi trường với Khoa

Trợ lý đã tạo key phát triển riêng cho Assessment và Progress, không ghi đè key
cũ nếu có. Private keys nằm trong `.secrets` của chính service, bị Git ignore.

Gói gửi cho Khoa: `Integration handoff Huy/HUY-public-keys-2026-10-31.zip` ở root
workspace. ZIP chỉ chứa hai public keys, fingerprint SHA-256 và README; không có
private key. Gửi gói này qua kênh nhóm của bạn, không gửi thư mục `.secrets`.

Nhờ Khoa trả lại:

- `api.public.pem` để cả Assessment/Progress xác minh Gateway.
- `content.public.pem` để Assessment xác minh Content gọi readiness.
- Source/image SHA và USER_URL/CONTENT_URL/GATEWAY_URL của môi trường test.
- Course/Level/Module/Lesson seed IDs, Learner/Admin test theo kênh phù hợp;
  không dán password/token/cookie vào issue hoặc chat công khai.

Đặt public keys nhận được vào `.secrets/public` đúng service. Có thể so fingerprint
bằng `Get-FileHash <public-file> -Algorithm SHA256`. Không thay hoặc chia private
key của Gateway/User/Content. Đây là key cho phát triển; key triển khai thật theo
quy trình nhóm và environment đã chọn.

## 5. Xác nhận thời gian thực tế và capacity cho W5-05

Điền thông tin bạn thực sự biết:

```text
Actual W5 của Huy: ... giờ / chưa theo dõi được
Actual công việc bổ sung W6: ... giờ / chưa theo dõi được
Remaining estimate: ... giờ (liệt kê việc)
Capacity còn lại tuần hiện tại: ... giờ
Blocker cá nhân hoặc lịch phối hợp: ...
```

Không lấy 11h/15h/23h dự toán làm actual. Không suy số giờ từ thời gian Codex chạy.
Hưng tổng hợp/replan với bạn, không tự cam kết capacity thay Khoa/Hưng.

## 6. Nhờ reviewer và chuẩn bị kiểm thử tích hợp

Gửi link PR #43 và báo cáo review cho Khoa/Hưng, nhờ người khác tác giả review
đúng source SHA. Huy không tự approve PR của mình.

Khi có quyết định #20/#42 và môi trường/keys/seed:

1. Xác nhận database/ports/URLs/key fingerprints và source/image của từng service.
2. Chạy health, signed readiness và bốn pha readiness theo runbook Khoa.
3. Chạy Auth → Course/Level → Lesson → Exercise → Result qua Gateway/frontend thật;
   kiểm retry giữ attempt, retake tạo mới và ownership/errors.
4. Có broker rồi mới chạy event delivery, duplicate/retry, DLT/replay và restart.
   Nếu dùng Docker, kiểm tra `docker version`, `docker compose version` và daemon
   trước khi chạy; môi trường hiện tại chưa có Docker CLI/runtime.
5. Ghi lượt test mới có SHA/env/seed/command/expected/actual. Trợ lý có thể tiếp tục
   chạy kỹ thuật khi đủ đầu vào; không cần bạn tự viết lại test hoặc code nền tảng.

Trello: Huy có thể ghi nhận phần code/self-test và cập nhật evidence của mình;
checkbox gộp acceptance/LIVE thì để chưa tick. W5-04/W5-05 là thẻ chung do Hưng
điều phối, không đánh dấu hoàn tất thay các thành viên khác.
