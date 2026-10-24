#!/bin/bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
docker exec -i postgres-nexora psql -U postgres -d nexora_db < "$DIR/seed-dev-data.sql"
echo "Successfully restored development seed data to postgres-nexora (nexora_db on port 5431)!"
