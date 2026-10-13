# English Learning Platform — Khoa implementation

Java 21 / Spring Boot 4.0.8 / PostgreSQL 17. User Service and Learning Content Service own separate databases. Gateway exposes 43 public operations; 6 internal operations remain private. Assessment is mocked only in the demo profile.

## Independently deployable folders

```text
user-service/              # standalone Maven app + Dockerfile + compose + user_db
learning-content-service/  # standalone Maven app + Dockerfile + compose + content_db
api-gateway/               # separate gateway image; no business database
shared/platform-core/      # canonical infrastructure SDK snapshot, no business entities
```

You can copy either service folder to another machine and build it there: `./mvnw.cmd verify` or `docker build -t group7-user-service:local .` inside `user-service/`; use `group7-learning-content-service:local` in `learning-content-service/`. Each application inherits directly from Spring Boot and includes its SDK snapshot, migrations, templates/test fixtures, wrapper and deployment files. It does not need a sibling folder, repository parent POM or unpublished SDK JAR. See the README inside each folder for peer URLs/public keys and standalone database provisioning.

Root Maven/Compose are optional integration conveniences. Root Compose now uses three different images, each containing only its owner's executable JAR; database volume names are preserved. SDK updates are explicit and checked with `python scripts/sync-sdk.py --check`.

## Run locally

Requirements: Docker Desktop (Linux containers), OpenSSL and PowerShell 7 or bash. For local Maven tests also install JDK 21.

```powershell
./scripts/init-dev.ps1
docker compose build user content api
docker compose --profile demo up -d --no-build
./scripts/bootstrap-admin.ps1 -Email admin@example.com
./scripts/seed-content.ps1
```

Gateway: http://localhost:8080. Frontend Origin default: http://localhost:5173. Admin password is in the ignored local `.secrets/admin_password` file; do not copy it into Git or API reports. CLI provisioning exits after completion; seeded Course is a draft, Levels belong only to Course.

```powershell
$env:JAVA_HOME='C:/path/to/jdk-21'
./mvnw.cmd verify
python -m pip install -r scripts/requirements-dev.txt
python scripts/smoke.py
```

For Linux/macOS use `bash scripts/init-dev.sh`, then the same Docker/Maven commands. Use `.env` for FRONTEND_ORIGIN, SECURE_COOKIES, MEDIA_HOSTS, ASSESSMENT_URL and GATEWAY_PORT. Configure HTTPS and SECURE_COOKIES=true for deployment. The development mock is not a production fallback.

## Database access

Default compose exposes only Gateway. `compose.debug.yaml` binds PostgreSQL to localhost:5541/5542 for inspection. Use separate migration/runtime credentials from `.secrets`. Services cannot join the other database network. Bootstrap roles are never mounted in application containers. Flyway migration containers run before runtime; Hibernate validates the mapped schema.

## Delivery and review

See [architecture](docs/architecture.md), [data dictionary](docs/database.md), [contract change](docs/contracts/CHANGELOG.md), [traceability](docs/traceability.md), [operations](docs/operations.md), and [verification evidence](docs/verification.md).

W3-00 v0.2 and public/internal v0.1 are frozen. Internal v0.1.1 is a user-authorized implementation proposal; it requires coordinated User/Gateway deployment and actual Huy/Khoa review. MySQL→PostgreSQL applies to Khoa's two services only. Nothing here signs reviewer acceptance or closes the full W3/MVP gate.
