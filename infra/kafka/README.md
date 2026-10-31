# Kafka foundation — Huy, W5-02

Outbox/relay nằm Assessment, consumer/ProcessedEvent nằm Progress. `compose.yaml`
là broker single-node KRaft cho development; port host chỉ bind localhost.
Cấu hình tham khảo [Apache Kafka Docker examples](https://github.com/apache/kafka/tree/trunk/docker/examples).

```powershell
docker compose up -d
```

Chạy hai service bằng Java trên host với `KAFKA_ENABLED=true` và
`KAFKA_BOOTSTRAP_SERVERS=localhost:9092`. Progress tạo topic
`assessment.completed.v1` và `assessment.completed.v1.DLT`, một partition mỗi
topic cho demo; Kafka key là userId. Start Progress trước relay Assessment.

Nếu client chạy trong service Compose riêng trên Docker Desktop, đặt
`KAFKA_ADVERTISED_HOST=host.docker.internal` khi khởi động broker và bootstrap
service là `host.docker.internal:9092`. Client cùng network broker có thể dùng
`kafka:29092`. Không dùng localhost trong container để chỉ host/broker khác.

Relay đợi broker ACK rồi mark SENT. Crash sau publish/trước mark có thể phát lại
cùng eventId; consumer phải idempotent. Tắt Kafka adapter mặc định để có thể làm
foundation độc lập khi chưa có broker; không diễn giải trạng thái này là đã sync.

Consumer validate JSON Schema và cross-field trước DB write. ProcessedEvent,
History, Completion và projection commit cùng transaction; sau đó mới ACK.
Transient failure retry 1s/5s/30s; invalid/conflicting event vào DLT. DLT publish
phải thành công mới recover/commit offset. Không dùng HTTP endpoint để chèn event.

Replay: sửa nguyên nhân, đọc payload/key gốc từ DLT, publish lại topic chính
bằng tooling Kafka được owner cho phép; giữ nguyên eventId/submissionId/key.
Không replay payload conflict cho tới khi review dữ liệu. Delivery at-least-once;
không có cam kết exactly-once toàn hệ thống.

Compose này dùng plaintext/local development. Broker dùng chung/deployment cần
ACL chỉ cho Assessment produce và Progress consume, cùng cấu hình transport của
nhóm. Replica/partition production và repair/reconciliation tiếp tục #30/#34/#41.

Docker/broker E2E chưa chạy trong môi trường hiện tại. Unit/DB tests sử dụng
publisher/record/ack test doubles, không thay cho evidence Kafka thật.
