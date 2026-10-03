#!/usr/bin/env bash
# Asks every running service who it is, directly and then through the gateway.
# Start the services first, each in its own terminal, with scripts/run.sh.
set -uo pipefail

check() {
  printf '%-34s ' "$1"
  curl -fsS --max-time 3 "$1" 2>/dev/null || printf 'not running'
  echo
}

echo "Direct"
for port in 8101 8102 8103 8201 8301 8302 8303 8304 8401 8501; do
  check "http://localhost:$port/v1/info"
done

echo
echo "Through the gateway"
for route in ledger payments notifications compliance market-data trading positions post-trade wealth ops-agent; do
  check "http://localhost:8080/$route/v1/info"
done
