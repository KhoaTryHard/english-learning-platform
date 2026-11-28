# Demo Docker local của Khoa

Gói này chạy frontend thật, Gateway, User, Content, Assessment, Progress, bốn
PostgreSQL riêng và Kafka. Chỉ dùng local; production HOLD. Statistics #36 chưa
có implementation, không có dữ liệu thống kê giả để thay consumer.

## Chuẩn bị và khởi động

Cần Docker Desktop Linux containers, Compose hỗ trợ `!reset`/`!override`, PowerShell7
và OpenSSL trên PATH (Windows có thể dùng Git `usr/bin`). Build Java25 và Node24
chạy trong Docker. Dành đủ RAM cho năm Java services và Kafka.

```powershell
pwsh -NoProfile -File scripts/khoa-local-demo.ps1 -Action start
pwsh -NoProfile -File scripts/khoa-local-demo.ps1 -Action status
```

Mở http://127.0.0.1:5173. Các probe Gateway18080, User18081, Content18082,
Assessment8090 và Progress8091 chỉ bind loopback; DB và Kafka không publish port.
Không dùng `localhost` thay `127.0.0.1` khi đăng nhập vì Origin/CSRF đã cấu hình
chính xác cho URL trên. Nginx phục vụ SPA và proxy `/api/` tới Gateway.

Project mặc định `khoa-backlog-demo-local`; năm mạng dùng10.241.0.0/24–10.241.4.0/24.
Kiểm tra Docker networks/host routes trước lần đầu. Nếu trùng, chọn `-SubnetPrefix`
và project mới; dùng cùng tham số ở các lượt sau. Không đổi subnet của project
đang có dữ liệu/container. Không chạy regression fault/restore đồng thời demo.

Script tạo secrets trong `.secrets`, giữ giá trị cũ và bảo vệ ACL; mỗi service chỉ
nhận private key của mình. Kafka có init container ngắn chạy root để chown đúng
thư mục volume project, sau đó broker chạy user1000. Assessment/Progress đợi broker
healthy. Không thay cấu hình Kafka/production của owner.

## Tài khoản và kịch bản

Admin `admin@example.com`, mật khẩu tại `.secrets/admin_password`. Fixture Learner,
Lesson, Exercise và Vocabulary được tạo qua Gateway/owner APIs. Xem
`.tools/<project>-fixture.json` để lấy email, IDs và đường dẫn passwordFile; không
chia sẻ `.secrets` hoặc trace chứa cookie. Chạy lại xác nhận fixture cũ còn tồn tại
và giữ dữ liệu; nếu lần seed bị ngắt trước khi lưu manifest, lần sau tạo fixture
mới, không xóa dữ liệu đã tạo.

1. Đăng nhập Learner, xem Profile và Course/Level đã chọn.
2. Mở `Khoa Final Demo - Greetings`, lưu/bỏ từ vào My Vocabulary.
3. Mở Exercise liên kết, chọn Đúng, nộp và xem Result.
4. Chờ event Kafka cập nhật Progress/History. Làm lại tạo Result/History mới;
   completedLessons vẫn1 cho cùng Lesson.
5. Admin quản lý User/Content và import bằng mẫu trong `docs/fixtures`.

API public v0.1, internal auth0.1.1 và V1 giữ nguyên. Không seed SQL xuyên DB.

## Dừng và phục hồi

```powershell
pwsh -NoProfile -File scripts/khoa-local-demo.ps1 -Action stop
pwsh -NoProfile -File scripts/khoa-local-demo.ps1 -Action start
```

Stop chỉ dừng container có label project được chọn; giữ volumes, credentials và
fixture. Start build/cập nhật image rồi chạy migration/bootstrap/seed idempotent.
Không dùng `down -v`, prune hoặc reset Docker. Backup/restore đã kiểm riêng trong
[runbook W9](khoa-w9.md), dùng TEST database mới cùng cluster, không suy rộng thành
khôi phục đa máy. User/Gateway rollback theo cặp.

## Kết quả và giới hạn

Ngày28/11/2026:6 browser LIVE W7 desktop/mobile PASS với Kafka thật;4 regression
desktop/mobile cho49/52 PASS. Trong49, PUT đầu tiên được trả503 bằng fault injection
ở trình duyệt; GET/retry/submit gọi backend thật. W9 Docker regression được ghi
riêng tại PR50/#53; không cộng assertion/rerun thành số nghiệp vụ mới.

Không bao gồm broker-fault/replay/DLT, Statistics36, QA toàn nhóm40 hoặc production.
Code demo là đóng góp Khoa với Codex hỗ trợ; kết quả test không thay review của
Huy/Hưng. PR demo và PR50 theo dõi điều kiện merge riêng.
