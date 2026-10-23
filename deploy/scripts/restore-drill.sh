#!/bin/bash
# Monthly restore drill, and the disaster procedure (10-DEPLOYMENT section 9.3). Run on a SEPARATE machine, never on
# the production server, with the off-site read credential and the offline private key:
#   OWNER_USERNAME=... OWNER_PASSWORD=... ./deploy/scripts/restore-drill.sh --from <rclone remote> --key <age key file> \
#       --image <app image of the current release> [--label nightly] [--keep]
# Passes only when: health UP, reconciliation queries return no rows, ledger counts equal those at backup time, the
# owner can log in and read the dashboard, and the whole drill took at most 2 hours. Cleans up after itself, except with
# --keep: the restored copy then stays up on http://127.0.0.1:18080 (go-live rehearsal, deploy/import/README.md) until
# `docker rm -f drill-app drill-pg && docker network rm nexora-drill`.
set -euo pipefail
START=$(date +%s)
LABEL=nightly
KEEP=false
while [ $# -gt 0 ]; do
  case "$1" in
    --from) FROM="$2"; shift 2 ;;
    --key) KEY="$2"; shift 2 ;;
    --image) IMAGE="$2"; shift 2 ;;
    --label) LABEL="$2"; shift 2 ;;
    --keep) KEEP=true; shift ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done
: "${FROM:?--from is required}" "${KEY:?--key is required}" "${IMAGE:?--image is required}"
: "${OWNER_USERNAME:?set OWNER_USERNAME}" "${OWNER_PASSWORD:?set OWNER_PASSWORD}"
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"
NET=nexora-drill
OWNER_PW="$(openssl rand -hex 16)"
APP_PW="$(openssl rand -hex 16)"
log() { echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) drill: $*"; }
# shellcheck disable=SC2329  # called by the EXIT trap
cleanup() {
  if [ "$KEEP" = false ]; then
    docker rm -f drill-app drill-pg > /dev/null 2>&1 || true
    docker network rm "$NET" > /dev/null 2>&1 || true
  fi
  shred -u "$WORK"/restore.dump 2> /dev/null || true
  rm -rf "$WORK"
}
trap cleanup EXIT

# 1. newest backup of the label, checked against its checksums
NAME="$(rclone lsf "$FROM" --include "nexora-$LABEL-*.dump.age" | sort | tail -1)"
[ -n "$NAME" ] || { log "no nexora-$LABEL-*.dump.age in $FROM"; exit 1; }
BASE="${NAME%.dump.age}"
log "restoring $BASE"
for suffix in dump.age documents.tar.age counts sha256; do
  rclone copyto "$FROM/$BASE.$suffix" "$WORK/$BASE.$suffix"
done
(cd "$WORK" && sha256sum -c "$BASE.sha256")

# 2. decrypt with the offline private key
age -d -i "$KEY" "$WORK/$BASE.dump.age" > "$WORK/restore.dump"

# 3-4. an empty PostgreSQL 17 with the production roles, then the dump restored as the schema owner
docker network create "$NET" > /dev/null
docker run -d --name drill-pg --network "$NET" -e POSTGRES_DB=nexora_db -e POSTGRES_PASSWORD="$(openssl rand -hex 16)" \
  -e OWNER_DB_PASSWORD="$OWNER_PW" -e APP_DB_PASSWORD="$APP_PW" \
  -v "$HERE/../postgres/10-roles.sh:/docker-entrypoint-initdb.d/10-roles.sh:ro" postgres:17 > /dev/null
for _ in $(seq 1 60); do
  docker exec drill-pg pg_isready -U postgres -d nexora_db > /dev/null 2>&1 \
    && docker exec drill-pg psql -U postgres -d nexora_db -tAc "SELECT 1 FROM pg_roles WHERE rolname = 'nexora_app'" \
      2> /dev/null | grep -q 1 && break
  sleep 2
done
docker exec -i drill-pg pg_restore -U postgres -d nexora_db --no-owner --role=nexora_owner --exit-on-error \
  < "$WORK/restore.dump"

# 5. the current release against it, as in production (it must find the schema up to date)
docker run -d --name drill-app --network "$NET" -p 127.0.0.1:18080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod -e DB_URL=jdbc:postgresql://drill-pg:5432/nexora_db \
  -e DB_USERNAME=nexora_app -e DB_PASSWORD="$APP_PW" -e FLYWAY_USER=nexora_owner -e FLYWAY_PASSWORD="$OWNER_PW" \
  -e JWT_SECRET="$(openssl rand -base64 48)" "$IMAGE" > /dev/null

# 6. pass criteria
FAIL=0
HEALTH=""
for _ in $(seq 1 60); do
  HEALTH="$(curl -fsS http://127.0.0.1:18080/actuator/health 2> /dev/null || true)"
  echo "$HEALTH" | grep -q '"status":"UP"' && break
  sleep 2
done
if echo "$HEALTH" | grep -q '"status":"UP"'; then log "(a) health UP"; else log "(a) FAILED: health $HEALTH"; FAIL=1; fi
ROWS="$(docker exec -i drill-pg psql -U postgres -d nexora_db -tA -v ON_ERROR_STOP=1 < "$HERE/reconciliation.sql")"
if [ -z "$ROWS" ]; then log "(b) reconciliation: no rows"; else log "(b) FAILED: $ROWS"; FAIL=1; fi
NOW="$(docker exec drill-pg psql -U postgres -d nexora_db -tA -c \
  "SELECT 'financial_transactions=' || (SELECT count(*) FROM financial_transactions)
   UNION ALL SELECT 'material_movements=' || (SELECT count(*) FROM material_movements)")"
if [ "$NOW" = "$(cat "$WORK/$BASE.counts")" ]; then
  log "(c) counts match: $(echo "$NOW" | tr '\n' ' ')"
else
  log "(c) FAILED: restored $NOW, backup $(cat "$WORK/$BASE.counts")"; FAIL=1
fi
TOKEN="$(curl -fsS http://127.0.0.1:18080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"$OWNER_USERNAME\",\"password\":\"$OWNER_PASSWORD\"}" 2> /dev/null \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
CODE="$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  http://127.0.0.1:18080/api/v1/dashboard/summary)"
if [ "$CODE" = 200 ]; then log "(d) owner login and dashboard 200"; else log "(d) FAILED: dashboard $CODE"; FAIL=1; fi
ELAPSED=$(( $(date +%s) - START ))
if [ "$ELAPSED" -le 7200 ]; then log "(e) elapsed ${ELAPSED}s"; else log "(e) FAILED: ${ELAPSED}s > 2 h"; FAIL=1; fi

# 7. the result line for docs/RUNBOOK.md (the cleanup trap destroys the drill database and the decrypted dump)
if [ "$FAIL" = 0 ]; then
  log "PASSED $BASE in ${ELAPSED}s"
else
  log "FAILED $BASE"
fi
exit "$FAIL"
