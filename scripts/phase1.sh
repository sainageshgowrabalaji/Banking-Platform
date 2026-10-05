#!/usr/bin/env bash
# Starts and stops the four services of phase 1 in the background.
#
#   scripts/phase1.sh start     build, start, and wait until each one is healthy
#   scripts/phase1.sh status    which ones are up
#   scripts/phase1.sh stop
#
# PostgreSQL and Kafka must be running first, for example from infra/docker-compose.yml.
# Logs go to .run/<service>.log
#
# Settings, all optional
#   GATEWAY_SECURITY=false                  no Keycloak, the gateway lets every request through
#   OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318   send traces, metrics and logs to Grafana
set -uo pipefail

cd "$(dirname "$0")/.."
run_dir=".run"
services="ledger-service payments-service notification-service api-gateway"

port_of() {
  case "$1" in
    ledger-service) echo 8101 ;;
    payments-service) echo 8102 ;;
    notification-service) echo 8103 ;;
    api-gateway) echo 8080 ;;
  esac
}

healthy() {
  curl -fsS --max-time 2 "http://localhost:$(port_of "$1")/actuator/health" 2>/dev/null | grep -q '"status":"UP"'
}

running() {
  [ -f "$run_dir/$1.pid" ] && kill -0 "$(cat "$run_dir/$1.pid")" 2>/dev/null
}

start() {
  mkdir -p "$run_dir"
  echo "Building"
  ./mvnw -q -ntp -pl services/ledger-service,services/payments-service,services/notification-service,services/api-gateway \
    -am package -DskipTests || { echo "The build failed"; exit 1; }

  for service in $services; do
    if running "$service"; then
      echo "$service is already running"
      continue
    fi
    nohup java -jar services/"$service"/target/"$service"-*.jar > "$run_dir/$service.log" 2>&1 &
    echo $! > "$run_dir/$service.pid"
  done

  failed=0
  for service in $services; do
    printf '%-22s ' "$service"
    up=no
    for _ in $(seq 1 90); do
      if healthy "$service"; then up=yes; break; fi
      running "$service" || break
      sleep 1
    done
    if [ "$up" = yes ]; then
      echo "up on port $(port_of "$service")"
    else
      failed=1
      echo "did not start. The last lines of $run_dir/$service.log"
      tail -n 15 "$run_dir/$service.log" | sed 's/^/    /'
    fi
  done
  [ "$failed" -eq 0 ] && echo "Ready. Try scripts/demo.sh"
  return "$failed"
}

stop() {
  for service in $services; do
    if running "$service"; then
      kill "$(cat "$run_dir/$service.pid")"
      echo "Stopped $service"
    fi
    rm -f "$run_dir/$service.pid"
  done
}

status() {
  for service in $services; do
    printf '%-22s ' "$service"
    if healthy "$service"; then echo "up on port $(port_of "$service")"; else echo "down"; fi
  done
}

case "${1:-}" in
  start) start ;;
  stop) stop ;;
  status) status ;;
  *) echo "Use: scripts/phase1.sh start | status | stop"; exit 1 ;;
esac
