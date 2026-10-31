# Backend

API Gateway và 4 service độc lập. Xem repository-layout.md và migration-plan.md.
Assessment hiện có implementation Java 25/Spring Boot 4.0.8, PostgreSQL 17 và
build độc lập trong `services/assessment-service`; xem README của service để chạy.
Các thư mục khác trong nhánh này vẫn là scaffold. Database/migration per service;
không shared business entity hoặc SQL xuyên service.
