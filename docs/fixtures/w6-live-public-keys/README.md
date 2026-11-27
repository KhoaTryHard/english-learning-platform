# Public keys của lần TEST W6-02 ngày 01/11/2026

Đây chỉ là public keys của stack TEST Khoa dùng để chạy integration với source
Assessment PR #43; không phải key production hoặc xác nhận Huy đã cấp key.

- Assessment nhận `api.public.pem` để xác minh service JWT và Gateway assertion.
- Assessment nhận `content.public.pem` cho caller readiness.
- User/Content nhận `assessment.public.pem` để tin caller Assessment trong lần test này.

Private keys chỉ nằm trong `.secrets` cục bộ của service tương ứng và không
được bàn giao qua Git. Khi chạy bằng key Assessment của Huy, Huy giữ private
key riêng, cung cấp public key mới; phân phối lại public keys và restart
consumer. Không dùng key test này như baseline nhóm đã duyệt.

Source/caller/URL/lệnh seed/fault/retry và boundary host/container:
[runbook Compose](../../runbooks/khoa-w6-live.md),
[runbook HOST](../../runbooks/khoa-w6-host-live.md),
[phiếu auth](../../contracts/internal-auth-review-2026-10-28.md).
