#!/bin/bash
# Deploy one image tag on the server (10-DEPLOYMENT section 7.1). Run from the deploy directory (/opt/nexora):
#   ./scripts/deploy.sh <image_tag>
# Steps: fresh backup first; switch IMAGE_TAG; start (the app migrates on start); wait for healthy; smoke test over
# HTTPS. On any failure after the switch the previous tag is started again and the script exits non-zero.
set -euo pipefail

TAG="${1:-}"
if [ -z "$TAG" ]; then
  echo "usage: $0 <image_tag>" >&2
  exit 2
fi
cd "$(dirname "$0")/.."
COMPOSE=(docker compose -f docker-compose.prod.yml --env-file .env.prod)
PREVIOUS_TAG="$(grep -E '^IMAGE_TAG=' .env.prod | cut -d= -f2-)"
APP_DOMAIN="$(grep -E '^APP_DOMAIN=' .env.prod | cut -d= -f2-)"
SMOKE_CURL_OPTS="${SMOKE_CURL_OPTS:-}"      # only for a local rehearsal against Caddy's internal CA (-k)

log() { echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) deploy: $*"; }

set_tag() {
  sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=$1/" .env.prod
}

wait_healthy() {
  local id status
  for _ in $(seq 1 60); do
    id="$("${COMPOSE[@]}" ps -q app)"
    status="$(docker inspect -f '{{.State.Health.Status}}' "$id" 2>/dev/null || echo starting)"
    [ "$status" = healthy ] && return 0
    [ "$status" = unhealthy ] && return 1
    sleep 2
  done
  return 1
}

# through Caddy and HTTPS, as a user would; Caddy may need a few seconds after a (re)start, so retry for up to 60 s
smoke_test() {
  for _ in $(seq 1 30); do
    # shellcheck disable=SC2086
    if curl -fsS $SMOKE_CURL_OPTS "https://$APP_DOMAIN/actuator/health" | grep -q '"status":"UP"' \
        && [ "$(curl -s $SMOKE_CURL_OPTS -o /dev/null -w '%{http_code}' "https://$APP_DOMAIN/api/v1/parties")" = 401 ]; then
      return 0
    fi
    sleep 2
  done
  return 1
}

start_and_check() {
  "${COMPOSE[@]}" pull --policy missing app \
    && "${COMPOSE[@]}" up -d \
    && wait_healthy \
    && smoke_test
}

log "deploying $TAG (current: $PREVIOUS_TAG)"
"${COMPOSE[@]}" up -d --wait postgres
DB_NAME="$(grep -E '^DB_NAME=' .env.prod | cut -d= -f2-)"
if [ "$("${COMPOSE[@]}" exec -T postgres psql -U postgres -d "$DB_NAME" -tAc \
    "SELECT to_regclass('public.flyway_schema_history') IS NOT NULL")" = t ]; then
  log "pre-deploy backup"
  ./scripts/backup.sh --label "pre-deploy-$TAG"      # no deploy without a fresh backup (set -e stops here)
else
  log "first installation: the database is empty, nothing to back up"
fi

set_tag "$TAG"
if start_and_check; then
  echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) $TAG ok (previous $PREVIOUS_TAG)" >> .deploy-history
  log "$TAG is live"
  exit 0
fi

log "$TAG failed the health or smoke check: rolling back to $PREVIOUS_TAG"
set_tag "$PREVIOUS_TAG"
if "${COMPOSE[@]}" up -d && wait_healthy; then
  log "rolled back to $PREVIOUS_TAG"
else
  log "ROLLBACK FAILED: $PREVIOUS_TAG is not healthy either; see docs/RUNBOOK.md (restore)"
fi
echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) $TAG FAILED, rolled back to $PREVIOUS_TAG" >> .deploy-history
exit 1
