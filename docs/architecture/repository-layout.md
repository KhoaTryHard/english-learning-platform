# Quyết định tổ chức repository — 20/10/2026

## Phương án được đề xuất trong PR scaffold
Monorepo cho nhóm 3 người, một frontend responsive, 4 backend domain services + Gateway. Chỉ tạo các package cần thiết; chưa thêm Nx/Turborepo/Eureka/Config Server/Kubernetes. Các folder scaffold không đại diện chức năng đã code/test.

| Đường dẫn | Mục đích | Owner |
|---|---|---|
| frontend/src/app | bootstrap, router, providers | Hưng |
| frontend/src/app/layouts/desktop | shell/sidebar desktop | Hưng |
| frontend/src/app/layouts/mobile | shell/menu/navigation mobile web | Hưng |
| frontend/src/features | auth, profile, learning-setup, learning, assessment, progress, vocabulary, admin | Hưng |
| frontend/src/shared | UI primitives, API client, hooks, styles, assets | Hưng |
| backend/api-gateway | route/auth forwarding/errors, không DB nghiệp vụ | Khoa |
| backend/services/user-service | auth/profile/currentCourseLevel/Admin User | Khoa |
| backend/services/learning-content-service | Course/Module/Lesson/Content/My Vocabulary/Import | Khoa |
| backend/services/assessment-service | Questions/Attempt/Result/deadline/readiness/Outbox | Huy |
| backend/services/progress-service | completion/history/Progress/Stats aggregator/consumer | Huy |
| infra/kafka | broker/topic/retry/DLT cấu hình | Huy |
| infra/compose | tích hợp local, chờ owner cấu hình thật | Khoa/Huy; Hưng điều phối |
| contracts/http | index hợp đồng REST approved | Khoa/Huy |
| contracts/events | AssessmentCompleted và compatibility | Huy |
| tests/integration, tests/e2e | kiểm thử xuyên ứng dụng | Cả nhóm/Hưng điều phối |
| scripts | tooling repository, không business logic | Owner liên quan |

## Frontend desktop + mobile
Dùng **một React app**. Tách layout theo viewport khi cần; ưu tiên CSS Grid/Flex/media queries, business hooks/API/schema giữ một nguồn. Không tạo hai bộ auth/timer/Progress cho desktop/mobile. Không đổi viewport bằng cách remount toàn bộ bài thi làm mất state. Component riêng chỉ khi cấu trúc UI khác thật sự.

Feature tự giữ components/hooks/api/types/tests của mình khi có code; không tạo ngay hàng chục folder rỗng. shared/ui chỉ chứa thành phần không phụ thuộc feature. shared/api xử lý cookie/CSRF/HTTP; feature chứa endpoint mapping. Không lưu JWT vào localStorage trái DD-03.

Thiết kế hiện hành W3-05 v0.6/Figma page 49:2: 28 desktop, 2 mobile 390px, một state Exercise đã nộp. Hai mẫu mobile chưa là coverage toàn hệ thống; FE-07 tiếp tục responsive các màn còn lại. Desktop tham chiếu 1440px. Mobile là web responsive.

## Backend theo domain
Mỗi service có build độc lập (pom.xml, Maven Wrapper), src/main/java, src/main/resources/db/migration, src/test, Dockerfile và README riêng khi implementation được tích hợp. Không bắt service phụ thuộc root Maven hoặc JAR domain của service khác.

Root package có Application class và subpackage theo nghiệp vụ; bên trong feature có api/application/domain/infrastructure khi cần, tránh layer rỗng hình thức. Giữ namespace code PR #1 trong bước di chuyển; không trộn rename business với move.

Mỗi service sở hữu DB/credentials/migration. Không shared JPA entity/repository, cross-service FK/SQL. Hợp đồng REST/event là ranh giới. Shared helper kỹ thuật PR #1 phải được kiểm kê, không ép vào shared business SDK mới. Gateway không là service sở hữu người dùng.

User/Content đang dùng PostgreSQL 17 ở nhánh Khoa. Assessment/Progress engine chờ Huy/Khoa ghi quyết định; không áp toàn hệ thống. Kafka là infrastructure, không thêm “kafka-service” Spring Boot nghiệp vụ.

## Căn cứ tham khảo
Đây là đề xuất áp dụng cho nhóm, không phải một cây thư mục bắt buộc từ framework.
- Spring Boot, Structuring Your Code: https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html (tham khảo cấu trúc; không yêu cầu nâng code hiện có lên phiên bản Boot mới).
- React, Sharing State Between Components: https://react.dev/learn/sharing-state-between-components
- MDN, Media query fundamentals: https://developer.mozilla.org/en-US/docs/Learn_web_development/Core/CSS_layout/Media_queries
- Microsoft, Data considerations for microservices: https://learn.microsoft.com/azure/architecture/microservices/design/data-considerations
