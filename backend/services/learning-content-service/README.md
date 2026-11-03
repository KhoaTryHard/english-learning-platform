# learning-content-service — deploy independently

This folder is a standalone Java 25 / Spring Boot 4.0.8 application. Its Maven parent is Spring Boot from Maven Central; it does not require the repository parent POM, sibling services or a locally installed platform-core JAR. Infrastructure SDK sources/resources are vendored here and checked against the versioned snapshot in the parent repository.

```powershell
./mvnw.cmd verify
docker build -t group7-learning-content-service:local .
./deploy/init-dev.ps1
```

Before starting, provision trusted peer public keys **api.public.pem, user.public.pem, assessment.public.pem, progress.public.pem** into `.secrets/keys/public/`. Each service signs only with its own private key. Obtain public keys from the same Gateway/service deployments; independently generated peer key pairs will not authenticate each other. The parent repository's development setup provisions a matching set for the full local stack.

Copy `.env.example` to `.env` and configure real peer URLs, frontend origin, TLS cookie policy, media hosts and bind address. By default the app is exposed on loopback port 8082; bind your private deployment interface for remote Gateway access. Internal and public service APIs still require caller authentication; browsers enter through Gateway.

```powershell
docker compose up -d --build --wait
```

PostgreSQL 17 uses its own volume and bootstrap/migration/runtime credentials. A one-shot migration runs before the runtime app.
Course/Level CLI: `docker compose run --rm content-seed`. Input is deploy/course-levels.json; seed remains draft.

For Linux/macOS use `bash deploy/init-dev.sh` and `bash mvnw verify`. `docker compose down` retains database volumes. This standalone deployment creates its own Compose project/volumes; migrating an existing deployment must preserve or explicitly restore its data. The parent compose continues to use its original volume names.
