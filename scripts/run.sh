#!/usr/bin/env bash
# Builds one service with the libraries it needs, then starts it.
#
#   scripts/run.sh ledger-service
#   scripts/run.sh api-gateway
set -euo pipefail

service="${1:?Give a service name, like: scripts/run.sh ledger-service}"
cd "$(dirname "$0")/.."

if [ ! -d "services/$service" ]; then
  echo "No service called $service. Pick one of these:"
  ls services
  exit 1
fi

./mvnw -q -ntp -pl "services/$service" -am package -DskipTests
exec java -jar services/"$service"/target/"$service"-*.jar
