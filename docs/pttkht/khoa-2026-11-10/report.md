# Hồ sơ phân tích thiết kế nền tảng học tiếng Anh trực tuyến

Nhóm 7 gồm Khoa, Hưng, Huy, Khang và Tùng. Khoa phụ trách tổng hợp. Ngày cập nhật
10/11/2026. Cấu trúc theo HoSo_PTTK_TaskHub; nội dung nghiệp vụ theo SRS/UC v0.4,
DD v0.2, public API v0.1 và internal auth 0.1.1.

Khoa đã biên soạn các Phần 1, 9, 10, 11, 13 và ghép nội dung thiết kế hiện có vào
các phần còn lại để review thống nhất. Người chủ trì từng phần vẫn theo kế hoạch
5 thành viên. Hồ sơ cần phản hồi của từng chủ trì, khảo sát/dùng thử thật và các
kết quả tích hợp còn thiếu trước khi trở thành bản nghiệm thu nhóm.

## Phần 1 Tổng quan và khởi tạo dự án

### Bối cảnh và vấn đề

English Online Learning Platform hỗ trợ người học tự học Vocabulary, Grammar,
Listening và Reading, làm Exercise/Quiz/Test, xem kết quả và tiến độ trên một
nền tảng web. Admin quản lý tài khoản, cấu trúc khóa học, học liệu và câu hỏi.
Phạm vi này đã được thể hiện trong FR-01–FR-21 và PB-01–PB-13 của nhóm.

Vấn đề cần giải quyết là kết nối nội dung học, lần làm bài và lịch sử học theo
một tài khoản, đồng thời giúp quản trị viên duy trì dữ liệu hợp lệ. Khi mở bài,
làm lại, đổi khóa hoặc gặp lỗi mạng, hệ thống cần phản ánh đúng dữ liệu đã lưu.
Đây là phát biểu mục tiêu của dự án; số liệu về hành vi người dùng phải được bổ
sung bằng khảo sát của Khang, không suy ra từ ví dụ TaskHub.

### Mục tiêu và điều kiện đo

| Mục tiêu của đợt PTTKHT | Cách kiểm chứng | Người theo dõi |
| --- | --- | --- |
| Tổ chức hồ sơ đủ 13 phần và phụ lục | Kiểm danh mục, chủ trì, reviewer, phiên bản và đầu ra | Khoa |
| Truy vết 21 FR và 6 NFR | RTM liên kết UC, AC, API, entity, màn hình và test hoặc lý do chưa đo | Tùng |
| Mô tả đúng ranh giới dữ liệu | Bốn service sở hữu dữ liệu; không FK/SQL/ghi xuyên DB | Huy và Khang |
| Phân biệt trạng thái dữ liệu thật và hiển thị | Kiểm save/ACK, submit/retake, completion và Statistics | Hưng và Tùng |
| Bàn giao có thể tái hiện | SHA, cấu hình mẫu, seed, lệnh chạy, expected/actual và giới hạn | Khoa |

Các mục tiêu trên là tiêu chí nội bộ của đợt thực hiện, không phải số đo hiệu quả
vận hành đã đạt. NFR-06 chưa quy định SLA hoặc chỉ tiêu tải số cứng.

### Phạm vi và các bên liên quan

Trong MVP có Learner/Admin; tài khoản và Profile; Course/Level; Course–Module–Lesson;
học liệu; My Vocabulary; Assessment, Attempt, Result; Progress/History/Dashboard;
Admin Statistics; import Vocabulary XLSX và Lesson DOCX.

Ngoài MVP có Goal bắt buộc, Basic Recommendation, tạo Vocabulary thủ công,
AI Tutor/AI Recommendation nâng cao, OCR nâng cao, Chrome Extension và các dịch
vụ trả phí chưa được yêu cầu. Không bổ sung thanh toán, giáo viên, lớp trực tuyến
hoặc chứng chỉ vào báo cáo hiện hành.

| Bên liên quan | Nhu cầu và trách nhiệm |
| --- | --- |
| Learner | Truy cập nội dung khả dụng; làm bài; dữ liệu cá nhân và lịch sử đúng chủ sở hữu |
| Admin | Quản lý dữ liệu, trạng thái công bố, câu hỏi và thống kê trong phạm vi cho phép |
| Nhóm phát triển | Duy trì tài liệu và code có truy vết, kiểm thử và review |
| Giảng viên | Đánh giá tính đúng, nhất quán và khả năng giải thích hồ sơ PTTKHT |

### Khả thi và nguồn lực

Khả thi kỹ thuật có căn cứ từ User/Content/Gateway đã nằm trên develop, frontend
và Assessment/Progress có nhánh implementation. Tuy nhiên Statistics #36, lỗi
frontend #49 và các gate review còn mở, nên chưa kết luận toàn bộ MVP hoàn tất.

Nguồn lực kế hoạch là 5 người, 10 ngày, khoảng 3 giờ/người/ngày, tổng 150 giờ công.
Đây là ước lượng, không phải actual hoặc cam kết lịch rảnh đã được xác nhận. Chi
phí tiền, hosting và lợi ích kinh tế chưa được đo; sử dụng môi trường local/TEST
để hoàn thiện báo cáo trước khi quyết định môi trường triển khai.

Khả thi vận hành cần khảo sát và dùng thử thật của Khang/Hưng. Phạm vi xử lý dữ
liệu gồm email, hash mật khẩu, Profile, thiết lập và lịch sử học; trước sử dụng
dữ liệu thật phải hoàn tất thông báo mục đích, quy trình truy cập và chính sách
lưu giữ. Các thông số pháp lý và lưu giữ cần quyết định riêng.

### Phân công và rủi ro

Khoa chịu trách nhiệm cuối cùng cho hồ sơ. Hưng chủ trì Phần 8; Huy Phần 5/7;
Khang Phần 2/3/6; Tùng Phần 4/12; Khoa Phần 1/9/10/11/13. Mỗi phần có reviewer
khác tác giả trong responsibility.csv. Phân công này áp dụng cho đợt PTTKHT năm
thành viên; lịch sử QLDAPM ba thành viên vẫn được giữ nguyên.

| Rủi ro | Ảnh hưởng | Cách xử lý và chủ trì |
| --- | --- | --- |
| Hai hệ mã UC khác nhau | Đứt truy vết giữa Word, API và test | Dùng 11 mã UC baseline và giữ alias 13 UC cũ; Khoa, Khang, Tùng |
| Statistics chưa triển khai đủ | UI có MOCK nhưng không đủ điều kiện nghiệm thu LIVE | Huy xử lý #36, Khang tính fixture, Hưng tích hợp |
| Save lỗi nhưng UI báo thành công | Learner tưởng đáp án đã lưu | Hưng sửa #49, Khoa/Tùng review và retest |
| Docker hoặc peer API chưa sẵn | Không chạy được restore/LIVE trong lượt hiện tại | Giữ BLOCKED, bàn giao runbook và ghi evidence lịch sử đúng ngày |
| Quá tải và thiếu phản hồi review | Trễ bản ghép cuối | Khoa theo dõi hằng ngày; chuyển fixture/test cho Khang/Tùng, không đổi ownership code tùy ý |

## Phần 2 Khảo sát hiện trạng

Chủ trì Khang, reviewer Tùng. Nguồn hiện có gồm tài liệu Use Case của nhóm, lab
Sequence, SRS/DD/API/UI trên Drive và repository. Rà soát các nguồn này là khảo
sát tài liệu; chưa thay cho phỏng vấn hoặc quan sát người dùng thật.

| Nguồn hiện trạng | Phát hiện có bằng chứng | Việc cần bổ sung |
| --- | --- | --- |
| PhanTich_UseCase.docx | 13 UC chi tiết, một số mô tả khác baseline | Xác minh crosswalk và sửa các khác biệt |
| W2/W3 Drive | Baseline yêu cầu, ownership và API đã có | Owner review và physical mapping hiện hành |
| GitHub | Mã triển khai trên các nhánh; defect #49 và Statistics #36 còn mở | Kiểm chứng bản sửa và consumer acceptance |
| Trello | Kế hoạch và trạng thái theo tuần | Liên kết đầu ra PTTKHT mới, không sửa lại lịch sử |

AS IS ở mức công việc nhóm: tài liệu nghiệp vụ, source và evidence đang ở nhiều
nơi, có các checkpoint khác ngày và khác branch. Quy trình rà soát cần xác định
đúng phiên bản, đối chiếu khác biệt rồi chuyển cho chủ trì chỉnh sửa.

Khang bổ sung phiếu khảo sát, ngày, mã người tham gia, phản hồi gốc và hạn chế
mẫu trước D2. Chưa có dữ liệu trong đợt này để kết luận tần suất học, tỷ lệ bỏ dở,
mức hài lòng hoặc nhu cầu đại diện của người học.

## Phần 3 Phân tích yêu cầu

Chủ trì Khang, reviewer Khoa. Danh mục FR/NFR giữ mã SRS v0.4. Bảng yêu cầu đầy
đủ được trích từ SRS hiện hành trong bản Word; thay đổi nghiệp vụ phải liên kết
DD/API và được nhóm review.


| Mã | Yêu cầu chức năng |
| --- | --- |
| FR-01 | Learner có thể tạo tài khoản bằng thông tin đăng ký mà hệ thống yêu cầu. |
| FR-02 | Người dùng đã có tài khoản có thể đăng nhập. |
| FR-03 | Người dùng có thể đăng xuất. |
| FR-04 | Learner có thể xem và cập nhật Profile của chính mình. |
| FR-05 | Hệ thống phân quyền Learner/Admin; Admin có thể quản lý người dùng. |
| FR-06 | Learner có thể xem danh sách Course hiện có. |
| FR-07 | Learner có thể chọn và thay đổi Course, Level; không cần chọn Goal. |
| FR-08 | Learner có thể duyệt nội dung theo Course → Module → Lesson. |
| FR-09 | Learner có thể mở nội dung học Vocabulary, Grammar, Listening và Reading khi nội dung đó được cung cấp. |
| FR-10 | Learner có thể lưu từ vựng đã tồn tại trong nội dung học vào My Vocabulary. |
| FR-11 | Learner có thể bỏ lưu từ vựng của mình. |
| FR-12 | Learner có thể làm Exercise sau mỗi Lesson và làm lại không giới hạn số lần. |
| FR-13 | Learner có thể làm Quiz/Test và gửi bài. |
| FR-14 | Hệ thống chấm tự động các câu trả lời khách quan thuộc loại được hỗ trợ. |
| FR-15 | Learner có thể xem Result sau khi hoàn tất Exercise/Quiz/Test. |
| FR-16 | Learner có thể xem Progress và Learning History của mình. |
| FR-17 | Admin có thể tạo, xem và cập nhật Course, Module, Lesson; không được xóa các đối tượng này. |
| FR-18 | Admin có thể quản lý nội dung Vocabulary, Grammar, Listening và Reading. |
| FR-19 | Admin có thể quản lý Exercise, Question, Quiz và Test. |
| FR-20 | Admin có thể xem thống kê học tập cơ bản. |
| FR-21 | Admin có thể import Vocabulary từ Excel và Lesson từ file văn bản Word. |

| Mã | Yêu cầu phi chức năng |
| --- | --- |
| NFR-01 | API dùng JWT cho xác thực; mật khẩu lưu dưới dạng BCrypt hash, không lưu plaintext; kiểm tra RBAC cho tài nguyên theo vai trò. |
| NFR-02 | Mỗi entity cốt lõi có đúng một service chịu trách nhiệm ghi; dùng Database per Service. Service không ghi trực tiếp vào database của service khác. |
| NFR-03 | Request/response cần kết quả tức thời dùng REST qua API Gateway; event bất đồng bộ dùng Apache Kafka khi phù hợp. AssessmentCompleted v1 theo DD-10 và W3-04 APPROVED: Assessment Result + transactional outbox → Kafka → Progress idempotent consumer, DLT/replay; không cross-service DB write. |
| NFR-04 | Thay đổi code đi theo Git/PR workflow đã thống nhất; API và quyết định kiến trúc được ghi tài liệu; PR có reviewer khác tác giả. |
| NFR-05 | Các luồng MVP trọng yếu phải kiểm tra được qua API/UI phù hợp và có Acceptance Criteria truy vết từ Requirement. |
| NFR-06 | Performance và availability là mục tiêu chất lượng ở mức phù hợp với dự án môn học; không đặt SLA hoặc ngưỡng số cứng khi chưa có yêu cầu cụ thể từ giảng viên. |

### Quy tắc nghiệp vụ dùng để kiểm tra liên phần

| Mã biên soạn | Quy tắc hiện hành | Nguồn |
| --- | --- | --- |
| BR01 | Đăng ký tự cấp LEARNER; Admin bootstrap riêng; không cho client chọn role | DD-01, FR-01/05 |
| BR02 | Learning Setup gồm Course/Level, không cần Goal; đổi setup giữ History | FR-07, UC-02 |
| BR03 | Mở Lesson chưa tạo completion; Exercise tạo Result hợp lệ thì hoàn thành, kể cả 0 điểm | FR-12/16, DD-10 |
| BR04 | Retake không giới hạn; key mới tạo Attempt mới; cùng key retry giữ Attempt | DD-09, API Attempt |
| BR05 | Quiz/Test dùng deadline server; refresh/relogin không reset; Exercise không timer | DD-09 |
| BR06 | My Vocabulary là reference tại Content, unique user/vocabulary; bỏ lưu không xóa nguồn | DD-12 |
| BR07 | Không DELETE hoặc soft-delete Course/Module/Lesson; publish cần readiness hợp lệ | FR-17, DD-04 |
| BR08 | Vocabulary XLSX và Lesson DOCX import all-or-nothing; một lỗi không tạo partial success | DD-05/06/07 |
| BR09 | Progress chia cho tất cả Lesson trong Course, gồm draft/unpublish; không Lesson trả null | FR-16, DD-11 |
| BR10 | Statistics giữ global/cohort/snapshot, tính mọi Result phù hợp; nguồn bắt buộc lỗi trả 503 | FR-20, DD-11 |

Ví dụ story UC-04: Là Learner, tôi muốn tiếp tục bài đang làm sau lỗi mạng để
không nộp một bộ đáp án chưa được lưu. Given PUT answers trả 503, When GET trạng
thái thành công, Then giao diện vẫn phải thể hiện đáp án chưa ACK là chưa lưu;
chỉ xóa cảnh báo sau khi lưu thành công hoặc đồng bộ terminal đúng server. Đây
là tiêu chí cần sửa ở #49, không phải kết quả đã đạt.

TO BE: Learner đăng nhập → chọn Course/Level → học Lesson → start Attempt → lưu
đáp án và nhận ACK → submit → xem Result → Progress cập nhật qua event. Admin
chuẩn bị học liệu/câu hỏi → kiểm readiness → công bố → kiểm thống kê theo dữ liệu.

Thuật ngữ thống nhất: Course là khóa học; Module là phần của khóa; Lesson là bài
học; Exercise là bài tập cuối bài; Attempt là lần làm; Result là kết quả; History
lưu lịch sử; Completion là dấu hoàn thành duy nhất; availability là khả dụng,
khác trạng thái published.

## Phần 4 Mô hình hóa phân tích

Chủ trì Tùng, reviewer Huy. Dùng mã UC-01–UC-11 của Drive làm mã canonical trong
hồ sơ; các mã UC01–UC13 ở file Word cũ là alias để tra lại, không đánh số lại API.
Crosswalk đính kèm ghi từng ánh xạ, phần thiếu và chỗ cần sửa.


| Mã baseline | Mục tiêu của tác nhân |
| --- | --- |
| UC-01 | Learner quản lý tài khoản, phiên và Profile |
| UC-02 | Learner chọn hoặc đổi Course, Level |
| UC-03 | Learner duyệt nội dung và học Lesson |
| UC-04 | Learner làm Exercise/Quiz/Test và xem Result |
| UC-05 | Learner xem Progress/History |
| UC-06 | Learner lưu/bỏ lưu từ có sẵn |
| UC-07 | Admin quản lý tài khoản |
| UC-08 | Admin quản lý Course và nội dung |
| UC-09 | Admin quản lý Assessment |
| UC-10 | Admin xem thống kê cơ bản |
| UC-11 | Admin import Vocabulary từ Excel, Lesson từ Word |

Các khác biệt đã đối chiếu: local UC08 dùng tổng Lesson đang công bố trong khi
baseline gồm mọi Lesson; local UC12 cho tạo Vocabulary thủ công trong khi MVP
chỉ tạo qua XLSX; bước preview import trong local UC11/12 chưa có giao thức
preview/commit riêng trong API v0.1; local UC06 nói trạng thái học từng từ không
được diễn giải thành mastery. Course/Level, Profile và Statistics cần được thể
hiện đủ theo UC baseline.

Sequence sẵn có trong lab mô tả nộp Exercise và Progress. Khi đưa vào báo cáo,
phải giữ bước lưu Result và Outbox trong transaction Assessment, publish Kafka,
consume idempotent ở Progress và phân biệt Result tức thời với projection eventual.
Không thay evidence runtime bằng sơ đồ.

Trạng thái Attempt dùng theo contract: IN_PROGRESS chuyển SUBMITTED khi nộp hợp
lệ, hoặc TIMEOUT cho bài có deadline khi hết hạn. Terminal không nhận thêm đáp
án; retake tạo Attempt khác. Tùng cần đối chiếu và bàn giao bộ sơ đồ chỉnh sửa
được, gồm Use Case, Activity, Sequence, state và domain model trước khi chốt Phần 4.

## Phần 5 Thiết kế kiến trúc

Chủ trì Huy, reviewer Khoa. Kiến trúc gồm React frontend, API Gateway, User,
Learning Content, Assessment, Progress và database riêng từng service. Gateway
không sở hữu business DB. REST dùng cho phản hồi tức thời; AssessmentCompleted
đi qua Outbox/Kafka sang Progress cho cập nhật bất đồng bộ.

| Thành phần | Trách nhiệm và dữ liệu | Source được đối chiếu |
| --- | --- | --- |
| User | Account, Profile, Setup, session/revocation | develop af92253 |
| Learning Content | Course/Level/Module/Lesson, học liệu, USER_VOCABULARY và import | develop af92253 |
| Assessment | Question, Attempt snapshot, answers, Result và Outbox | candidate 413a929 |
| Progress | History, unique Completion, ProcessedEvent và tổng hợp | candidate 413a929; Statistics còn thiếu |
| API Gateway | Routing, session/CSRF/RBAC boundary, signed principal | develop af92253 |

Công nghệ kiểm được: Java 25, Spring Boot 4.0.8, PostgreSQL 17; frontend Node 24,
React/TypeScript/Vite. Database per service không tạo physical FK xuyên database.
Huy hoàn thiện C4/ADR và review toàn bộ kiến trúc; ảnh tổng thể trong bản Word
chỉ thể hiện ranh giới đã đối chiếu, không khẳng định các nhánh đã merge chung.

Các quyết định giữ nguyên gồm DB per service, internal service identity,
full-replacement answers/ACK trước submit, transactional Outbox và idempotent
consumer. Chiến thuật chất lượng được kiểm bằng timeout, transaction, unique
constraint, fail-closed và peer review; chưa có phép đo để tuyên bố đạt SLA.

## Phần 6 Thiết kế dữ liệu

Chủ trì Khang, reviewer Huy. Migration là định nghĩa thực thi; dictionary logic
không tự chứng minh DDL đã chạy trong môi trường nào.

| Database | Bảng/nhóm dữ liệu chính | Quy tắc liên kết |
| --- | --- | --- |
| User | app_user, learner_profile, learning_setup, revoked_token, spring_session/attributes | FK nội bộ user_id; course/level là external IDs |
| Content | course, level, course_module, lesson, vocabulary, learning_content, user_vocabulary | FK trong Content; user_id ở saved reference là external ID |
| Assessment | Question/Assessment, Attempt, saved answers, Result, Outbox | Snapshot câu hỏi/đáp án/thời gian và Course/Level tại lúc bắt đầu |
| Progress | History, Completion, ProcessedEvent | eventId chống trùng; completion duy nhất theo Learner/Lesson |

User/Content dictionary đã có tại docs/database.md, ERD tại docs/erd.md. Khoa đã
đối chiếu ownership và migration tương ứng. Assessment/Progress mapping cần Huy
review theo candidate, không gộp entity vào hai database của Khoa.

Các ràng buộc cần giữ: email_normalized unique; mỗi Learner có một Setup hiện
tại; Course–Level và Module–Lesson đúng cha; Vocabulary unique theo Lesson và
word/meaning chuẩn hóa; USER_VOCABULARY unique user/vocabulary. Lịch sử và completion
không được gán lại theo Setup mới. Khang bổ sung ERD logic, phân tích chuẩn hóa,
dictionary đầy đủ và fixture tính tay cho Statistics theo KH3.

## Phần 7 Thiết kế tích hợp và API

Chủ trì Huy, reviewer Khoa. Public /api/v1 gồm 63 operations theo v0.1; internal
/internal/v1 chỉ dành service caller được allowlist. Session validate hiện dùng
internal 0.1.1 với token và requestMethod, thêm CSRF cho unsafe methods.

| API/luồng | Owner | Điều kiện cần thuyết minh |
| --- | --- | --- |
| POST /auth/register và /auth/login | User | Validation, LEARNER-only register, cookie/CSRF và lỗi |
| PUT /me/learning-setup | User | Course/Level hợp lệ qua Content; nguồn lỗi 503 trước ghi |
| POST /assessments/{id}/attempts | Assessment | Snapshot, Idempotency-Key, retry và deadline |
| PUT /attempts/{id}/answers | Assessment | Thay toàn bộ answers; ACK/terminal; không tự nhận 200 là đã lưu |
| POST /attempts/{id}/submit | Assessment | Tính điểm, Result/Outbox atomic và unlimited retake |
| GET /me/progress và /me/history | Progress | Own-only, paging, null/empty và snapshot cũ |
| GET /admin/statistics | Progress | Sáu metric, filter/null/source503; chưa có implementation ở candidate |

Bảng trên là relative public paths để thuyết minh, không thay thế file OpenAPI.
Tệp public-v0.1.yaml và internal-v0.1.1.yaml là nguồn máy đọc. Lỗi theo schema
hiện hành có code/message/errors/traceId và import outcome khi áp dụng; không
tự thay contract thành Problem Details của mẫu TaskHub.

| API nguồn nội bộ Khoa bàn giao | Caller |
| --- | --- |
| User GET /users/{userId}/learning-setup | assessment, progress |
| User GET /learners/cohort | progress |
| Content GET /courses/{courseId}/context | user, assessment, progress |
| Content GET /courses/contexts | progress |
| Content GET /lessons/{lessonId}/context | assessment |

Các paths trên có tiền tố /internal/v1. Pagination nguồn page từ 0, size 1–100,
mặc định 100 theo bàn giao hiện hành. Không có transaction snapshot chung qua
nhiều service/trang. Consumer cần phân biệt lỗi nguồn và tập dữ liệu rỗng.

AssessmentCompleted v1: Result và Outbox commit cùng DB; relay publish event;
Progress ghi ProcessedEvent/History/Completion trước ACK. Retry/DLT/replay giữ
eventId; retake giữ Result riêng nhưng không nhân Completion. Kết quả luồng hẹp
đã công bố không thay kiểm broker outage/replay đầy đủ của Huy.

## Phần 8 Thiết kế giao diện và trải nghiệm

Chủ trì Hưng, reviewer Tùng. UI v0.6 có screen inventory và thiết kế Learner/Admin.
Frontend W8 candidate ec373bd có Admin Assessment/Statistics và Progress UI.
Ngày 10/11, npm run check đạt 55 unit/UI tests, lint, typecheck, build và production
guard. Đây là kiểm frontend/unit với test doubles; không chứng minh Statistics LIVE.

| Nhóm màn | Luồng và trạng thái cần đối chiếu |
| --- | --- |
| Tài khoản và Setup | Đăng ký/đăng nhập/Profile; Course/Level, lỗi/không có dữ liệu |
| Course và Lesson | Điều hướng nội dung, trạng thái không khả dụng và readiness |
| Attempt và Result | Chưa lưu/đang lưu/ACK/503, terminal, deadline và retake |
| Progress History My Vocabulary | Own-only, null/empty, unavailable và đổi Course |
| Admin nội dung và import | Draft/publish, validation, rollback và không partial success |
| Admin Assessment Statistics | Question, filter, sáu metric, null/0 và retry nguồn503 |

Lỗi #49 còn tái hiện trên candidate W8: PUT answers503, sau đó GET Attempt thành
công khiến giao diện báo đã tải/lưu trong khi server answers vẫn rỗng. Hưng sửa
trước nghiệm thu; Khoa/Tùng retest. Cần tối thiểu 2–3 persona, một journey,
navigation, 6 wireframe, tokens và dùng thử với ít nhất 3 người có SUS theo bài
giảng. Đợt này chưa có dữ liệu người dùng thật hoặc SUS để điền kết quả.

## Phần 9 Thiết kế bảo mật và quản lý dữ liệu

Khoa biên soạn, Huy review. Tài sản cần bảo vệ gồm thông tin định danh, password
hash, session/JWT/CSRF, private key service, nội dung học, answers/Result và History.
Ranh giới tin cậy nằm giữa trình duyệt–Gateway, Gateway–service, service–DB và
các source API nội bộ. Dữ liệu TEST dùng fixture, không dùng mật khẩu/định danh
thật trong báo cáo hoặc log.

### Mô hình mối đe dọa STRIDE

| Mối đe dọa | Tình huống | Kiểm soát thấy trong source hoặc thiết kế | Cách kiểm |
| --- | --- | --- | --- |
| Giả mạo | Client giả role/principal hoặc gọi internal bằng user JWT | Gateway tạo assertion ký; service JWT riêng, aud/caller allowlist; không tin header client | Sai caller/chữ ký, role giả phải bị từ chối |
| Sửa dữ liệu | Đổi userId/answer/parent ID hoặc inject trường role | DTO whitelist, ownership, bound SQL, FK/unique và snapshot | Own-only, unknown field, duplicate và parent mismatch |
| Chối bỏ | Không truy được thao tác hoặc lần nộp | X-Trace-Id, timestamps và Result/Outbox; biên bản thao tác TEST | Đối chiếu trace/request/Result, không ghi token |
| Lộ dữ liệu | Lộ password/JWT/private key hoặc đọc History người khác | Hash BCrypt, HttpOnly cookie, sanitized lỗi, owner scoped queries; secrets ngoài Git | 401/403/503, kiểm response và file bàn giao |
| Từ chối dịch vụ | File ZIP quá lớn, lặp save, nguồn chậm | Giới hạn request/OOXML, timeout và reject macro/entity; các hạn mức vận hành cần đo | File quá cỡ/corrupt, dependency down, retry có kiểm soát |
| Nâng quyền | Learner tự thành Admin hoặc runtime DB có DDL | Register luôn LEARNER, RBAC, bootstrap riêng, migration/runtime roles tách biệt | Register role bị reject; Learner admin403; kiểm grant |

### Ma trận quyền

| Chức năng | Chưa đăng nhập | Learner | Admin | Service nội bộ |
| --- | --- | --- | --- | --- |
| Đăng ký và đăng nhập | Theo public contract | Không tự thay role | Không tạo Admin qua self-register | Không thay user flow |
| Profile Setup History Result | Từ chối | Chỉ tài nguyên của mình | Không tự có quyền vào Learner-only routes | Source API theo caller |
| Học Lesson và làm bài | Từ chối | Nội dung khả dụng, Attempt của mình | Không giả Learner | Readiness/context theo allowlist |
| Quản trị và import | Từ chối | 403 | Trong whitelist công bố/quản lý | Không dùng user cookie làm identity |
| Statistics | Từ chối | 403 | Theo /admin/statistics khi implementation đạt | Progress tổng hợp source APIs |
| /internal/v1 | Không public | User JWT không đủ | Admin JWT không đủ | Chữ ký/audience/caller hợp lệ |

### Session và xác thực

Source hiện hành dùng BCrypt cost 12; access JWT mặc định 30 phút, không refresh
token. Cookie access_token là HttpOnly, host-only, path /api/v1, SameSite=Lax;
Secure khi HTTPS được cấu hình. Session CSRF lưu trong User DB, xoay khi login,
gắn jti và hạn JWT; logout lưu revoked jti trước khi xóa cookie/session.

Mỗi protected request được Gateway kiểm trạng thái tài khoản và revoked token
qua User. Method/path/role/sessionJti được ràng buộc vào signed principal. Thiếu
hoặc sai session trả401, CSRF/Origin/quyền sai trả403, dependency lỗi trả503 và
không đi tiếp thành công. HTTP loopback TEST không chứng minh TLS/Secure cookie
trên môi trường public.

### Import và dữ liệu

Content validate toàn file trước transaction all-or-nothing. Imports.java kiểm
macro/embedded object, ZIP entry bất thường/trùng, XML DTD/entity, external
relationship, số entry và tổng dữ liệu giải nén. Không coi đuôi file là đủ chứng
minh định dạng. Source giới hạn request Gateway 6 MiB và tổng OOXML giải nén
20 MiB; các giới hạn riêng của từng endpoint phải đọc theo contract/source.

DB dùng tài khoản migration/runtime riêng; Gateway không có business DB
credential. Runtime không được sửa schema tùy ý. User/Content không đọc SQL sang
Assessment/Progress. Dump, .env, cookie, JWT và private key không được commit.

### Dữ liệu cá nhân và nhật ký

Chỉ thu trường trong baseline; hạn chế dữ liệu khảo sát nhận diện được, dùng mã
người tham gia khi đưa vào phụ lục. Quy trình yêu cầu truy cập/chỉnh dữ liệu,
thời gian lưu, xóa/ẩn danh và xử lý sự cố cần nhóm xác định trước dùng dữ liệu
thật. Không lấy thời gian lưu hoặc cam kết tuân thủ pháp luật của mẫu TaskHub
làm chính sách đã duyệt cho dự án.

TraceId và timestamps đã có không đồng nghĩa đã có kho audit bất biến đầy đủ.
Nhóm cần review trường audit, quyền đọc log và retention; hiện chưa có bằng
chứng để tuyên bố đáp ứng kiểm toán production toàn diện.

## Phần 10 Thiết kế hạ tầng và vận hành

Khoa biên soạn, Huy review. Runtime User/Content/Gateway build độc lập với wrapper,
Dockerfile, Compose và migration của owner. Root Compose là tiện ích tích hợp;
Assessment/Progress lấy từ candidate Huy và có DB riêng.

### Môi trường và quy trình bàn giao

| Môi trường | Mục đích | Quy tắc |
| --- | --- | --- |
| Local dev/demo | Phát triển và minh họa | Demo mock phải ghi nhãn; không tính là Assessment/Progress thật |
| TEST riêng | Regression, fault và restore | Project/image/report directory riêng, fixture ổn định, loopback |
| Production | Triển khai thực tế | Còn HOLD W10; TLS, secrets, ingress và rollout phải kiểm riêng |

Không dùng CI/CD theo CONTRIBUTING hiện hành. Quy trình thủ công: checkout đúng
SHA → kiểm contract/layout/SDK → build/test → ghi evidence → reviewer khác tác
giả → xử lý phản hồi → tích hợp theo phụ thuộc PR → kiểm lại phần bị ảnh hưởng.

### Runbook TEST cho lần chạy tiếp theo

Đầu vào: Docker Engine/Compose hỗ trợ !override, PowerShell 7, JDK25, OpenSSL,
Python dependencies và hai checkout đúng source. Không trỏ runner vào shared DB.

```powershell
./scripts/verify-khoa-w9.ps1 `
  -HuySourceRoot '<Huy checkout tại 413a929>' `
  -Project 'khoa-backlog-w9-<run-id>' `
  -ImageTag 'khoa-w9-<run-id>' `
  -ReportDirectory '.tools/w9-<run-id>' `
  -Python '<python trong venv TEST>' `
  -BuildImages
```

Runner kiểm nhãn project/service, loopback Gateway và opt-in trước fault/restore;
thư mục report mới cho từng lần. Source APIs/Assessment seed qua owner API. Các
pha gồm core/owners/Gateway, retry-security, DB/pool/session fault, readiness và
operations/post-restart. Dừng stack giữ volumes/fixtures; không dùng down -v.

### Backup và khôi phục

pg_dump dùng custom format bên trong container; restore vào DB TEST mới được
provision riêng, không restore đè DB đang dùng. Đối chiếu tất cả bảng/digest,
constraint, owner/grant, Flyway history rồi kiểm HTTP sau restart. Restore cùng
cluster không chứng minh khôi phục sang host khác hoặc đạt RTO/RPO production.

Evidence ngày 09/11 ghi PASS cho user_db/content_db backup/restore vào DB mới
cùng TEST cluster và restart DB/app. Kết quả này được kế thừa đúng ngày/source;
đợt 10/11 chưa chạy lại fault/restore vì không có Docker Engine/socket. Đã chuẩn
bị JDK25 riêng để kiểm backend không cần Docker; kết quả chi tiết ở Phần 12.

### Giám sát và rollback

Theo dõi health, traceId, lỗi 401/403/503, latency phụ thuộc và outbox/consumer
lag khi Kafka bật. Chưa có phép đo để công bố SLO số cứng. User/Gateway phải
rollout/rollback theo cặp tương thích internal0.1.1; giữ Content/schema tương
thích, không sửa migration V1 đã áp dụng hoặc downgrade schema tùy ý.

Nếu smoke sau thay đổi thất bại, dừng mở truy cập, quay về cặp image đã kiểm
chứng và xác minh login/session/CSRF/owner requests. Thao tác rollback TEST khác
triển khai production; điều kiện production vẫn theo kế hoạch W10.

## Phần 11 Đánh giá thành phần trí tuệ nhân tạo

Khoa biên soạn, Hưng review. AI Tutor và AI Recommendation nâng cao bị loại khỏi
MVP hiện hành. Chấm SINGLE_CHOICE/TRUE_FALSE theo đáp án là xử lý deterministic,
không được ghi thành mô hình AI đã huấn luyện hoặc triển khai.

Phần này giữ trong cấu trúc TaskHub để nêu rõ mức áp dụng. Nếu mở rộng sau MVP,
nhóm có thể nghiên cứu gợi ý nội dung dựa trên lịch sử học, nhưng cần đề xuất
phạm vi, dữ liệu, baseline đánh giá, quyền sử dụng dữ liệu, chi phí và reviewer
trước khi phát triển. Đợt 10 ngày không có đầu ra model training, model card
thực nghiệm, accuracy hoặc tích hợp nhà cung cấp AI.

## Phần 12 Kiểm thử và đảm bảo chất lượng

Chủ trì Tùng, reviewer Hưng; Khoa cung cấp kết quả thuộc phạm vi review/source.
Ngày 10/11 đã chạy lại frozen contract/63 Gateway routes, service layout,
SDK snapshot và checksum evidence. Frontend W8 npm run check PASS với 55 tests;
ca riêng #49 FAIL một test như expected của việc tái hiện lỗi hiện hữu. Đây là
hai suite khác phạm vi; không trừ/ghép để gọi chất lượng toàn MVP đạt.

Backend focused ngày10/11 đạt 49 lượt JUnit trong ba module, không failure/error/skip; đóng gói thành công ba JAR. Shared core tests lặp theo module nên không gọi đây là 49 kịch bản nghiệp vụ duy nhất. Các suite dùng doubles/mock HTTP, không phải LIVE PostgreSQL. Lượt đầu lỗi DNS/proxy/Mockito được giữ riêng; lượt thành công dùng proxy hiện hành và Mockito startup agent. Backend focused và trạng thái môi trường được ghi trong evidence cùng SHA.
Các ca Docker/LIVE/restore mới giữ BLOCKED khi thiếu Docker; evidence ngày09/11
giữ riêng. Peer approval còn là điều kiện độc lập sau test.

| Mã kiểm tra | Hành động và expected | Trạng thái trong đợt này |
| --- | --- | --- |
| K-01 | Kiểm frozen contracts và 63 routes/owner/role | PASS mới 10/11 |
| K-02 | Kiểm standalone layout và SDK | PASS mới 10/11, chưa build isolated Docker |
| K-03 | npm run check tại PR47 head | PASS mới, 55 unit/UI; lint/type/build/guard đạt |
| K-04 | PUT answers503 rồi GET status không được xóa unsaved | FAIL mới trên PR47, #49 còn mở |
| K-07 | Focused verify JDK25 và package3module | PASS 49 lượt JUnit, ba JAR; không Docker/LIVE |
| K-05 | Progress/Statistics consumer acceptance | Chờ Huy; source handoff đã có |
| K-06 | TEST backup/restore, DB fault và post-restart mới | BLOCKED Docker; evidence lịch sử09/11 có scope riêng |

RTM đầy đủ của Tùng cần liên kết FR/NFR→UC/AC→API/entity/screen→case→evidence.
Không coi metadata route có endpoint là owner đã triển khai: /admin/statistics
có contract/Gateway nhưng candidate Progress chưa có controller tương ứng.

Definition of Done cho từng gói: đầu ra đúng AC; reviewer khác tác giả phản hồi;
lỗi nghiêm trọng xử lý hoặc có giới hạn được ghi nhận; link/RTM cập nhật; actual
và kết quả kiểm chứng có bằng chứng. Chưa đánh dấu KO1–KO5 Done thay cho các gate này.

## Phần 13 Triển khai và chuyển giao

Khoa biên soạn, Tùng review. Lịch đề xuất giữ 11–20/11/2026: D1–D2 chốt đầu vào;
D3–D4 nháp phân tích/API/bảo mật/vận hành; D5–D6 dữ liệu/UI/consumer và RTM;
D7–D8 kiểm chứng và bản ghép đầy đủ; D9–D10 sửa lỗi, tập báo cáo và bàn giao.
Phần Khoa được chuẩn bị sớm ngày10/11, không ghi các hoạt động tương lai đã diễn ra.

### Kịch bản demo và tiêu chí dừng

| Bước | Người trình bày dự kiến | Thao tác và bằng chứng |
| --- | --- | --- |
| 1 | Khoa | Giới thiệu phạm vi, 5 thành viên, architecture và source version |
| 2 | Hưng | Learner login, Course/Level, Lesson và saved vocabulary |
| 3 | Huy | Start/save ACK/submit, Result, retake và event/Progress |
| 4 | Khang | Đối chiếu ERD/dictionary và phép tính Progress/Statistics |
| 5 | Tùng | RTM, ca lỗi, expected/actual và giới hạn chưa đạt |

Demo thành công phải dùng API thật cho phạm vi tuyên bố LIVE. Nếu Statistics chưa
sẵn hoặc lỗi #49 còn tồn tại, trình bày trạng thái và case lỗi; không đưa MOCK
ra làm bằng chứng backend đã hoàn thành. Không demo fault/restore trên shared
environment; dùng TEST được chọn và runner guard.

### Bàn giao và hỗ trợ

Gói bàn giao gồm Word có 13 phần, diagram source, OpenAPI hiện hành, crosswalk UC,
RACI, RTM/test log, runbook, seed/config mẫu và backlog. Loại khỏi gói secrets,
dump dữ liệu thật, cookie/JWT, node_modules, target và private keys.

| Phần cần bàn giao tiếp | Người thực hiện | Điều kiện Khoa nhận |
| --- | --- | --- |
| Khảo sát và Phần2/3/6 cuối | Khang | Phiếu thật, crosswalk sửa, ERD/dictionary và reviewer |
| UI và patch49 | Hưng | SHA sửa, regression/ảnh, usability/SUS thật |
| Kiến trúc/API và consumer | Huy | Review44, Statistics/progress matrix, seed và evidence |
| Mô hình/RTM và nghiệm thu | Tùng | File sơ đồ gốc, case/actual, review độc lập và checklist |

Khoa tổng hợp phản hồi, sửa liên phần, chốt mục lục/hình/bảng/nguồn và kiểm bản
nộp. Tập báo cáo, xác nhận đóng góp và nghiệm thu là hoạt động có người thật,
chưa được thực hiện trong đợt này. Không mở production từ kết quả hồ sơ.

### Nguồn tham khảo của hồ sơ

Tài liệu giảng viên: HoSo_PTTK_TaskHub.docx; Bài2 UML; Bài3 Use Case; Bài4
Activity/Sequence; Bài5 PTTKCSDL; Bài6 Architecture Design và Case Study; Bài7
Thiết kế tích hợp; Bài8 UX/UI. Các tệp này là căn cứ cấu trúc và yêu cầu học phần.

Nguồn nhóm: PhanTich_UseCase.docx, W5_Lab2_Sequence_Nhom7_DaGiai(1).html;
W2 SRS/UC, W3 DD/API/Architecture/Database/UI và W8 log trên Drive; source,
issues/PR và evidence trong repository. Danh mục URL và SHA được giữ trong
review.md và bảng nguồn của bản Word.

Nguồn trực tuyến:

- [Repository](https://github.com/KhoaTryHard/english-learning-platform).
- [Drive nhóm](https://drive.google.com/drive/folders/1S4XjotjFXANMykYZPa_TGfNEHDCKtD0P).
- [Trello nhóm](https://trello.com/b/q0Cyhale/nhom-7-qldapm).
- [SRS v0.4](https://docs.google.com/document/d/1GcHdmljWlC5P4o7V3UMh9OkKSThlYrPaxWFWdIlI8w0/edit).
- [UC và AC](https://docs.google.com/document/d/14OKvB16aG1HgfH-aSe5fsp_3xm2jVDqmiNl482YWALs/edit).
- [DD v0.2](https://docs.google.com/document/d/1OepZRaVWvEQJOIQcPRPsOq6Ld_4LK5FA5rmnUD2Vv3A/edit).
- [API v0.1](https://docs.google.com/document/d/1VQKbmWOJae3Kj3S92ea08pUb-bswQNyDtuBGH_QTPBc/edit).
