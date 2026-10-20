# Tích hợp code hiện có vào cây mới

## Hiện trạng xác minh 20/10/2026
main/develop: 879a0dea35f3d7ee10f6db4dd6a364c4ff7562e5, chỉ README.
PR #1 Draft → develop: feature/khoa-user-content-postgres, 56450fc0034021f1653a95a0b8ad7e3f1aa6e73f; ba deployable roots đã có code. Không đóng/merge PR này tự động.

## Owner Khoa — thực hiện trong issue chuyển cây
1. Kiểm kê trạng thái PR #1 mới nhất, nhánh backup/commit trước di chuyển. Chọn tích hợp PR #1 rồi move, hoặc cập nhật nhánh PR #1 trước merge; thống nhất một đường, không copy duplicate.
2. Di chuyển api-gateway → backend/api-gateway; user-service → backend/services/user-service; learning-content-service → backend/services/learning-content-service.
3. Sửa root Maven modules (nếu giữ optional aggregator), Compose build context/dockerfile, scripts relative paths, CI working-directory/path filters, contract checksum/snapshot tooling và README. Service vẫn build độc lập từ bản copy isolated.
4. Contracts hiện có docs/contracts/public-v0.1.yaml/internal-v0.1.yaml phải giữ bytes/hash. contracts/http chỉ là index cho tới khi có quyết định migrate canonical file; internal-v0.1.1-proposal vẫn là proposal.
5. Preserve volume names, credentials/config semantics và dữ liệu. Không docker compose down -v; backup/restore theo README PR #1. Chưa di chuyển infra/mock thành service thật.
6. Kiểm tra isolated Maven builds, package/image ownership, Compose config, scripts, contract checksum, Gateway smoke và restart persistence. Bằng chứng CI cũ không dùng như CI sau move.
7. Đối chiếu diff chỉ move/path plumbing; mọi sửa nghiệp vụ có issue/AC riêng.

## Huy
Khởi tạo Assessment/Progress ở target path ngay; DB decision/migration riêng. Không lấy mock readiness trong PR #1 làm Assessment implementation. Outbox là một phần Assessment; consumer là phần Progress, broker config trong infra/kafka.

## Hưng
Khởi tạo React ở frontend; một lockfile của app, shared responsive logic. README root hiện là scaffold, chỉ cập nhật lệnh chạy sau khi code thật sẵn.
