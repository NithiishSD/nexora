#!/bin/bash
# Host check (10-DEPLOYMENT section 11), cron every 5 minutes on the server:  */5 * * * * /opt/nexora/scripts/host-check.sh
# Alerts go to ALERT_WEBHOOK_URL (a plain POST, e.g. an ntfy topic that pushes to the owner's and developer's phones),
# or to syslog when it is empty. The site-down alert from outside and the backup dead-man's switch are external services.
#   - app liveness (inside the container: no port is published); restart after 3 consecutive failures
#   - root disk > 80 % (warning) / > 90 % (critical)
#   - the app restarting more than 2 times within an hour (out of memory)
#   - more than 5 HTTP 5xx, or more than 20 HTTP 401 from one address, in the last 5 minutes (Caddy's JSON access log)
#   - any refresh_token_reuse in the app log in the last 5 minutes
#   - the TLS certificate expiring within 14 days
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
COMPOSE=(docker compose -f docker-compose.prod.yml --env-file .env.prod)
STATE=.host-check
mkdir -p "$STATE"
env_value() { grep -E "^$1=" .env.prod | cut -d= -f2-; }
APP_DOMAIN="$(env_value APP_DOMAIN)"
ALERT_WEBHOOK_URL="$(env_value ALERT_WEBHOOK_URL)"
NOW=$(date +%s)

# alert <kind> <severity> <message>: one alert per kind and hour (the check runs every 5 minutes; no phone spam),
# except the kind "restart", which is always sent
alert() {
  local kind="$1" severity="$2" message="$3" stamp="$STATE/sent-$1"
  if [ "$kind" != restart ] && [ -f "$stamp" ] && [ $((NOW - $(stat -c %Y "$stamp"))) -lt 3600 ]; then
    return 0
  fi
  touch "$stamp"
  logger -t nexora-host-check "$severity: $message" 2> /dev/null || true
  echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) $severity: $message"
  if [ -n "$ALERT_WEBHOOK_URL" ]; then
    curl -fsS -m 10 --retry 2 -H "Title: Nexora $severity" -d "$message" "$ALERT_WEBHOOK_URL" > /dev/null || true
  fi
}

# --- liveness ---
if "${COMPOSE[@]}" exec -T app curl -fsS -m 5 http://localhost:8080/actuator/health/liveness 2> /dev/null \
    | grep -q '"status":"UP"'; then
  echo 0 > "$STATE/liveness-failures"
else
  failures=$(( $(cat "$STATE/liveness-failures" 2> /dev/null || echo 0) + 1 ))
  echo "$failures" > "$STATE/liveness-failures"
  if [ "$failures" -ge 3 ]; then
    alert restart critical "app liveness failed $failures times in a row: restarting the app container"
    "${COMPOSE[@]}" up -d app > /dev/null 2>&1 && "${COMPOSE[@]}" restart app > /dev/null 2>&1
    echo 0 > "$STATE/liveness-failures"
  fi
fi

# --- disk ---
used=$(df -P / | awk 'NR == 2 { gsub("%", "", $5); print $5 }')
if [ "$used" -gt 90 ]; then
  alert disk critical "root disk ${used}% used"
elif [ "$used" -gt 80 ]; then
  alert disk warning "root disk ${used}% used"
fi

# --- restarts (out of memory) ---
id="$("${COMPOSE[@]}" ps -q app 2> /dev/null)"
if [ -n "$id" ]; then
  count=$(docker inspect -f '{{.RestartCount}}' "$id")
  last=$(cat "$STATE/restart-count" 2> /dev/null || echo "$count")
  if [ "$count" -gt "$last" ]; then
    for _ in $(seq $((count - last))); do echo "$NOW" >> "$STATE/restarts"; done
  fi
  echo "$count" > "$STATE/restart-count"
  recent=$(awk -v since=$((NOW - 3600)) '$1 >= since' "$STATE/restarts" 2> /dev/null | wc -l)
  if [ "$recent" -gt 2 ]; then
    alert memory critical "app restarted $recent times in the last hour (out of memory?)"
  fi
fi

# --- errors and authentication (Caddy JSON access log, last 5 minutes) ---
"${COMPOSE[@]}" exec -T caddy cat /data/access.log 2> /dev/null | awk -v since=$((NOW - 300)) '
  { ts = ""; if (match($0, /"ts":[0-9.]+/)) ts = substr($0, RSTART + 5, RLENGTH - 5) }
  ts != "" && ts + 0 >= since {
    status = ""; if (match($0, /"status":[0-9]+/)) status = substr($0, RSTART + 9, RLENGTH - 9) + 0
    ip = ""; if (match($0, /"remote_ip":"[^"]*"/)) ip = substr($0, RSTART + 13, RLENGTH - 14)
    if (status >= 500) errors++
    if (status == 401) unauthorized[ip]++
  }
  END {
    if (errors > 5) print "errors|critical|" errors " HTTP 5xx responses in 5 minutes"
    for (ip in unauthorized) if (unauthorized[ip] > 20) print "auth-" ip "|warning|" unauthorized[ip] " HTTP 401 from " ip " in 5 minutes"
  }' | while IFS='|' read -r kind severity message; do alert "$kind" "$severity" "$message"; done
reuse=$("${COMPOSE[@]}" logs --since 5m app 2> /dev/null | grep -c refresh_token_reuse)
if [ "$reuse" -gt 0 ]; then
  alert token-reuse warning "refresh token reuse detected $reuse times in 5 minutes (stolen token?)"
fi

# --- certificate ---
if [ -n "$APP_DOMAIN" ]; then
  end=$(echo | openssl s_client -servername "$APP_DOMAIN" -connect "$APP_DOMAIN:443" 2> /dev/null \
    | openssl x509 -noout -enddate 2> /dev/null | cut -d= -f2)
  if [ -n "$end" ] && [ $(( ($(date -d "$end" +%s) - NOW) / 86400 )) -lt 14 ]; then
    alert certificate warning "TLS certificate for $APP_DOMAIN expires on $end"
  fi
fi
exit 0
