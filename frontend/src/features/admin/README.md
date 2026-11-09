# admin
Owner Hưng. Feature UI theo W3-05 v0.6. Components/hooks/api/types và tests được colocate khi có implementation; gọi owner endpoints qua Gateway. Không thêm endpoint giả hoặc mock không nhãn.

W8: `/admin/assessments` quản lý Assessment/Question; `/admin/statistics` đọc sáu nhóm metric. Chỉ dùng endpoint whitelist public v0.1. Admin không có API đọc Level: bộ lọc Level dùng UUID tùy chọn với Course bắt buộc; không gọi Learner-only `/courses/{id}/levels` hoặc tự nới quyền. [Checkpoint/evidence và blocker LIVE](../../../../docs/evidence/w8-frontend.md).
