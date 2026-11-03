# LinguaTrack — English Online Learning Platform

## Cập nhật W6 — 27/10/2026

Đã có React frontend: Auth/Profile/Course/Level/Lesson, Exercise/Quiz/Test/Attempt/Result và các trang Progress/History/My Vocabulary cơ bản. [Hướng dẫn chạy](frontend/README.md), [bằng chứng kiểm thử](docs/evidence/w6-frontend.md), [rà soát tài liệu & GitHub](docs/reviews/2026-10-27-project-audit.md), [kế hoạch W6](docs/planning/w6-plan.md).

```powershell
cd frontend
npm ci
npm run demo
```

Node 24 LTS. Demo có nhãn MOCK; tài khoản `hung.demo@example.com` / `LearningDemo2026!`. `npm run dev` gọi Gateway thật, không tự đổi sang dữ liệu mẫu khi lỗi. Nhánh frontend kế thừa scaffold PR #18, chưa merge thay nhóm. Backend của Khoa vẫn ở PR #1; Assessment/Progress/Kafka chưa được frontend kiểm thử toàn luồng thật.

Web học tiếng Anh MVP của Nhóm 7. Monorepo gồm React responsive và Java 21/Spring Boot, API Gateway + 4 service theo nghiệp vụ.

## Bắt đầu
- [Cây thư mục và quyết định tổ chức](docs/architecture/repository-layout.md)
- [Phân công và task GitHub](docs/planning/task-index.md)
- [Kế hoạch đầy đủ W5–W8 và workload](docs/planning/w5-w8-plan.md)
- [Quy trình làm việc](CONTRIBUTING.md)
- [Kế hoạch chuyển code đang có](docs/architecture/migration-plan.md)
- [Nguồn yêu cầu/thiết kế](docs/references.md)

**Trạng thái backend scaffold:** các thư mục backend ở nhánh này chưa có runtime; code User/Content/Gateway nằm riêng trong PR #1. Chạy npm tại frontend, không chạy npm/Maven tại repository root. Code/test mock không thay reviewer acceptance hoặc full MVP acceptance.

## Ownership
| Thành viên | Phần phụ trách |
|---|---|
| Lê Tiến Hưng — @LeTHung | PM, toàn bộ frontend desktop/mobile responsive |
| Nguyễn Đăng Khoa — @KhoaTryHard | Gateway, User, Learning Content |
| Nguyễn Quang Huy — @Huy1188 | Assessment, Progress, Kafka; Architecture/Data |

Code User/Content/Gateway hiện nằm ở PR #1 (Draft, chưa merge lúc đối chiếu 27/10/2026). Scaffold không thay thế code đó. Tích hợp theo migration-plan, giữ DB/data và chạy lại kiểm thử thủ công. Dự án bỏ CI/CD từ 27/10; xem [quy trình](CONTRIBUTING.md) và [báo cáo đồng bộ Drive/issue](docs/reviews/2026-10-27-document-cleanup.md).

## Nguyên tắc
Một React app cho desktop/mobile; API/state/business logic dùng chung. Database và migration thuộc từng service. My Vocabulary thuộc Content. Kafka nối AssessmentCompleted từ Assessment sang Progress. API qua Gateway, không join SQL xuyên service.
