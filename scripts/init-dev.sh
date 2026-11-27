#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p .secrets/keys/public
chmod 700 .secrets .secrets/keys
chmod 755 .secrets/keys/public
for name in api user content assessment progress; do
 [ -f ".secrets/keys/$name.private.pem" ] || openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out ".secrets/keys/$name.private.pem" 2>/dev/null
 [ -f ".secrets/keys/public/$name.public.pem" ] || openssl pkey -in ".secrets/keys/$name.private.pem" -pubout -out ".secrets/keys/public/$name.public.pem"
 chmod 644 ".secrets/keys/$name.private.pem" ".secrets/keys/public/$name.public.pem"
done
for name in user_bootstrap user_migration user_runtime content_bootstrap content_migration content_runtime admin_password; do
 [ -f ".secrets/$name" ] || openssl rand -hex 32 > ".secrets/$name"
done
echo 'Local ignored secrets ready; existing values preserved.'
