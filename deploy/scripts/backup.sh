#!/bin/bash
# Encrypted backup (10-DEPLOYMENT section 9.1). Cron on the server: 30 21 * * * (03:00 IST) for the nightly one;
# deploy.sh runs it with --label pre-deploy-<tag>. Usage: ./scripts/backup.sh [--label name]
# Writes, encrypted with the PUBLIC key before anything touches the disk:
#   nexora-<label>-<stamp>.dump.age            the whole database (pg_dump custom format)
#   nexora-<label>-<stamp>.documents.tar.age   the uploaded documents
#   nexora-<label>-<stamp>.counts              ledger row counts at backup time (the restore drill compares them)
# plus a .sha256 of the three, copies all to the off-site remote, keeps 7 days locally, pings the dead-man's switch.
set -euo pipefail
cd "$(dirname "$0")/.."

LABEL=nightly
if [ "${1:-}" = "--label" ]; then
  LABEL="${2:?--label needs a name}"
fi
env_value() { grep -E "^$1=" .env.prod | cut -d= -f2-; }
DB_NAME="$(env_value DB_NAME)"
AGE_PUBLIC_KEY="$(env_value AGE_PUBLIC_KEY)"
RCLONE_REMOTE="$(env_value RCLONE_REMOTE)"
HEALTHCHECK_PING_URL="$(env_value HEALTHCHECK_PING_URL)"
: "${AGE_PUBLIC_KEY:?AGE_PUBLIC_KEY is not set in .env.prod}"
: "${RCLONE_REMOTE:?RCLONE_REMOTE is not set in .env.prod}"

COMPOSE=(docker compose -f docker-compose.prod.yml --env-file .env.prod)
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p backups
BASE="backups/nexora-$LABEL-$STAMP"

"${COMPOSE[@]}" exec -T postgres pg_dump -U postgres -d "$DB_NAME" -Fc --no-owner \
  | age -r "$AGE_PUBLIC_KEY" > "$BASE.dump.age"
# a one-off container of the app image, so the documents are saved even while the app is stopped (restore, rollback)
"${COMPOSE[@]}" run --rm -T --no-deps --entrypoint tar app -C /var/lib/nexora/documents -cf - . \
  | age -r "$AGE_PUBLIC_KEY" > "$BASE.documents.tar.age"
"${COMPOSE[@]}" exec -T postgres psql -U postgres -d "$DB_NAME" -tA -c \
  "SELECT 'financial_transactions=' || (SELECT count(*) FROM financial_transactions)
   UNION ALL SELECT 'material_movements=' || (SELECT count(*) FROM material_movements)" > "$BASE.counts"

test "$(stat -c %s "$BASE.dump.age")" -gt 1024                     # a dump smaller than 1 KiB is a failure
(cd backups && sha256sum "$(basename "$BASE").dump.age" "$(basename "$BASE").documents.tar.age" \
  "$(basename "$BASE").counts" > "$(basename "$BASE").sha256")
for file in "$BASE.dump.age" "$BASE.documents.tar.age" "$BASE.counts" "$BASE.sha256"; do
  rclone copyto "$file" "$RCLONE_REMOTE/$(basename "$file")"       # write-only credential; the bucket keeps 30 days
done
find backups -type f -mtime +7 -delete                               # 7 days on the server itself
if [ -n "$HEALTHCHECK_PING_URL" ]; then
  curl -fsS -m 10 --retry 3 "$HEALTHCHECK_PING_URL" > /dev/null    # silence for more than 26 h raises an alert
fi
echo "backup $BASE: $(tr '\n' ' ' < "$BASE.counts")"
