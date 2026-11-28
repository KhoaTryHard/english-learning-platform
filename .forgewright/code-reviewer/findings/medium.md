# P2 — Admin Question giới hạn điểm không đúng contract

[#52](https://github.com/KhoaTryHard/english-learning-platform/issues/52), owner LeTHung,
PR47 ec373bd, `frontend/src/features/admin/AssessmentsPage.tsx:522`, `min="0.01"`.
Question `points=0.001` hợp lệ ở public v0.1 và owner413a929, nhưng form chặn lưu
ngay cả khi Admin chỉ sửa nội dung. LIVE desktop/mobile xác nhận201/200 ở API,
rangeUnderflow/no PUT ở UI; reviewer React/MSW regression FAIL.

Giữ `points > 0` và finite validation; không nâng giới hạn contract/backend.
Xem [report](../2026-11-10-pr47-review.md) và [fixture](../fixtures/QuestionForm.fractional-points-regression.test.tsx).
