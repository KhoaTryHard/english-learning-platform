# P1 — Attempt báo đã lưu sau save503 và GET trạng thái thành công

Owner Hưng; [issue #49](https://github.com/KhoaTryHard/english-learning-platform/issues/49).
Nguồn PR22 `f386170`, PR46 `1e11c6d`, PR47 `ec373bd` có cùng blob AttemptPage.

`frontend/src/features/assessment/AttemptPage.tsx:59–69`:

```tsx
try { await queue.flush(); } catch { /* reconcile deadline */ }
try {
  accept(await api.attempt(initial.attemptId));
  setError(undefined);
} catch (e) { setError(e); }
```

Khi PUT answers thất bại, local answers còn bộ đáp án người học chọn, server chưa
có. synchronize bỏ lỗi queue rồi xóa error sau GET200, dù chưa nhận ACK lưu bộ
đáp án mới. UI vẫn checked radio nhưng báo đã lưu, bỏ cảnh báo beforeunload.
Reload/rời trang có thể mất đáp án. synchronize được gọi cả từ nút kiểm tra trạng
thái và khi tab visible.

React/MSW repro: chọn đáp án, PUT503, GET200; xác minh radio checked và server
answers=[] rồi assert không hiển thị đã lưu. FAIL tại cả hai snapshot đã chạy.
Các suite sẵn có PASS không phủ chuỗi lỗi này. Không gọi repro là LIVE DB outage.

Sửa theo trạng thái dirty/ACK: giữ cảnh báo nếu bộ local mới nhất chưa được ACK;
GET200 không tự đánh dấu PUT thành công. Nếu chủ động thay bộ đáp án local bằng
snapshot server, UI cần thể hiện việc bỏ đáp án chưa lưu. Giữ terminal/timeout
reconciliation, serialize write và ACK trước submit. Thêm regression manual
resync/visibilitychange, retry ACK và terminal; retest trên SHA sửa.
