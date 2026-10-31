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

Chưa có broker E2E hoặc nghiệm thu Gateway/User/Content thật. Sáu metric Statistics,
source APIs và repair/reconciliation đầy đủ tiếp tục #31/#34/#35/#36. Xem
`docs/architecture/progress-data-mapping.md` trong repository để review W3-03.
