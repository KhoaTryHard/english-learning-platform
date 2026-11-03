# REST contracts

Canonical approved bytes hiện ở docs/contracts trong PR #1: public-v0.1.yaml và internal-v0.1.yaml. Giữ hash, không tạo bản YAML rỗng trùng source. Khi merge/migrate, index này trỏ tới file canonical thật. internal-v0.1.1-proposal chưa thay approved v0.1. Khoa/Huy owner theo endpoint.
# Public v0.1 dùng cho frontend

`public-v0.1.yaml` là bản nguyên byte từ Drive W3-04, khớp approved file ở PR #1 SHA 56450fc. SHA-256: `800a25b92e7272a2e02ea1d08b29612ad58d28d0db85dea9743c25257537e22c`. Giữ nhãn PROPOSED lịch sử trong file; trạng thái approved hiện hành theo companion W3-04/W3-06. Không sửa kỹ thuật baseline để dọn nhãn cũ. Internal v0.1.1 của PR #1 vẫn là proposal riêng.

Types sinh bằng `npm run types:api` trong frontend. CI kiểm tra generated type drift và hash baseline.
