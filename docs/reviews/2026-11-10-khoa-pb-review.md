# Khoa — thực hiện phần PB còn lại ngày10/11/2026

Đã review delta PR47, kiểm thử phần W8 mới qua Gateway/PostgreSQL/Kafka thật và
ghi hai finding frontend. **Không tổ chức nghiệm thu riêng từng PB** theo yêu cầu
Khoa; kiểm thử kỹ thuật vẫn đi cùng thay đổi. #31 tiếp tục Open theo quyết định
riêng về consumer. Application source User/Content/Gateway không có lỗi mới được
tái hiện trong phạm vi này, không phát sinh bản sửa backend.

## Kết quả theo thứ tự kế hoạch

| Bước | Phần đã thực hiện | Còn lại / owner |
|---|---|---|
|1 Review47|Đã đối chiếu delta ec373bd trên base1e11c6d: Admin Assessment, Statistics, Progress/History, route/role và source contract|Hưng sửa52 và49; Khoa review SHA sửa|
|2 Content–Assessment|Đã đọc lại PR44/43 và xác minh runtime/source mapping. LIVE readiness409→ready→publish PASS với bản có44|Huy review44 và đưa fix vào43; chưa có SHA mới để chạy lại DB503|
|3 Attempt49|Tái hiện trên PR47 mới được review: reviewer test FAIL cùng nguyên nhân đã ghi|Hưng patch49; không lặp review22/46 trên SHA cũ|
|4 Source/consumer|LIVE History giữ retake/Level snapshot khi đổi setup, completion không trùng; Progress refresh đúng Course mới|Statistics owner404; Huy35/36/cross-review;31 Open5/7|
|5 Auth/Setup/Profile/Admin User|Source/nhánh nền22/46 không đổi; route/shell regression trên47 PASS; kế thừa evidence từng luồng|Chỉ retest phần bị ảnh hưởng khi có patch/rebase mới|
|6 Import/My Vocabulary|Kế thừa Import rollback/DB503 và collection evidence09/11 theo tree; không có code mới ở các feature này|Hỗ trợ sửa lỗi tái hiện mới nếu có|
|7 Bàn giao|Công bố review, fixture, manifest tại PR50; đồng bộ kết quả mới vào GitHub/Trello/W8 Doc|Các dependency vẫn giữ đúng owner và trạng thái|

## Bao phủ13 PB

| PB | Trạng thái phần Khoa sau lượt này |
|---|---|
|01 Auth|Backend đã merge; kế thừa LIVE/security, không có delta feature|
|02 Profile|Backend đã merge; kế thừa ownership/persistence/LIVE|
|03 Setup|Đã kiểm thêm đổi Course với History snapshot thật ở UI47|
|04 Learning Content|Readiness/publish trên runtime có44 PASS; baseline43 chờ Huy|
|05 My Vocabulary|Đã PASS trước; không làm lại khi source không đổi|
|06 Exercise|Event thường/retake có evidence; UI49 còn mở|
|07 Quiz/Test|Giữ evidence deadline/retake; UI49 và server gates của Huy còn mở|
|08 Progress/History|W8 UI refresh và Level snapshot LIVE PASS;31 chưa đóng|
|09 Admin User|Đã PASS trước; không có delta nghiệp vụ|
|10 Admin Content|Đã PASS trước; thêm chứng cứ Lesson publish qua Assessment thật|
|11 Admin Assessment|W8 authoring LIVE PASS trong luồng1 điểm; defect52 chặn điểm hợp lệ0.001|
|12 Statistics|UI contract/MOCK đã review; consumer36 chưa có, Admin404|
|13 Import|Backend rollback đã PASS09/11; giữ gate Khoa đã ghi tại33|

## Bằng chứng và giới hạn

55 unit/UI sẵn có,8 browser MOCK regression,2 W8 MOCK contract PASS. LIVE có4 ca
chức năng PASS và2 ca tái hiện52 thành công;2 reviewer unit FAIL xác nhận49/52.
Không cộng số lần chạy kế thừa/desktop/mobile thành case nghiệp vụ độc lập.
Các kết quả trên không thay full MVP QA tại40 hoặc consumer Statistics.

Tái dùng project TEST `khoa-backlog-review-20261109`, images cũ theo tree, fixture
mới qua owner API,4 PostgreSQL17.11/Kafka4.0.0. Không build image mới, không chạy
lại Maven backend vì application trees không đổi. Stack được dừng sau khi lấy
evidence; volumes/dữ liệu giữ. Manifest là nguồn xác minh trạng thái dừng.

Hưng giữ owner49/52, Huy giữ44/35/36; các phản hồi cũ không thay review mới.
Publicv0.1/internal0.1.1/V1 giữ; production HOLD W10. Các issue đã Closed không mở lại.

- [Review chi tiết](../../.forgewright/code-reviewer/2026-11-10-pr47-review.md).
- [Manifest mới](../evidence/khoa-pb-review-2026-11-10/manifest.json).
- [Ma trận nguồn31 đã có](../handoffs/khoa-consumer-acceptance-2026-11-09.md).
- [Gói09/11 đã công bố](2026-11-09-khoa-backlog-review.md).
