# Verification evidence — Khoa implementation

Scope: User Service, Learning Content Service and minimal Gateway. PostgreSQL 17, Java 21. Assessment dependency is mocked; Progress/Kafka/Figma are not certified by this package.

## Local checks

- Java 21 Maven reactor package: successful compilation of all applications and tests.
- platform-core JUnit validation tests: 2 passed.
- Frozen public/internal v0.1 SHA-256 and inventory (User 11 / Content 32 public operations): passed.
- Docker image build: completed before subsequent security/test fixes; final image is verified by CI.
- Initial PostgreSQL Testcontainers run exposed incompatible module versions; migrated to consistent Testcontainers 2.0.5.
- Windows JVM timezone Asia/Saigon was rejected by PostgreSQL image; tests/runtime now use UTC.
- Docker initialization CRLF failure was corrected, shell files use LF; existing task-created databases were provisioned without deleting volumes.
- Docker Desktop later stopped with an explicit insufficient-disk-space error on drive C. Local integration did not complete; no PostgreSQL/Gateway success is claimed here until CI evidence is recorded.

## Automated verification

GitHub Actions runs Maven tests against real PostgreSQL Testcontainers, baseline checks, Docker Compose, admin/Level provisioning and scripts/smoke.py. Smoke validates response schemas and all 43 public owner operations plus RBAC/CSRF/hidden-content/logout failures. The report records requests/status/operation names only, never passwords or cookies.

Runtime review issues require actual test evidence and correct reviewer acceptance before closure. CI results will be appended with the exact run link and commit, not substituted for Huy/Hưng approval or full MVP integration.
