# #31 — bàn giao nghiệm thu API nguồn cho Progress/Statistics

Khoa đã hoàn tất phần API nguồn. **#31 giữ OPEN / 5 trên 7 mục** theo quyết định
của Khoa ngày 09/11/2026: chờ nghiệm thu consumer và cross-review thực tế của Huy.
W7-05 Done chỉ khép bàn giao nguồn; không thay hai điều kiện còn lại của #31.

## Phiên bản và kết nối

| Thành phần | Phiên bản bàn giao |
|---|---|
| User/Content/Gateway trên develop | `af92253a95cf39fbdbfcbada2cfdd47fd413534d` |
| User tree | `6b2298167f2ce1da595133957540cbb2a65aecf1` |
| Content tree | `8850af6dfccf3a230ef5b9ea5e99f52ec52269ae` |
| Gateway tree | `c5012a9f74c156be70f319895a32ccccf1f42bdb` |
| Consumer hiện có để đối chiếu | PR #43 `c1a2a074a4cc6c0d3d0bdf04ef77540a530c5930`; bản kèm fix #44 `413a9297d4d63f61b0ad4b81a0c1f1e7a9370abd` |

Public API v0.1; internal auth baseline 0.1.1 (`token + requestMethod`, unsafe
methods có CSRF), đường dẫn internal `/internal/v1`. Trong mạng Docker TEST:
User `http://user:8081`, Content `http://content:8082`, Assessment
`http://assessment-live:8090`, Progress `http://progress-live:8091`; Gateway
host `http://127.0.0.1:18080`. Consumer giữ private key của mình; nguồn nhận
public key `progress`. Không đưa private keys/cookies/token vào report.

| API nguồn (GET) | Caller được phép |
|---|---|
| User `/internal/v1/users/{userId}/learning-setup` | assessment, progress |
| User `/internal/v1/learners/cohort` | progress |
| Content `/internal/v1/courses/{courseId}/context` | user, assessment, progress |
| Content `/internal/v1/courses/contexts` | progress |
| Content `/internal/v1/lessons/{lessonId}/context` | assessment |

Pagination nguồn: page bắt đầu 0, size 1–100, mặc định 100. Nhiều trang/nhiều
service không tạo một transaction snapshot chung. Lỗi nguồn bắt buộc trả 503;
consumer không được chuyển lỗi thành số 0 thành công.

## Ma trận expected/actual cần Huy nghiệm thu

Các dòng dưới đây là **ca cần chạy**, không phải kết quả PASS mới. Bằng chứng
nguồn đã chạy được liên kết ở phần cuối; actual consumer chỉ ghi sau khi có run.

| Case | Fixture/thao tác | Expected | Actual / trạng thái hiện tại |
|---|---|---|---|
| SRC-01 | Caller progress hợp lệ; caller sai/chữ ký sai | Đúng allowlist; 401/403; không đọc DB chéo | Source PASS trong evidence; consumer chờ Huy |
| SRC-02 | Learner ACTIVE và BLOCKED; có/không setup | Cohort gồm mọi LEARNER status; setup null đúng schema | Source PASS; consumer chờ Huy |
| SRC-03 | Hai Course/Level, lọc và phân trang nhiều trang | Global count độc lập filter; không rơi/lặp Learner khi đọc tập fixture ổn định | Source PASS; consumer chờ Huy |
| SRC-04 | Course có Lesson published và draft/hidden | Denominator gồm tất cả Lesson của Course | Source PASS; consumer chờ Huy |
| PROG-01 | Submit Exercise thật, kể cả 0 điểm; retake với key mới | Event thật tạo History; completedLessons chỉ tăng một lần; Result/History retake giữ riêng | Có evidence luồng hẹp; #35 còn chờ acceptance đầy đủ |
| PROG-02 | Đổi Course/Level sau khi có Result/Completion | Setup mới được áp dụng; lịch sử giữ snapshot Course/Level cũ | Chờ consumer #35 |
| PROG-03 | No setup/no Lesson, owner-scoped History paging | Null/empty theo public contract, không đọc chéo Learner | Chờ consumer #35 |
| STAT-01 | ACTIVE/BLOCKED ở các Course/Level và Learner chưa chọn setup | totalLearners toàn hệ thống; currentLearnerCount theo cohort hiện tại | BLOCKED: chưa có Statistics implementation tại SHA owner trên |
| STAT-02 | Cohort có Learner 0 completion; Course gồm Lesson draft | averageProgressPercent gồm Learner 0, denominator gồm draft; không cohort/không Lesson thì null | BLOCKED #36 |
| STAT-03 | Exercise retake, completion đầu tiên rồi đổi setup | completedLessonCount theo unique first-completion snapshot; không cộng lại retake | BLOCKED #36 |
| STAT-04 | EXERCISE/QUIZ/TEST có retake và TIMEOUT; có Learner chưa làm | attemptCounts gồm toàn Results hợp lệ; averageQuizTestScorePercent dùng mọi QUIZ/TEST Result, không chọn best/latest; không Result thì null | BLOCKED #36 |
| STAT-05 | Không filter, Course, Course+Level, Level thiếu/sai Course, UUID sai/ID không tồn tại | Schema/mã lỗi theo public v0.1; levelId cần courseId và thuộc Course; global count không đổi theo filter | BLOCKED #36 |
| DEP-01 | Dừng lần lượt User/Content hoặc DB nguồn trong TEST | Consumer cần nguồn trả 503; không trả 200 với metric giả 0 | Progress có evidence hẹp; Statistics BLOCKED |
| DEP-02 | Khôi phục nguồn; đọc lại consumer | Dữ liệu phục hồi đúng; generatedAt hợp lệ, syncState EVENTUAL | Statistics BLOCKED; chờ acceptance #35/#36 |

Huy tạo fixture bằng API owner và event thật trong TEST; ghi IDs và phép tính
từng metric trước khi so actual. Dùng số đếm nền trước/sau nếu môi trường có
nhiều Learner; không mặc định totalLearners bằng số tài khoản vừa tạo. Dữ liệu
Result/History/Completion không seed SQL xuyên database.

## Gói bằng chứng đầu vào và điều kiện đóng

Huy cung cấp consumer commit SHA, image ID/digest nếu dùng Docker, thời gian,
project/env, DB/migration, fixture IDs, lệnh/request đã loại credential,
expected/actual và trạng thái mỗi case. Review semantics phải ghi đúng người và
phạm vi. Khoa đối chiếu source compatibility, sửa lỗi User/Content/Gateway nếu
có rồi chạy lại ca bị ảnh hưởng. Chỉ đóng #31 sau consumer acceptance và review
thực tế; Huy có thể tiếp tục #35/#36 với API nguồn đã sẵn trong lúc #31 Open.

- [#31](https://github.com/KhoaTryHard/english-learning-platform/issues/31),
  [#35](https://github.com/KhoaTryHard/english-learning-platform/issues/35),
  [#36](https://github.com/KhoaTryHard/english-learning-platform/issues/36).
- [Review nguồn W7-05](../reviews/2026-11-09-khoa-w7-05.md),
  [manifest](../evidence/khoa-w7-05-2026-11-09/manifest.json),
  [mẫu HTTP đã loại credential](../evidence/khoa-w7-05-2026-11-09/source-samples.json).
- [Regression W8](../evidence/khoa-w8-41-2026-11-09/manifest.json),
  [runbook](../runbooks/khoa-w9.md).

Ước lượng cũ giữ là kế hoạch, không phải giờ còn lại hoặc actual. Production
HOLD W10. Bằng chứng Kafka luồng hẹp không thay broker outage/replay/DLQ/crash
recovery hoặc sáu metric Statistics acceptance.
