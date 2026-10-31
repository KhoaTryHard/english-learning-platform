# LinguaTrack — English Online Learning Platform

Web học tiếng Anh MVP của Nhóm 7. Monorepo gồm React responsive và Java 21/Spring Boot, API Gateway + 4 service theo nghiệp vụ.

## Bắt đầu
- [Cây thư mục và quyết định tổ chức](docs/architecture/repository-layout.md)
- [Phân công và task GitHub](docs/planning/task-index.md)
- [Quy trình làm việc](CONTRIBUTING.md)
- [Kế hoạch chuyển code đang có](docs/architecture/migration-plan.md)
- [Nguồn yêu cầu/thiết kế](docs/references.md)

**Trạng thái triển khai:** Assessment đã có implementation độc lập, POM/Maven Wrapper,
migration và Compose tại [backend/services/assessment-service](backend/services/assessment-service/README.md).
Frontend và các service khác trong nhánh này vẫn là scaffold; code Khoa/Hưng nằm
trên nhánh/PR riêng. Chạy Maven từ thư mục Assessment; root chưa có Maven aggregator.

## Ownership
| Thành viên | Phần phụ trách |
|---|---|
| Lê Tiến Hưng — @LeTHung | PM, toàn bộ frontend desktop/mobile responsive |
| Nguyễn Đăng Khoa — @KhoaTryHard | Gateway, User, Learning Content |
| Nguyễn Quang Huy — @Huy1188 | Assessment, Progress, Kafka; Architecture/Data |

Code User/Content/Gateway hiện nằm ở PR #1 (Draft, chưa merge lúc đối chiếu 20/10/2026). Scaffold không thay thế code đó. Tích hợp theo migration-plan, giữ DB/data và kiểm tra lại CI.

## Nguyên tắc
Một React app cho desktop/mobile; API/state/business logic dùng chung. Database và migration thuộc từng service. My Vocabulary thuộc Content. Kafka nối AssessmentCompleted từ Assessment sang Progress. API qua Gateway, không join SQL xuyên service.
