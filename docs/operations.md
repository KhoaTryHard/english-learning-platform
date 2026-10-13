# Operations

Secrets: run scripts/init-dev.ps1 or init-dev.sh before Compose. They preserve existing credentials. Production uses a secret manager and caller-specific private keys. Each app mounts the public-key directory plus its own private key only. Never mount bootstrap/migration credentials in runtime. Access cookies require configured frontend origin and HTTPS policy.

Database initialization scripts create dedicated migration/runtime roles; they run only on a new volume. Flyway runs in separate one-shot containers using migration credentials. Restarting runtime does not run DDL. New schema revisions use new versioned migrations; do not edit applied migrations.

CLI tools: `docker compose run --rm -e ADMIN_EMAIL=admin@example.com admin-bootstrap`; `docker compose run --rm content-seed`. A pre-existing Learner email cannot be escalated. Admin bootstrap preserves an existing Admin without resetting password. Course/Level seed is atomic and preserves existing records; seed IDs must remain stable. Change Course/Level catalog through an explicit seed file/review, not public API expansion.

Backups: `docker compose exec -T user-db pg_dump -U postgres -d user_db -Fc > user.dump`; likewise content-db/content_db. PowerShell 7.4+ preserves native binary redirects; on older shells use pg_dump to a container file and docker cp. Keep dumps outside Git in secure storage. Restore into a separately provisioned empty database using pg_restore --no-owner --no-privileges, then apply role grants as migration owner. Do not restore over a live database. Validation should compare row counts, FK/unique constraints, Flyway history and fresh app schema validation. Verification records the isolated round-trip actually tested.

Restart: `docker compose --profile demo restart user content api`. Named volumes retain accounts, setup, saved references and session/revocation state. `docker compose down` stops services without removing volumes. Volume deletion is not a routine step.

Review gates: internal v0.1.1 is a proposal; API-06 requires reviewer acceptance. Assessment mock never establishes real Assessment/Progress/Kafka readiness. W3-03 schema and W3-06 full design acceptance remain owner review tasks. No deadline or approval is fabricated by bootstrap/tests.
