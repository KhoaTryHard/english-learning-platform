# Progress Service — Huy, W5-02

Foundation BE-P1/P2/K2 cho #4/#16/#17: History, Completion, Progress/Dashboard APIs,
ProcessedEvent và Kafka consumer idempotent. PostgreSQL 17 triển khai theo hướng
Assessment; lựa chọn/mapping của Huy chờ review chung #20. Java 25/Spring Boot 4.0.8.

## Build và test độc lập

```powershell
.\mvnw.cmd -B verify
```

Mặc định test dùng H2 PostgreSQL mode. Để chạy transaction/HTTP/listener tests
trên PostgreSQL thật, tạo database test riêng tên `progress_test` trên localhost:

```powershell
$env:PROGRESS_TEST_DB_PASSWORD = '<test-password>'
.\mvnw.cmd -B verify '-Dprogress.test.jdbc-url=jdbc:postgresql://127.0.0.1:5544/progress_test' '-Dprogress.test.db-user=progress'
```

Các test xóa dữ liệu trong bảng Progress của database test trước mỗi case;
không trỏ vào database sử dụng. Boot/wiring tests dùng H2 riêng. Kafka record,
ACK và owner sources trong test là test doubles, không phải broker/Gateway LIVE.

## Chạy local

Chuẩn bị database `progress_db` của riêng Progress. Tạo key chỉ cho service này:

```powershell
java scripts/GenerateKey.java .secrets
```

Chuyển `progress.public.pem` cho Khoa; nhận `api.public.pem` của Gateway.
Chỉ mount public keys và private key của chính Progress. User/Content phải cho
phép caller `progress` đọc setup/Course context theo approved internal API.

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5544/progress_db'
$env:DB_USER = 'progress'
$env:DB_PASSWORD = '<local-password>'
$env:PLATFORM_KEYS = '<absolute-public-key-directory>'
$env:PRIVATE_KEY_FILE = (Resolve-Path .secrets/progress.private.pem).Path
$env:USER_URL = 'http://localhost:8081'
$env:CONTENT_URL = 'http://localhost:8082'
.\mvnw.cmd -B spring-boot:run
```

Port mặc định 8091; health `/actuator/health`; Flyway tự migrate Progress DB.
Kafka mặc định tắt. Để consume thật, bật `KAFKA_ENABLED=true` và đặt
`KAFKA_BOOTSTRAP_SERVERS` theo [hướng dẫn broker](../../../infra/kafka/README.md).

Docker: copy `.env.example` thành `.env`, điền password/keys rồi chạy
`docker compose up --build -d`. Stop giữ volume `progress_data`.

## API theo approved v0.1

- `GET /api/v1/me/progress?courseId=<UUID>`: denominator từ Content gồm tất cả
  persisted Lessons, không chỉ published. Course rỗng trả null; source lỗi 503.
- `GET /api/v1/me/history?page=0&size=20&courseId=<UUID>&type=EXERCISE`: lọc tùy
  chọn, chỉ lịch sử principal, completedAt giảm dần + historyId ổn định.
- `GET /api/v1/me/dashboard`: User setup + own Progress + tối đa 5 recentHistory;
  chưa setup thì progress=null. Không thêm Goal/recommendation.
- `GET /api/v1/admin/statistics?courseId=<UUID>&levelId=<UUID>` (W8-02/#36, ADMIN):
  sáu nhóm chỉ số DD-11. `levelId` phải kèm `courseId` và thuộc Course đó, nếu
  không trả 400; `courseId` không tồn tại trả 404. Không có bộ lọc thời gian.

Owner routes yêu cầu signed service JWT của Gateway và assertion audience
progress/method/path/role/session. Không có HTTP endpoint nhận event hoặc tự seed
History. Dữ liệu projection chỉ tạo từ AssessmentCompleted hợp lệ.

## Consumer/consistency

Schema máy đọc là bản sao approved event giữ nguyên bytes. Validator bổ sung
UUID/time/UTC, key=userId, earned<=max và immutable references trong projection.
Assessment chịu trách nhiệm xác minh nguồn Course/Level/Lesson khi start; broker
ACL là boundary tin cậy producer, không dùng schema như bằng chứng nguồn tồn tại.

ProcessedEvent + History + Exercise-only Completion + count projection commit
cùng transaction, rồi mới ACK Kafka. EventId trùng cùng payload là no-op;
submission trùng cùng payload không nhân History. Payload conflict rollback.
Retake có History mới, Completion vẫn unique(userId,lessonId). Quiz/Test không
complete Lesson; Exercise điểm 0 vẫn complete.

Event đến lệch thứ tự giữ first completion sớm nhất theo completedAt, tie-break
submissionId. Thay setup không gán lại snapshots cũ. Consumer retry 1/5/30 giây,
DLT/replay giữ eventId/key/payload. Gửi DLT lỗi thì không commit offset.

## Statistics — sáu nhóm chỉ số (W8-02/#36)

Progress sở hữu shape công bố nhưng không sở hữu input nào. Nguồn dùng internal
auth với caller `progress`:

- User `GET /internal/v1/learners/cohort` — cohort theo Course/Level hiện tại và
  `totalLearnersGlobal` (mọi LEARNER, kể cả BLOCKED, bỏ qua filter).
- Content `GET /internal/v1/courses/contexts` — mọi Course persisted, kể cả
  Course chưa publish / không có Lesson / không có Learner; `totalLessons` là
  denominator.
- Assessment `GET /internal/v1/statistics/assessment-results` — `attemptCounts`
  mọi Result kể cả retake và TIMEOUT, cùng `quizTestScoreSum`/`quizTestResultCount`.

Sáu nhóm: `totalLearners`; `currentLearnerCount`; `averageProgressPercent`;
`completedLessonCount`; `attemptCounts`; `averageQuizTestScorePercent`.

Quy tắc đã triển khai:

- Average không có denominator trả **null**, không trả 0 giả. Cohort rỗng hoặc
  Course không có Lesson => `averageProgressPercent` null; không có Quiz/Test
  Result => `averageQuizTestScorePercent` null.
- `averageProgressPercent` tính trên toàn cohort, learner chưa hoàn thành Lesson
  nào vẫn vào mẫu với 0; denominator luôn là **toàn bộ Lesson của Course**, kể cả
  khi lọc theo Level.
- `completedLessonCount` đếm first completion theo snapshot Course/Level, nên
  retake không cộng trùng.
- Tỉ lệ Quiz/Test cộng dồn **chưa làm tròn** ở Assessment rồi Progress chia và
  làm tròn 2 số một lần duy nhất.
- Bất kỳ nguồn bắt buộc nào lỗi/sai shape đều trả **503**, không che bằng 0.
- Không có bộ lọc thời gian theo hợp đồng.

Điểm cần Khoa xác nhận: `quizTestScoreSum` được triển khai là **tổng scorePercent
chưa làm tròn** (earned/max × 100). Hợp đồng ghi "unrounded point ratio" nên ratio
0..1 cũng đọc được; hai service của Huy dùng cùng một quy ước nên số liệu nhất
quán, nhưng cần chốt trước khi service khác đọc endpoint này.

Giới hạn còn lại: cohort nhiều trang không nằm trong một transaction toàn cục —
hợp đồng đã ghi đây là open review item; Progress lấy `totalLearnersGlobal` từ
trang đầu. Bảng tổng hợp đọc trực tiếp từ `lesson_completion`, chưa có cache.

Đã áp cùng fix #44 cho Progress: `CannotCreateTransactionException` không kế thừa
`DataAccessException`, nên DB không mở được connection từng trả 500. Statistics bắt
buộc trả 503 khi dependency không sẵn, nên `core/Errors.java` nay xử lý cả hai;
`TransactionUnavailableHttpTest` giữ regression trên đúng route `/admin/statistics`.

Chưa có broker E2E hoặc nghiệm thu Gateway/User/Content thật. Repair/reconciliation
đầy đủ tiếp tục #31. Xem `docs/architecture/progress-data-mapping.md` trong
repository để review W3-03.
