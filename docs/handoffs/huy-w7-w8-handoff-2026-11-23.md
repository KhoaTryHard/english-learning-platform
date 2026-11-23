# Bàn giao phần Huy — W7-06, W8-01, W8-02 và tích hợp fix #44

**Owner:** Nguyễn Quang Huy · **Ngày:** 23/11/2026 (UTC+7) · **Refs:** #30, #34, #35, #36, #44
**Trello:** W7-06, W8-01, W8-02 (đang ở Doing)
**Bằng chứng:** `docs/evidence/huy-w7-w8-delivery-2026-11-23.json`,
`docs/evidence/huy-service-layout-2026-11-23.json`

Đây là bàn giao kỹ thuật của một owner, **không phải nghiệm thu nhóm**. Checkbox
acceptance trên Trello giữ nguyên chưa tick cho đến khi có reviewer khác tác giả
và môi trường LIVE.

## 1. Base của bản bàn giao

Xây trực tiếp trên **PR #43 head `c1a2a07`** (nhánh `feature/huy-assessment-foundation`),
không phải trên snapshot `develop`. Lý do: trong snapshot `develop`,
`backend/services/assessment-service` và `backend/services/progress-service`
**chỉ có `.gitkeep`** — code W5-02 chưa bao giờ vào develop.

Đã tích hợp luôn **fix #44 `413a929`** (nhánh `fix/khoa-assessment-db-503`), nằm
ngay trên `c1a2a07`. Việc Khoa ghi nhận còn mở — *"Huy chưa review 44/tích hợp fix
vào 43"* — nay đã xong (mục 4).

Hai service vẫn **build độc lập**: không thêm module vào root `pom.xml`, không phụ
thuộc `platform-core` hay service của thành viên khác.

## 2. Đối chiếu nhiệm vụ ↔ kết quả

| Nhiệm vụ | Yêu cầu trưởng nhóm | Trạng thái trước | File đã thêm/sửa | Kết quả | Kiểm thử | Mức đáp ứng |
|---|---|---|---|---|---|---|
| **W7-06** Kafka + Outbox relay (#30) | Relay publish **ngoài transaction**, giữ DB; retry/recovery đúng trạng thái; publish lại giữ `eventId`; broker down/restart/crash không mất Result/event | PR #43 đã có relay nhưng `runBatch` mở transaction; checklist 0/4, chưa nghiệm thu | `OutboxRelay.java`, `OutboxKafkaConfiguration.java` (sửa), `OutboxRelayTest.java` (mới), `application.properties`, `.env.example`, `compose.yaml`, `README.md` | Relay publish sau commit, không giữ row lock/connection; lỗi giữ `PENDING` + `retry_count` + `last_error` có chẩn đoán thật; crash sau publish phát lại đúng `eventId`; lỗi một key không nghẽn key khác | 7 test mới + **3 test relay gốc của PR #43 giữ nguyên và vẫn PASS** | **Code + self-test đạt.** Broker thật `NOT_RUN` → acceptance chưa đạt |
| **W8-01** Progress/History/consumer (#34/#35) | Consumer + completion không cộng trùng; API cho FE; test quyền/phân trang/đổi Course-Level | Có từ W5-02, chưa verify lại | Không sửa logic consumer | Consumer + `/me/progress`, `/me/history`, `/me/dashboard` đúng; retake không cộng trùng completion | 26 test cũ PASS + 13 test mới đi qua đúng đường consumer | **Code đạt.** LIVE qua Gateway `NOT_RUN` |
| **W8-02** Statistics API (#36) | 6 nhóm chỉ số + bộ lọc Course/Level qua owner APIs; kiểm null, lỗi nguồn, dữ liệu làm lại | **GET 404 — chưa có** (Khoa ghi tại `413a929`, 10/11) | `StatisticsService.java`, `StatisticsController.java` (mới), `ProgressSources.java`, `AssessmentService.metrics`, `AssessmentController`, `core/ServiceGuard.java` (Assessment), + 2 file test mới | `GET /api/v1/admin/statistics` đúng schema `Statistics`/`CourseStatistics`; nguồn lỗi → 503; average không có denominator → null | 8 + 5 + 7 + 1 = 21 test mới PASS | **Code + self-test đạt.** Cần Khoa đối chiếu nguồn + Hưng ghép UI |
| **#44** Fix 503 khi không mở được transaction | Khoa yêu cầu Huy review và tích hợp vào #43 | Chưa review | `core/Errors.java` (cả 2 service), `TransactionUnavailableHttpTest.java` (×2) | Đã tích hợp cho Assessment, áp cùng fix cho Progress | 4 + 4 test PASS | **Đạt.** Vẫn cần Khoa xác nhận phần mở rộng sang Progress |

## 3. Hợp nhất `OutboxRelay` — đã giải quyết, không còn conflict

PR #43 đã có `OutboxRelay.java`, `EventPublisher.java`, `OutboxKafkaConfiguration.java`
và `OutboxKafkaWiringTest.java`. Bản bàn giao này **merge lên thiết kế của PR #43**,
không thay thế nó.

**Giữ từ PR #43:**

- `EventPublisher` làm transport abstraction — test không cần subclass `KafkaTemplate`
- `OutboxKafkaConfiguration` wiring theo `assessment.kafka.enabled` + `RelayJob` scheduled
- Chữ ký `public int runBatch(int limit)` và validation limit 1..100
- **Cả 3 test relay gốc giữ nguyên văn và vẫn PASS** — đây là bằng chứng bản merge
  tương thích hành vi

**Thay đổi so với PR #43, kèm lý do:**

| Thay đổi | Lý do | Đánh đổi |
|---|---|---|
| Bỏ `@Transactional` khỏi `runBatch`, bỏ `FOR UPDATE SKIP LOCKED` | W7-06 yêu cầu relay publish **ngoài transaction**. Ở PR #43, lời gọi broker nằm trong transaction đang mở, giữ row lock và connection pool tới `batch-size × send-timeout`; lỗi DB giữa batch làm mất luôn bookkeeping `retry_count` của cả batch | Không còn row lock nên hai instance relay có thể publish trùng. An toàn vì delivery là at-least-once và Progress chống trùng bằng `processed_event` |
| Update `SENT`/retry có guard `AND status='PENDING'` | Idempotent; số trả về đúng bằng số lần chuyển PENDING→SENT thật | — |
| `last_error` ghi loại lỗi + message thật thay cho hằng `'PUBLISH_FAILED'` | W7-06 đòi bằng chứng retry/recovery; một cờ hằng không có giá trị chẩn đoán | Cắt còn 1000 ký tự |
| Lỗi publish chỉ chặn các event **cùng partition key**, key khác tiếp tục | PR #43 dùng `continue` nên có thể publish event sau của **cùng** user trước event lỗi → sai thứ tự theo key. Dùng `break` thì một event không gửi được sẽ nghẽn toàn bộ outbox. Skip theo key giữ đúng mức thứ tự Kafka bảo đảm mà không nghẽn toàn hệ | — |
| Thêm `backlogAgeSeconds()`; `relay-batch-size` / `send-timeout-seconds` cấu hình qua `assessment.kafka.*` | Quan sát backlog và tinh chỉnh sweep không cần build lại | — |

→ **Khoa cần review đúng một điểm:** publish ngoài transaction (bản này) so với
transactional sweep + `SKIP LOCKED` (PR #43).

## 4. Fix #44 — đã tích hợp, và mở rộng sang Progress

**Lỗi:** `CannotCreateTransactionException` **không** kế thừa `DataAccessException`,
nên khi DB không mở được connection trước lúc method transactional lấy connection,
response là **500 INTERNAL_ERROR** thay vì **503 DEPENDENCY_UNAVAILABLE**.

- **Assessment:** lấy nguyên văn thay đổi `core/Errors.java` và
  `TransactionUnavailableHttpTest` của Khoa từ `413a929`.
- **Progress:** bản copy `core/Errors.java` của Progress **có cùng lỗi**. Hợp đồng
  Statistics bắt buộc trả 503 khi dependency không sẵn, nên tôi áp cùng fix và viết
  test regression tương ứng trên đúng route `/api/v1/admin/statistics`. Test cũng
  kiểm response **không rò** chi tiết kết nối (`jdbc:...`, host, `SQLException`).

## 5. API đã hoàn thành

**Progress (public, ADMIN):**

```
GET /api/v1/admin/statistics?courseId=<UUID>&levelId=<UUID>
```

Sáu nhóm chỉ số DD-11: `totalLearners`, và theo từng Course:
`currentLearnerCount`, `averageProgressPercent`, `completedLessonCount`,
`attemptCounts{EXERCISE,QUIZ,TEST}`, `averageQuizTestScorePercent`.

- `levelId` phải kèm `courseId` và phải thuộc Course đó → nếu không: **400**
- `courseId` không tồn tại → **404**; nguồn bắt buộc lỗi → **503** (không trả 0 giả)
- Không có bộ lọc thời gian (đúng hợp đồng)
- Average không có denominator → **null** (cohort rỗng, Course không Lesson, không có Quiz/Test Result)
- `averageProgressPercent`: learner chưa hoàn thành Lesson nào vẫn vào mẫu với 0;
  denominator luôn là **toàn bộ Lesson của Course**, kể cả khi lọc Level
- `completedLessonCount`: đếm first completion theo snapshot → **retake không cộng trùng**
- Tỉ lệ Quiz/Test cộng dồn chưa làm tròn ở Assessment, Progress chia và làm tròn 2 số **một lần**

**Assessment (internal, chỉ caller `progress`):**

```
GET /internal/v1/statistics/assessment-results?courseId=<UUID>&levelId=<UUID>
```

Đếm mọi Result đã lưu theo loại, **kể cả retake và TIMEOUT**, nhóm theo Course/Level
trong snapshot lúc start. Không có `levelId` thì mọi Level của Course gộp về một dòng
`levelId: null`. Chỉ đọc DB của Assessment, không callback service khác.

## 6. Nguồn dữ liệu đang dùng (đều đã có sẵn từ Khoa)

| Nguồn | Endpoint | Dùng cho |
|---|---|---|
| User | `GET /internal/v1/learners/cohort` | `totalLearners`, `currentLearnerCount`, danh sách cohort |
| Content | `GET /internal/v1/courses/contexts` | danh sách Course + `totalLessons` (denominator) |
| Assessment | `GET /internal/v1/statistics/assessment-results` | `attemptCounts`, điểm Quiz/Test |

**Cần Khoa cấp thêm:** thư mục public keys của Assessment phải có
`progress.public.pem` để Assessment tin caller `progress`.

**Biến môi trường mới:** `ASSESSMENT_URL` (Progress); `RELAY_DELAY_MS`,
`RELAY_BATCH_SIZE`, `RELAY_SEND_TIMEOUT_SECONDS` (Assessment).
`KAFKA_ENABLED` mặc định `false` nên bật relay là quyết định có ý thức.

## 7. Cách chạy và kiểm thử

Từ thư mục từng service, JDK 25:

```powershell
.\mvnw.cmd -B verify
```

Chạy thêm trên PostgreSQL 17 thật (database test riêng, không trỏ vào DB đang dùng):

```powershell
$env:ASSESSMENT_TEST_DB_PASSWORD = '<test-password>'
.\mvnw.cmd -B verify '-Dassessment.test.jdbc-url=jdbc:postgresql://127.0.0.1:5543/assessment_test' '-Dassessment.test.db-user=assessment'
```

```powershell
$env:PROGRESS_TEST_DB_PASSWORD = '<test-password>'
.\mvnw.cmd -B verify '-Dprogress.test.jdbc-url=jdbc:postgresql://127.0.0.1:5544/progress_test' '-Dprogress.test.db-user=progress'
```

Kiểm tra hai service vẫn standalone và jar chỉ chứa package của chính nó:

```powershell
python scripts/verify-huy-services.py --build --report docs/evidence/huy-service-layout-2026-11-23.json
```

## 8. Kết quả kiểm thử thực tế

| Suite | DB | Tests | Kết quả |
|---|---|---|---|
| assessment-service | H2 PostgreSQL mode | **50** (base PR #43: 31) | PASS |
| assessment-service | PostgreSQL 17.11 | **50** | PASS |
| progress-service | H2 PostgreSQL mode | **43** (base PR #43: 26) | PASS |
| progress-service | PostgreSQL 17.11 | **43** | PASS |
| verify-huy-services --build | — | 2 service | PASS (isolated build, owner-only jar) |

Tổng **93 test**, 0 failure, 0 error. Flyway V1 đã áp dụng thật lên cluster
PostgreSQL 17.11; bảng được kiểm lại qua `information_schema`.

Môi trường: Windows 11, JDK 25.0.3, Maven 3.9.11 (offline, repo local),
PostgreSQL 17.11 tại `127.0.0.1:15543`.

## 9. NOT_RUN — nói rõ phần chưa chạy

| Hạng mục | Trạng thái | Lý do |
|---|---|---|
| Kafka broker E2E (Outbox → broker → consumer Progress) | **NOT_RUN** | Không có broker, không có Docker runtime. Thay bằng recording `EventPublisher`: publish OK, broker down có `last_error` chẩn đoán, retry nhiều lần, crash publish lại giữ `eventId`, cô lập lỗi theo partition key, giới hạn batch, batch size không hợp lệ |
| LIVE qua Gateway/User/Content với seed thật | **NOT_RUN** | Stack test của Khoa đã stop; chưa trao keys/seed trong phiên này. ServiceGuard vẫn chạy ký/verify RS256 thật |
| `scripts/check-contracts.py` | **NOT_RUN** | Thiếu PyYAML, môi trường offline |
| `scripts/check-service-layout.py` | **NOT_APPLICABLE** | `SERVICES` chỉ gồm user/content/api. Luật tương đương đã chạy cho 2 service của Huy bằng `verify-huy-services.py`; thêm 2 folder vào guard của Khoa là quyết định của Khoa |
| Docker Compose runtime | **NOT_RUN** | Không có Docker CLI |
| Peer review | **NOT_RUN** | Tác giả không tự approve |

## 10. Điểm cần chốt với nhóm

1. **Relay publish ngoài transaction** (mục 3) — Khoa review đánh đổi row lock.
2. **`quizTestScoreSum`** đang là tổng `scorePercent` chưa làm tròn (`earned/max*100`).
   Hợp đồng ghi "unrounded point ratio" nên ratio 0..1 cũng đọc được. Hai service của
   Huy dùng cùng quy ước nên số liệu nhất quán, nhưng cần Khoa chốt trước khi có
   caller khác đọc endpoint.
3. **`averageProgressPercent`** giữ denominator là toàn bộ Lesson của Course kể cả khi
   lọc Level, theo câu "all Course Lessons" trong hợp đồng. Nếu nhóm hiểu là Lesson
   của riêng Level đó thì công thức phải đổi.
4. **Mở rộng fix #44 sang Progress** — Khoa xác nhận vì `core/Errors.java` là bản copy
   của cùng một lớp dùng chung.
5. **Cohort nhiều trang** không nằm trong một transaction toàn cục;
   `totalLearnersGlobal` lấy từ trang đầu. Hợp đồng internal đã ghi đây là open
   review item — chưa tự coi là đã giải quyết.
6. **#20** (đối chiếu W3-03 data dictionary với approved API/DD) vẫn **Open**, assignee
   Huy1188, 0 comment — bàn giao này không thay đổi.
   **#42 đã Closed** (Khoa đóng 09/11); internal auth đã `BASELINED_0.1.1`. Nhưng
   `docs/contracts/internal-auth-decision-2026-11-01.md` ghi rõ: *"Không có review event
   dưới tài khoản Huy trên GitHub; hồ sơ ghi nguồn là Huy phản hồi, Khoa chuyển lại"* —
   nên phần xác nhận của Huy vẫn là lời chuyển lại, chưa phải dấu vết của chính Huy.
8. **PR #44 vẫn Open + Draft và target `feature/huy-assessment-foundation`**, tức là PR
   vào nhánh của chính Huy. Tài liệu quyết định ghi *"chưa gán peer approval cũ của PR #1
   thành approval cho bản sửa này"* → review/merge #44 vẫn là việc của Huy trên GitHub,
   bản tích hợp local ở mục 4 không thay được điều đó.
7. Delivery là **at-least-once**: relay có thể publish lại cùng `eventId`;
   Progress chống trùng bằng `processed_event`. Không coi producer PASS là Progress E2E.

## 10b. Trạng thái GitHub issue của Huy (đọc ngày 23/11/2026)

| Issue | Tiêu đề | State | Phần bàn giao này phủ |
|---|---|---|---|
| #4 | W5-02 nền tảng Assessment/Progress/Kafka | Open | Code có đủ; chờ merge + peer review |
| #15 | [Theo dõi BE-A1→A4] Assessment/Attempt/Result/deadline | Open | Đã có từ PR #43 |
| #16 | [Theo dõi BE-K1/K2/P1] Outbox → Kafka → Progress idempotent | Open | Relay + consumer có; broker E2E `NOT_RUN` |
| #17 | [Theo dõi BE-P2/Architecture] Progress/History/Statistics | Open | **Statistics nay đã có** |
| #20 | Đối chiếu W3-03 data dictionary với approved API/DD | Open | **Không phủ** — cần Huy quyết |
| #25 | W6-07 Assessment thật: Question/readiness/Exercise → Result | **Closed** 03/11 | — |
| #27 | W7-01 Quiz/Test, server deadline, resume, chấm điểm | Open | Đã có từ PR #43 |
| #30 | W7-04 Kafka broker/topic và Outbox relay | Open | Relay xong; broker/topic thật `NOT_RUN` |
| #34 | W8-01 Consumer idempotent và LessonCompletion/Progress | Open | Code có; LIVE `NOT_RUN` |
| #35 | W8-02 Progress, History và Learner Dashboard APIs | Open | Code có; LIVE `NOT_RUN` |
| #36 | W8-03 Sáu nhóm Statistics qua owner APIs và filter | Open | **Nay đã triển khai** |

Cả 10 issue Open của Huy đều có **0 comment** — chưa có lần cập nhật tiến độ nào từ phía
Huy trên GitHub. Đây là lý do trưởng nhóm không thấy được phần đã làm.

## 11. Việc tiếp theo của Huy (W9-02)

Khi có môi trường/keys/seed: chạy completion không trùng, History, snapshot đổi
Course/Level, Kafka retry/replay, sáu nhóm Statistics và đối chiếu số liệu tính tay
theo ma trận W9-T02. Mỗi lượt ghi SHA/env/seed/command/expected/actual.

## 12. Gợi ý Git

**Thứ tự quan trọng.** PR #44 target `feature/huy-assessment-foundation`, nên nếu merge
#44 sau khi đã push nhánh này thì phần `core/Errors.java` + `TransactionUnavailableHttpTest`
của Assessment sẽ trùng/đụng. Làm theo thứ tự:

1. Review và merge **PR #44** vào `feature/huy-assessment-foundation` (Huy là owner nhánh).
2. Tạo nhánh mới **từ nhánh đã cập nhật**, không base trên `develop` (develop chưa có code
   của Huy):

```powershell
git switch feature/huy-assessment-foundation
git pull
git switch -c feature/huy-w7-w8-statistics-relay
```

3. Khi đó **bỏ** hai file Assessment đã có sẵn nhờ #44
   (`core/Errors.java`, `TransactionUnavailableHttpTest.java`) khỏi changeset, chỉ giữ phần
   mở rộng fix sang **Progress** và các thay đổi còn lại.

Nếu merge #44 muộn hơn thì ngược lại: giữ nguyên changeset này và giải conflict ở đúng hai
file đó.

Commit:

```
feat(assessment,progress): outbox relay ngoai transaction va sau nhom Statistics

- Assessment: OutboxRelay publish sau commit, bo @Transactional de broker treo
  khong giu row lock/connection; last_error ghi chan doan that; loi mot partition
  key khong nghen key khac; them backlogAgeSeconds va cau hinh batch/timeout.
  Giu EventPublisher/OutboxKafkaConfiguration va ca 3 test relay cua PR #43.
- Assessment: them GET /internal/v1/statistics/assessment-results chi cho caller
  progress, dem moi Result ke ca retake va TIMEOUT.
- Progress: them GET /api/v1/admin/statistics voi sau nhom chi so DD-11; doc
  cohort User, course contexts Content va result aggregates Assessment; nguon loi
  tra 503, average khong co denominator tra null.
- Tich hop fix #44 cho Assessment va ap cung fix cho Progress: 
  CannotCreateTransactionException nay tra 503 thay vi 500.
- 93 test PASS tren H2 va PostgreSQL 17.11 (assessment 50, progress 43).

Refs #30 #34 #35 #36 #44
```

Khi mở PR, dẫn lại **mục 3** (hợp nhất relay) và **mục 10** (điểm cần chốt) để
reviewer biết chính xác chỗ nào cần quyết định.
