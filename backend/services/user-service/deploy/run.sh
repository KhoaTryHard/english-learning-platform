#!/bin/sh
set -eu
if [ -n "${DB_PASSWORD_FILE:-}" ]; then export DB_PASSWORD="$(cat "$DB_PASSWORD_FILE")"; fi
if [ -n "${ADMIN_PASSWORD_FILE:-}" ]; then export ADMIN_PASSWORD="$(cat "$ADMIN_PASSWORD_FILE")"; fi
if [ "${PLATFORM_MODE:-serve}" != serve ]; then
 exec java -jar /app/app.jar --spring.main.web-application-type=none --spring.jpa.hibernate.ddl-auto=none --management.health.db.enabled=false
fi
exec java -jar /app/app.jar
