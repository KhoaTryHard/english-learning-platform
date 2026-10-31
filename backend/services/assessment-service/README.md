# Assessment Service — Huy

Triển khai W5-02/#4 và Exercise thật #25: Question/readiness, Attempt, snapshot,
save/resume/submit/Result, deadline Quiz/Test và transactional Outbox.
Huy đã chọn PostgreSQL 17 trong phiên triển khai. Java 25/Spring Boot 4.0.8
khớp nhánh backend Khoa tại `bf183461`.

Service build độc lập; Kafka relay/consumer và Progress chưa nằm trong phần này.

## Kiểm thử

Chạy từ thư mục service với JDK 25:

```powershell
.\mvnw.cmd -B verify
```

Mặc định dùng H2 PostgreSQL mode trong test; production không phụ thuộc H2.
Để chạy cùng bộ transaction/HTTP tests với PostgreSQL 17, tạo database test
riêng tên `assessment_test` trên localhost:

```powershell
$env:ASSESSMENT_TEST_DB_PASSWORD = '<test-password>'
.\mvnw.cmd -B verify '-Dassessment.test.jdbc-url=jdbc:postgresql://127.0.0.1:5543/assessment_test' '-Dassessment.test.db-user=assessment'
```

Các test xóa dữ liệu trong bảng Assessment của database test trước mỗi case.
Không trỏ vào database đang sử dụng. Flyway V1 chạy thật. Owner APIs là stub;
`AssessmentApplicationTest` kiểm tra Spring Boot/Flyway riêng bằng H2.

## Chạy local

Chuẩn bị PostgreSQL 17/database `assessment_db`, User và Content của Khoa.
Tạo key riêng cho Assessment:

```powershell
java scripts/GenerateKey.java .secrets
```

Chuyển **assessment.public.pem** cho Khoa để User/Content tin caller `assessment`.
Nhận public key `api` từ Khoa, đặt trong thư mục public keys. Không chuyển
private key cho service khác hoặc commit `.secrets`.

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5543/assessment_db'
$env:DB_USER = 'assessment'
$env:DB_PASSWORD = '<local-password>'
$env:PLATFORM_KEYS = '<absolute-path-to-public-keys>'
$env:PRIVATE_KEY_FILE = (Resolve-Path .secrets/assessment.private.pem).Path
$env:USER_URL = 'http://localhost:8081'
$env:CONTENT_URL = 'http://localhost:8082'
.\mvnw.cmd -B spring-boot:run
```

Port mặc định `8090`, health `GET /actuator/health`. Flyway tự migrate database
của service khi khởi động. Không dùng auto-DDL hoặc truy cập database khác.

## Docker Compose độc lập

Copy `.env.example` thành `.env`, điền password và đường dẫn keys local.
`PUBLIC_KEYS_DIR` chỉ chứa public keys; `PRIVATE_KEY_FILE` là key của Assessment.

```powershell
docker compose config
docker compose up --build -d
docker compose logs assessment
docker compose stop
```

Database/service chỉ publish port trên `127.0.0.1`. Volume `assessment_data`
giữ dữ liệu khi stop. Trên Docker Desktop, User/Content ở host được truy cập qua
`host.docker.internal`. Khi ghép Compose chung, thay bằng DNS các service tương
ứng và để Gateway gọi `http://assessment:8090`.

## Seed qua Gateway

Seed User/Course/Level/Module/Lesson tại owner trước. Lesson có thể còn draft;
Admin cấu hình Exercise xong mới publish Lesson qua Content.

Qua Gateway với phiên ADMIN và CSRF hợp lệ:

1. `POST /api/v1/admin/assessments` với JSON dưới đây, thay UUID bằng dữ liệu thật.
2. Lưu `assessmentId`; `POST /api/v1/admin/assessments/{assessmentId}/questions`
   với JSON Question dưới đây. Lưu `questionId` từ response.
3. `PATCH /api/v1/admin/assessments/{assessmentId}` với `{"ready":true}`.
4. Khoa gọi readiness bằng service identity `content`, rồi publish Lesson.
5. Learner có setup đúng Course/Level, start với `Idempotency-Key` UUID mới,
   save answers, chờ ACK rồi submit không body.

AssessmentCreate:

```json
{"type":"EXERCISE","courseId":"<Course UUID>","lessonId":"<Lesson UUID>","title":"Exercise demo","timeLimitSeconds":null}
```

QuestionWrite:

```json
{"questionType":"TRUE_FALSE","content":"False is a valid answer","points":2,"correctAnswer":false}
```

AnswersWrite (kết quả demo là 100%):

```json
{"answers":[{"questionId":"<Question UUID>","questionType":"TRUE_FALSE","value":false}]}
```

Không seed bằng ghi SQL sang User/Content. Kiểm tra danh sách Admin trước khi
seed lại để tránh tạo trùng Exercise. Mapping V1 có một Exercise cho mỗi Lesson.

## API đã triển khai

- Admin: list/create/get/patch Assessment; create/replace Question.
- Learner: list/get Assessment, get Lesson Exercise.
- Start attempt, get/resume attempt, PUT toàn bộ answers, submit.
- Get Result, list own Attempts/Results với paging/filter theo approved API.
- Internal: `GET /internal/v1/lessons/{lessonId}/exercise-readiness`, chỉ cho
  caller `content`. Endpoint chỉ đọc Assessment DB, không callback Content.

Ngoài health, các route cần signed service JWT. Public owner routes cần thêm
`X-Gateway-Principal`, kiểm audience/method/path/role/session ID. Gateway/User
xác minh JWT cookie và CSRF trước khi ký assertion; browser không gọi owner
trực tiếp. Không có profile bỏ qua authentication.

Adapter sử dụng cơ chế bàn giao của Khoa. Internal v0.1.1 vẫn là proposal chờ
quyết định nhóm #42; implementation này không thay approved v0.1.

## Consistency và bàn giao

- Start key giữ theo user/operation/request cùng vòng đời attempt; retry giữ
  attempt, retake dùng key mới.
- Snapshot giữ question/key/points/time/Course/Level; Learner không nhận answer
  key. Sửa Question không đổi attempt cũ.
- Exercise không timer; Quiz/Test dùng server deadline. Finalizer chạy định kỳ,
  GET/save/submit cũng kiểm tra hết hạn; resume không reset deadline.
- Save thay toàn bộ answers; client serialize saves và chờ ACK. Contract chưa
  có revision để chống stale writes giữa nhiều tab.
- Submit/finalizer khóa cùng submission; Result unique submission. Result,
  Outbox và trạng thái submission commit trong cùng transaction.
- Outbox topic `assessment.completed.v1`, key=userId; hiện giữ `PENDING`.
  Chưa publish Kafka, chưa ghi Progress, chưa chứng minh full E2E của nhóm.
- JSON lưu dạng TEXT; xem `docs/architecture/assessment-data-mapping.md` trong
  repository để review mapping với W3-03.

Lưu command/SHA/environment/result khi chạy thủ công. Peer review và tích hợp
Gateway/User/Content thật được theo dõi riêng ở #4/#20/#25/#42.
