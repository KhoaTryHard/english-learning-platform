# learning-content-service
Owner **Khoa**. BE-C1→C4; Course/Module/Lesson/Content/My Vocabulary/Import.

Scaffold vị trí đích, chưa phải implementation. Code hiện có từ PR #1 được chuyển theo migration-plan, không code lại từ đầu.

Khi tích hợp: pom.xml + Maven Wrapper, Application trong root package, Dockerfile/standalone Compose/.env.example; build riêng không phụ thuộc root/sibling services. src/main/resources/db/migration thuộc DB service này. Dùng package theo domain, thêm api/application/domain/infrastructure khi thật sự cần.
