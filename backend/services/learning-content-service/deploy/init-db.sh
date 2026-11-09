#!/bin/sh
set -eu
runtimepass=$(cat /run/secrets/content_runtime)
migrationpass=$(cat /run/secrets/content_migration)
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=rp="$runtimepass" --set=mp="$migrationpass" <<'SQL'
CREATE ROLE content_migration LOGIN PASSWORD :'mp';
CREATE ROLE content_runtime LOGIN PASSWORD :'rp';
ALTER DATABASE content_db OWNER TO content_migration;
REVOKE ALL ON DATABASE content_db FROM PUBLIC;
GRANT CONNECT ON DATABASE content_db TO content_migration,content_runtime;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO content_migration;
SQL
