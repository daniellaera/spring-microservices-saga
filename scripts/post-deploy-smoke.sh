#!/usr/bin/env bash
# Post-deploy smoke check, run on infra-node1 by main.yml right after a deploy.
# For every redeployed service: waits (bounded) for the container to be healthy, then checks that
# /actuator/health reports UP (shop-ui: that / answers 200). Read-only: it never creates data.
# Prints the health output and the last 50 log lines of every failing service, then exits 1.
# Usage: scripts/post-deploy-smoke.sh <service>...
set -uo pipefail

HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-300}"
POLL_SECONDS="${POLL_SECONDS:-5}"

# Host ports published by docker-compose.yml
port_of() {
  case "$1" in
    gateway-service) echo 8080 ;;
    order-service) echo 8081 ;;
    inventory-service) echo 8082 ;;
    payment-service) echo 8083 ;;
    auth-service) echo 8084 ;;
    notification-service) echo 8085 ;;
    cart-service) echo 8086 ;;
    audit-trail-service) echo 8087 ;;
    config-server) echo 8888 ;;
    shop-ui) echo 80 ;;
    *) return 1 ;;
  esac
}

container_health() {
  docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}} (no healthcheck){{end}}' \
    "$1" 2>/dev/null || echo "missing"
}

# Bounded wait: the compose healthcheck only reports after its first 30 s interval
wait_until_healthy() {
  local service="$1" deadline=$((SECONDS + HEALTH_TIMEOUT)) status
  while true; do
    status=$(container_health "$service")
    [[ "$status" == "healthy" ]] && return 0
    if ((SECONDS >= deadline)); then
      echo "container is '$status' after ${HEALTH_TIMEOUT}s"
      return 1
    fi
    sleep "$POLL_SECONDS"
  done
}

# Prints the endpoint's answer; succeeds only when it reports UP (or, for shop-ui, answers 200)
check_endpoint() {
  local service="$1" port="$2" body code
  if [[ "$service" == "shop-ui" ]]; then
    code=$(curl -sS -o /dev/null --max-time 10 -w '%{http_code}' "http://localhost:${port}/")
    echo "GET / -> ${code}"
    [[ "$code" == "200" ]]
    return
  fi
  body=$(curl -sS --max-time 10 "http://localhost:${port}/actuator/health") || { echo "no answer from :${port}"; return 1; }
  echo "$body"
  python3 -c 'import json, sys; sys.exit(0 if json.loads(sys.argv[1]).get("status") == "UP" else 1)' "$body"
}

report_failure() {
  local service="$1"
  echo "::error::${service} failed the post-deploy smoke check"
  echo "--- ${service}: container health ---"
  docker inspect --format '{{json .State.Health}}' "$service" 2>/dev/null \
    | python3 -m json.tool 2>/dev/null || echo "(no health information)"
  echo "--- ${service}: last 50 log lines ---"
  docker logs --tail 50 "$service" 2>&1 || true
}

if [[ $# -eq 0 ]]; then
  echo "No services redeployed: nothing to check"
  exit 0
fi

FAILED=()
for SERVICE in "$@"; do
  echo "=== ${SERVICE} ==="
  if ! PORT=$(port_of "$SERVICE"); then
    echo "unknown service: ${SERVICE}"
    FAILED+=("$SERVICE")
    continue
  fi
  if ! wait_until_healthy "$SERVICE"; then
    report_failure "$SERVICE"
    FAILED+=("$SERVICE")
    continue
  fi
  echo "container healthy"
  if ! check_endpoint "$SERVICE" "$PORT"; then
    report_failure "$SERVICE"
    FAILED+=("$SERVICE")
    continue
  fi
  echo "OK"
done

if ((${#FAILED[@]} > 0)); then
  echo "Smoke check FAILED for: ${FAILED[*]}"
  exit 1
fi
echo "Smoke check passed for: $*"
