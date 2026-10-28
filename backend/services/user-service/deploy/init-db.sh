#!/bin/sh
set -eu
runtimepass=$(cat /run/secrets/user_runtime)
migrationpass=$(cat /run/secrets/user_migration)
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=rp="$runtimepass" --set=mp="$migrationpass" <<'SQL'
CREATE ROLE user_migration LOGIN PASSWORD :'mp';
CREATE ROLE user_runtime LOGIN PASSWORD :'rp';
ALTER DATABASE user_db OWNER TO user_migration;
REVOKE ALL ON DATABASE user_db FROM PUBLIC;
GRANT CONNECT ON DATABASE user_db TO user_migration,user_runtime;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO user_migration;
SQL
