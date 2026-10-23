#!/bin/bash
# Runs once, when the PostgreSQL data directory is empty (docker-entrypoint-initdb.d).
# Two roles (NFR-SEC-08, ADR-032): nexora_owner owns the schema and is used only by Flyway; nexora_app is the running
# application and can read and write rows but never change the schema. The ledger tables lose UPDATE/DELETE/TRUNCATE
# for nexora_app in migration V15, once they exist.
set -euo pipefail
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
CREATE ROLE nexora_owner LOGIN PASSWORD '${OWNER_DB_PASSWORD}';
CREATE ROLE nexora_app   LOGIN PASSWORD '${APP_DB_PASSWORD}';
ALTER DATABASE ${POSTGRES_DB} OWNER TO nexora_owner;
ALTER SCHEMA public OWNER TO nexora_owner;
GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO nexora_app;
GRANT USAGE ON SCHEMA public TO nexora_app;
-- every table/sequence Flyway creates later is usable by the app (the ledger REVOKEs are in migration V15)
ALTER DEFAULT PRIVILEGES FOR ROLE nexora_owner IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO nexora_app;
ALTER DEFAULT PRIVILEGES FOR ROLE nexora_owner IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO nexora_app;
SQL
