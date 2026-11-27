# Quyết định internal auth W6-02 — 01/11/2026

Trạng thái: **BASELINED_0.1.1_VIA_KHOA_RELAY / W6_ACCEPTED / PRODUCTION_HOLD_W10**.

Checkpoint TEST sau W6: Docker Engine đã hoạt động và có local TEST images cho
User/Content/Gateway/Assessment/Progress trong đợt backlog01/11; xem
[evidence mới](../evidence/khoa-backlog-2026-11-01/manifest.json).
Đây là diễn biến môi trường sau quyết định HOST bên dưới, không sửa nguồn xác nhận
lịch sử. Baseline0.1.1 và production HOLD W10 giữ nguyên; local image IDs không
thay registry digest, review hoặc rollout/rollback production được chấp thuận.
Đây là bản ghi phản hồi thực của Huy/Hưng do Khoa chuyển lại và kết quả nghiệm
thu W6. Approved v0.1 được giữ nguyên; baseline mới nằm ở `internal-v0.1.1.yaml`.

## Phản hồi và nguồn xác nhận

Trong phiên W6-02 ngày01/11, Khoa chuyển ba phản hồi:
`1. đồng ý`, `2. approval`, `3. chấp nhận em sửa`. Khi được hỏi người quyết
định, Khoa xác nhận: **“Anh chuyển phản hồi thực tế của Huy”**.

- Huy đồng ý nội dung auth #42 và peer approval PR #1 được thảo luận tại HEAD
  `b8bdcfe350989e7c98075fa004890cc76a48b7f2`. Không có review event dưới tài khoản
  Huy trên GitHub; hồ sơ ghi nguồn là **Huy phản hồi, Khoa chuyển lại**.
- Huy chấp nhận Khoa/Codex sửa lỗi DB. Bản sửa sau đó nằm ở PR #44,
  `413a9297d4d63f61b0ad4b81a0c1f1e7a9370abd`; chưa gán peer approval cũ của
  PR #1 thành approval cho bản sửa này.
- Khoa tiếp tục yêu cầu hoàn tất W6-02; phía Khoa ghi kết quả kỹ thuật và phản
  hồi Huy trong tài liệu này. Sau khi được trình nội dung baseline/version,
  rollout/rollback theo cặp và HOLD production W10 bên dưới, Khoa xác nhận:
  **“Hưng đã xác nhận, anh chuyển lại”**. Đây là nguồn xác nhận PM được dùng
  để ghi baseline; không tạo chữ ký hoặc GitHub review event dưới tên Hưng.

## Baseline Hưng đã xác nhận, do Khoa chuyển lại

| Nội dung | Quyết định đã ghi nhận |
|---|---|
| Version | Internal contract **0.1.1**, giữ HTTP base `/internal/v1`; public API v0.1 không đổi |
| Nội dung | Proposal SHA-256 `54280e3b1c0f864aea4a1e49f027ca1ffc49bd754823ee27c5d39343b7de1ae1`; tạo `internal-v0.1.1.yaml`, giữ proposal và frozen v0.1 làm lịch sử |
| Compatibility | Breaking request shape: `token` + `requestMethod` bắt buộc; unsafe có `csrfSessionId`/`csrfToken`; token-only bị400, không default GET |
| Caller/assertion | Chỉ api gọi session validate; Assessment/Progress dùng source APIs theo allowlist. Service JWT RS256 và Gateway assertion bind audience/method/path/user/role/sessionJti |
| Mã lỗi | Invalid/expired/revoked/BLOCKED401; CSRF/session sai403; dependency503; không trả chi tiết DB/token |
| W6 release pair | User/Gateway từ application tree PR #1 tại b8bdcfe; JAR identities bên dưới là cặp TEST đã kiểm chứng |
| Rollout/rollback | Đổi User/Gateway theo cặp, dừng Gateway trong lúc thay. Cặp TEST đã kiểm chứng là baseline khôi phục cho các thay đổi TEST kế tiếp. Không có cặp production được phê duyệt trong W6 |
| Deployment | **HOLD production đến W10**: Docker engine chưa sẵn, image/digest/rehearsal production chưa có. Giữ quy trình pair; không ghi Docker rollout PASS từ HOST evidence |
| Nghiệm thu W6 | HOST integration đã PASS tại source/test fixture ghi trong evidence; việc merge PR #44/#1 và deployment theo workflow repository có gate riêng |

User JAR SHA-256: `5603d08eb16ae1bc55c9e75e12faa94682af76bef730bc2de7652402b7686f04`.
Gateway JAR SHA-256: `39a550b3d8aad84ea21bcdf5aa5dd783ae5bf63eda42c2c8072b57488d9069b7`.
Không coi hai hash này là Docker image digests. Application User/Content không
đổi từ source626b3d4; Gateway fix85462cf có trong b8bdcfe. Assessment candidate
PR #44 có JAR hash `2e3b4f3d81f4808a9b79d690029b99ede587a451baa11fc007664cc67c03ff9f`.

## Bằng chứng để chốt

Assessment35 tests PASS gồm4 regression advice; trước sửa ca transaction
connection expected503/actual500. Lượt HOST mới dùng ba PostgreSQL17.11 clusters:
online30, service-down7, database-down7, recovery16 và retry/security40 checks
đều PASS; session privilege fault PASS. Mỗi số là checks trong report, không
cộng thành số test JUnit. Data/report giữ nguyên, app và clusters đã dừng.

Phản hồi Huy được chuyển trước bản sửa PR #44 hoàn tất. W6 checklist peer
review liên quan PR #1; PR #44 tiếp tục review riêng. Baseline/version và quyết
định W6/HOLD-W10 được Hưng xác nhận thông qua lời chuyển của Khoa, sau khi
Khoa được trình bản sửa và kết quả LIVE PASS. W6-02 được nghiệm thu theo các
nguồn xác nhận này; không tạo native approval dưới tài khoản người khác.
