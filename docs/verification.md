# Verification evidence — 14/10/2026 (Asia/Saigon)

**PASS:** [CI run 38436751397](https://github.com/KhoaTryHard/english-learning-platform/actions/runs/38436751397), tested application source `72cddb1d9f2715cfdad142950667503f7fc779f3`.

- 16 JUnit tests: 2 core validation, 5 User PostgreSQL/HTTP/security, 3 import boundary/fallback, 6 Content PostgreSQL/private API/concurrency tests. Zero failures, errors or skipped tests. [Summary](verification-junit.json)
- All 43 owner public operations passed response-schema checks through Gateway: 62 requests including CSRF/RBAC, hidden-parent saved vocabulary and logout failures. [Smoke report](verification-smoke.json)
- Fresh Docker Compose build/start, separate migration/runtime roles, Hibernate schema validation, Admin bootstrap and Course/Level seed passed.
- Isolated backup/restore of both databases preserved row counts, constraints and Flyway history; restart preserved account/setup/revocation/session/content/saved-relation counts. Gateway has no database credentials. [Operations report](verification-operations.json)
- Frozen public/internal v0.1 SHA-256 and owner operation inventory passed. Internal v0.1.1 remains a separate user-authorized implementation proposal requiring reviewer acceptance.

Local Java 21 compilation and core tests passed. Local Docker later stopped on an explicit insufficient C-drive disk-space error; complete runtime evidence above comes from GitHub Actions on Linux. No user Docker volume was deleted. Initial dependency-version, Windows CRLF/timezone, security bean and readiness/error-mapping failures were fixed before the passing run.

Assessment is **MOCK_ONLY** in this evidence. Real Assessment/Progress/Kafka, UI/Figma and full MVP acceptance are not certified. CI does not replace Huy/Hưng review, approve the internal proposal or close outstanding W3 design gates.
