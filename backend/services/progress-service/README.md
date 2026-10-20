# progress-service
Owner **Huy**. BE-P1/P2/BE-K2; completion/history/statistics/idempotent consumer.

Scaffold vị trí đích, chưa phải implementation. Owner khởi tạo độc lập theo W5-02; chọn DB engine và ghi quyết định trước migration.

Khi tích hợp: pom.xml + Maven Wrapper, Application trong root package, Dockerfile/standalone Compose/.env.example; build riêng không phụ thuộc root/sibling services. src/main/resources/db/migration thuộc DB service này. Dùng package theo domain, thêm api/application/domain/infrastructure khi thật sự cần.
