# Đồng bộ tài liệu và issue — 27/10/2026

Theo yêu cầu Lê Tiến Hưng: bỏ CI/CD, sửa trực tiếp toàn bộ tài liệu quản lý/thiết kế trong Drive để nhất quán và dễ đọc, rà issue và đánh dấu phần đã làm. Trello chỉ đọc; không đổi card/status/deadline.

## Kết quả

- Kiểm kê 36 file trong 6 thư mục, không thấy thư mục con/file mới so với đợt audit. Đọc lại 18 Google Docs và 5 Word; đối chiếu 13 contract/schema/CSV/template/fixtures với inventory. Sửa trực tiếp 23 tài liệu, giữ nguyên ID/link và vị trí thư mục.
- Đổi tên tài liệu theo Wn-xx và tiêu đề rõ; thống nhất các cấp heading, cỡ chữ và khoảng cách; sửa các đoạn văn bị gán nhầm Title. Đặt thông tin dùng cho triển khai ở đầu, tách nội dung/lịch sử cũ. Giữ các native tables, hyperlinks và hình.
- Dùng chung SRS/UC-AC 0.4, DD 0.2, API 0.1, UI 0.6; owner Hưng FE/PM, Khoa User/Content/Gateway, Huy Assessment/Progress/Kafka. W5 19–25/10; W6 26/10–01/11 (UTC+7). Không backdate actual hoặc ký review thay nhóm.
- W5-00/04/05 đã có trạng thái ngày 27/10, SHA/env và actual/remaining. T-01/02/07/10 PARTIAL; T-08 PASS lát đầu; T-03/09 NOT_RUN; T-04/05/06 BLOCKED. MOCK và LIVE tách riêng; không quy số suite thành business cases đạt.
- W4 giữ lịch tài liệu gốc 16–18/10 và ghi rõ khác lịch board 15–17/10. Giữ estimate Planning 37,5h, W5 46h và PERT roadmap 288,3h ở đúng cấp, không cộng trùng hoặc giả actual.
- W3-03 sửa fullName nullable/max100; Vocabulary unique(lesson_id,normalized_word,normalized_meaning), các fields/limits; MODULE.published; Lesson title/content/order; Assessment.courseId/lessonId/timeLimit theo type; nullable lesson snapshots; thêm logical LearningContent mapping. Không đổi approved API hoặc tự chọn physical schema/DB Assessment/Progress.

## Bỏ CI/CD

Workflow Frontend (397910652) và Verify owner services (383448672) đã xác minh disabled_manually. Gỡ frontend.yml khỏi nhánh PR #22; cập nhật CONTRIBUTING, README, migration/task/W6 plan dùng kiểm thử thủ công và peer review. File verify.yml chỉ còn trên nhánh backend Khoa; #13 có checklist gỡ khi migration, workflow này đã tắt tại repository. Các run cũ giữ làm lịch sử đúng SHA.

## Tài liệu đã cập nhật

| Tài liệu | Đích giữ nguyên ID | Kiểm chứng |
|---|---|---|
| W5-05 — Tổng kết W5 và bàn giao W6 | [Mở tài liệu](https://docs.google.com/document/d/1Hv4nXpUpe3StJIoRegE5nv8yAnckDI__nbsdFe5LAW4/edit) | Nội dung/trình bày/đọc lại đạt; bảng và hình giữ đủ |
| W5-04 — Nhật ký kiểm thử và bằng chứng | [Mở tài liệu](https://docs.google.com/document/d/15_pgC2eS_3gfJ9L5B0XYT2AoMnLuJgkIDZUDk1RWIzo/edit) | Nội dung/trình bày/đọc lại đạt; bảng và hình giữ đủ |
| W5-01 — Gói công việc và ma trận kiểm thử | [Mở tài liệu](https://docs.google.com/document/d/1uZJpQUbJIPB4VMH5fpJaPgsf30o6VrXhYWRGtg8q3T8/edit) | Nội dung/trình bày/đọc lại đạt; bảng và hình giữ đủ |
| W5-00 — Khởi động triển khai và điều kiện sẵn sàng | [Mở tài liệu](https://docs.google.com/document/d/1S_2rIxehfD4v73M_3I-wudWHp_gUd8C9wCW-IhtwrEw/edit) | Nội dung/trình bày/đọc lại đạt; bảng và hình giữ đủ |
| W4-04 — Kế hoạch phát triển và kiểm thử | [Mở tài liệu](https://docs.google.com/document/d/1uTbMYjOV52oWuYX122cJYdd6Y83RUehiJppB9G72R2Q/edit) | Nội dung/trình bày/đọc lại đạt; bảng và hình giữ đủ |
| W4-03 — Phân công và phân bổ khối lượng công việc | [Mở tài liệu](https://docs.google.com/document/d/1AAxsUJu5XPGCXkhZYenCZlMtRAx6qfKHnpjVLDwzx1s/edit) | Nội dung/trình bày/đọc lại đạt; bảng và hình giữ đủ |
| W4-02 — Sổ rủi ro và vấn đề kỹ thuật | [Mở tài liệu](https://docs.google.com/document/d/1zAHHRzumn76bM0UKufN8uDRLNNTYHBhNHDZzfOAqOXE/edit) | Nội dung/trình bày/đọc lại đạt; bảng và hình giữ đủ |
| W4-00 — Khởi động kế hoạch và baseline kỹ thuật | [Mở tài liệu](https://docs.google.com/document/d/1VxS_3nj5hmkSoXgZfbLmN6O4EvaP8O76RKQXfRuFPq8/edit) | Nội dung/trình bày/đọc lại đạt; bảng và hình giữ đủ |
| W4-05 — Review kế hoạch và bàn giao W5 | [Mở tài liệu](https://docs.google.com/document/d/180Vf16Lgjrhp-ieoLevuR-766bFANKpP4Qi62b_RHS0/edit) | Nội dung/trình bày/đọc lại đạt; bảng và hình giữ đủ |
| W4-01 — Ước lượng WBS và lịch thực hiện | [Mở tài liệu](https://docs.google.com/document/d/1HywsPPWSlJ5vwZL4FohO0VRte9cGZmXW-y5CWNMFfwI/edit) | Nội dung/trình bày/đọc lại đạt; bảng và hình giữ đủ |
| W3-05 — Đặc tả UI/UX và truy vết | [Mở tài liệu](https://docs.google.com/document/d/1FOEg7FQgBHKv-8RoRYfN-axBOHlQTVQwDOHEZnXixKc/edit) | Nội dung/trình bày/đọc lại đạt; bảng và hình giữ đủ |
| W3-06 — Review thiết kế và bàn giao | [Mở tài liệu](https://docs.google.com/document/d/1AVLy5gSfx4SaNgb_4CE_Qrq3Xgybje3YfVF7nKz-PPk/edit) | Nội dung/trình bày/đọc lại đạt; bảng và hình giữ đủ |
| W3-01 — WBS và từ điển gói công việc | [Mở tài liệu](https://docs.google.com/document/d/14kswPuIbLVtdzTMvDpjCd7qBi5bMoCeDiHEg6kWAaX8/edit) | Nội dung/trình bày/đọc lại đạt; bảng và hình giữ đủ |
| W3-00 — Sổ quyết định thiết kế | [Mở tài liệu](https://docs.google.com/document/d/1OepZRaVWvEQJOIQcPRPsOq6Ld_4LK5FA5rmnUD2Vv3A/edit) | Nội dung/trình bày/đọc lại đạt; bảng và hình giữ đủ |
| W3-04 — Đặc tả API và hợp đồng tích hợp — v0.1 Approved | [Mở tài liệu](https://docs.google.com/document/d/1VQKbmWOJae3Kj3S92ea08pUb-bswQNyDtuBGH_QTPBc/edit) | Nội dung/trình bày/đọc lại đạt; bảng và hình giữ đủ |
| W2-01 — Phân tích và làm rõ yêu cầu | [Mở tài liệu](https://docs.google.com/document/d/1GZn-pB5EqMJ2qxNUGkp-uUFqCtm2Lq3MIcKmREkCdWk/edit) | Nội dung/trình bày/đọc lại đạt; bảng và hình giữ đủ |
| W2-03 — Use Case và tiêu chí chấp nhận | [Mở tài liệu](https://docs.google.com/document/d/14OKvB16aG1HgfH-aSe5fsp_3xm2jVDqmiNl482YWALs/edit) | Nội dung/trình bày/đọc lại đạt; bảng và hình giữ đủ |
| W2-02 — Đặc tả yêu cầu phần mềm | [Mở tài liệu](https://docs.google.com/document/d/1GcHdmljWlC5P4o7V3UMh9OkKSThlYrPaxWFWdIlI8w0/edit) | Nội dung/trình bày/đọc lại đạt; bảng và hình giữ đủ |
| W1-00 — Project Charter.docx | [Mở tài liệu](https://drive.google.com/file/d/1H50AzoLnNAqy4siouRZAK9V5fA9vQZeV/view) | Upload cùng ID, đọc lại đạt; đã kiểm tra bản Word xuất PDF |
| W1-05 — Bản nháp kiến trúc và ghi chú hiện hành.docx | [Mở tài liệu](https://drive.google.com/file/d/1BfbEgtAxRfM4Gx8HrIBYrcKu1EIpLbEf/view) | Upload cùng ID, đọc lại đạt; đã kiểm tra bản Word xuất PDF |
| W1-06 — Bản nháp dữ liệu và tích hợp.docx | [Mở tài liệu](https://drive.google.com/file/d/1bWNF_G87ntm0zuVXHlXHP1_FY-erjP2v/view) | Upload cùng ID, đọc lại đạt; đã kiểm tra bản Word xuất PDF |
| W3-02 — Kiến trúc hệ thống.docx | [Mở tài liệu](https://drive.google.com/file/d/1xSXfTD4GKHEXbNtESut-ymTYRDsG-MdR/view) | Upload cùng ID, đọc lại đạt; đã kiểm tra bản Word xuất PDF |
| W3-03 — Thiết kế cơ sở dữ liệu.docx | [Mở tài liệu](https://drive.google.com/file/d/1nqJvPf1zzGA2tHtQZOUu7IqiOwrCbdYE/view) | Upload cùng ID, đọc lại đạt; đã kiểm tra bản Word xuất PDF |

13 file kỹ thuật/mẫu/fixture giữ nguyên byte/nội dung: public/internal OpenAPI approved v0.1, event schema/example, traceability/review CSV, import validation/expected results, Lesson/Vocabulary templates, fixtures valid/invalid. Fixture cố ý thiếu Title/sai dữ liệu phải giữ lỗi để kiểm thử; không sửa thành mẫu hợp lệ. Danh sách nguồn đầy đủ ở [inventory](drive-inventory-2026-10-27.md).

## Kiểm chứng

- Google Docs: đọc lại đủ 18 tài liệu; số tab/table/image bằng trước sửa. Tổng 14 bảng native và 41 inline images được giữ. Có PDF xuất cho từng file; xem phần đầu/cuối và các vùng thay đổi, không tuyên bố đã review trực quan mọi trang phụ lục.
- Word: chỉnh bằng bundled python-docx, giữ đủ 13 hình và các bảng cũ; thêm một bảng logical mapping LearningContent (24→25 bảng trong W3-03). Xuất PDF bằng Microsoft Word vì máy không có LibreOffice; xem toàn bộ 45 trang của 5 file. Upload vào file gốc, fetch đọc lại thấy ngày/policy/mapping mới.
- Kiểm thử thủ công mới trên cùng code nền 55de70f, chỉ thay tài liệu/workflow: npm run check PASS (lint, 14 unit/UI, typecheck/build, production/contract guard); npm run test:e2e PASS 6 MOCK tests. LIVE 2 tests vẫn là evidence đã chạy trước trong cùng ngày, không báo đã chạy lại backend trong phiên chỉnh tài liệu.
- git diff --check đạt. Không thêm dependency vào package.json/lockfile; công cụ render tài liệu dùng runtime ngoài repo.

## Issue và phần còn mở

Đã rà toàn bộ 19 issue #2–#17/#19/#20/#21. Checklist phân biệt triển khai, test MOCK/LIVE, review và tích hợp; các đoạn cũ giữ dưới mục lịch sử. #11 Admin chưa triển khai; #4/#15–17 chưa có backend runtime Huy được kiểm chứng. #5/#8/#9 có code/test, vẫn chờ peer review và phần integration theo scope.

#20: mapping tài liệu đã sửa/đọc lại; Khoa/Huy còn review physical schema, migrations/tests và hình ERD/cardinality. Hình ERD gốc được ghi rõ là tổng quan, chưa giả đã vẽ bổ sung LearningContent. #21: Gateway routes Assessment/Progress và Idempotency-Key/CORS chưa triển khai. GitHub @Huy1188 còn cần owner repo xử lý collaborator/invitation; giữ Responsible Huy.

Không kết luận toàn bộ thiết kế/runtime đã nhất quán hoàn toàn hoặc full MVP đã Done. Các điểm còn phải xác nhận có issue/owner thay vì để câu TBD không rõ trách nhiệm.
