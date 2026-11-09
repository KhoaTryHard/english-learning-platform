# Implementation proposal 0.1.1 — 13/10/2026

Frozen public/internal v0.1 are preserved byte-for-byte; SHA-256 recorded in baseline-sha256.json.
User-authorized proposal, awaiting actual Huy/Khoa architecture/security review. No team approval is inferred.

Only internal SessionCheckRequest changes: required requestMethod plus csrfSessionId/csrfToken for unsafe methods. Response unchanged. Coordinated User/Gateway deployment required; old clients are not backward compatible. Public API remains v0.1.

API-06 implementation choices: RS256, JDBC CSRF session, 15-minute anonymous session, authenticated session bounded by JWT expiry, service JWT 60s, gateway principal assertion 15s, fixed audience/caller allowlist and private network. Key distribution/rotation and HTTPS deployment require reviewer acceptance before production integration.
