# Internal auth: mapping và bàn giao Khoa — 28/10/2026

Approved public/internal v0.1 được giữ nguyên bytes/hash. Runtime User/Gateway kế thừa PR #1 sử dụng proposal v0.1.1. Việc kiểm thử nhánh này không phê duyệt proposal hoặc triển khai production.

| Mục | Approved v0.1 | Runtime/proposal hiện tại | Hành động |
|---|---|---|---|
| SessionCheckRequest | Chỉ token, additionalProperties=false | Bắt buộc token + requestMethod; csrfSessionId/csrfToken cho unsafe method | Không backward compatible. Giữ gate Huy/Khoa và quyết định nhóm; không âm thầm default GET hoặc bỏ CSRF |
| SessionPrincipal | UUID userId/jti, role, expiresAt, ACTIVE | User kiểm tra chữ ký, role/status/revoked; Gateway kiểm tra fields/expiry/status trước proxy | Response malformed/source unavailable => fail closed 503 |
| Service identity | Caller allowlist, issuer/audience/signature | RS256, key riêng mỗi service, service JWT 60s, fixed issuer, audience owner | Unit kiểm wrong audience/signature/expiry/caller; User JWT không là service identity |
| Public identity | Cookie/CSRF và RBAC | Gateway validate qua User, ký assertion audience owner/method/path, 15s | Browser Authorization/principal không được forward; Cookie chỉ tới User |
| Route | public /api/v1; internal private | 63 public operations từ approved spec, bốn owner rõ | Không fallback unknown owner sang Content; owner down =>503 |
| Retry start | Idempotency-Key | Forward nguyên header và cho phép preflight | Stub tests chỉ chứng minh proxy; Assessment thật chịu trách nhiệm mapping/retention/attempt |

## Bàn giao Huy: cấu hình không chứa secret

Service IDs: `api`, `user`, `content`, `assessment`, `progress`; issuer service là `group7-service:<id>`, aud bằng ID owner. Service private key chỉ mount vào chính service đó; public keys phân phối read-only. Gateway assertion issuer `group7-gateway`, audience là owner service, fields `sub`, `role`, `sessionJti`, `method`, `path`; không dùng assertion như user-signed payload.

| Owner API | Caller được phép | Mục đích |
|---|---|---|
| User /internal/v1/sessions/validate | api | Runtime đang yêu cầu proposal requestMethod/CSRF, không dùng request v0.1 token-only |
| User /internal/v1/users/{id}/learning-setup | assessment, progress | Course/Level snapshot; GET signed service identity |
| User /internal/v1/learners/cohort | progress | All LEARNER incl BLOCKED, source cohort/counts, page/size/filter |
| Content /internal/v1/courses/{id}/context | user, assessment, progress | Course published, levels, denominator gồm hidden lessons |
| Content /internal/v1/courses/contexts | progress | Page contexts nguồn Statistics/Progress |
| Content /internal/v1/lessons/{id}/context | assessment | Visibility và parent IDs |

Ví dụ service base URL chỉ là cấu hình môi trường: USER_URL, CONTENT_URL, ASSESSMENT_URL, PROGRESS_URL. Không public expose /internal/v1; không chia private key của api cho Huy. Sinh key test riêng, trao public keys và cấu hình issuer/audience qua kênh nhóm đã có; tài liệu này không gửi thông tin đăng nhập.

Unit/proxy HTTP tests ghi trong checkpoint. CSRF rotation/session/JTI, DB outage và LIVE integration phải chạy với User/PostgreSQL thật; không dùng stub để khép các mục đó. Huy review caller/security và quyết định compatibility/rollout còn OPEN (#42).
