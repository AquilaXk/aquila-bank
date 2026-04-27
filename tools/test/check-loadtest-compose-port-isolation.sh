#!/usr/bin/env bash
set -euo pipefail

echo "[loadtest-compose-port-isolation] compose contract"
compose_config="$(docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml config)"
grep -F 'published: "15432"' <<<"${compose_config}" >/dev/null
if grep -F 'published: "5432"' <<<"${compose_config}" >/dev/null; then
  echo "loadtest postgres still publishes the dev DB port 5432" >&2
  exit 1
fi

grep -F 'container_name: ${LOADTEST_POSTGRES_CONTAINER_NAME:-aquila-bank-postgres-loadtest}' compose.loadtest.yml >/dev/null
grep -F '${LOADTEST_DB_PORT:-15432}:5432' compose.loadtest.yml >/dev/null
grep -F 'container_name: ${LOADTEST_BACKEND_CONTAINER_NAME:-aquila-bank-backend-loadtest}' compose.loadtest.yml >/dev/null
grep -F '${LOADTEST_BACKEND_PORT:-18080}:8080' compose.loadtest.yml >/dev/null
grep -F '${LOADTEST_PROMETHEUS_PORT:-19090}:9090' compose.loadtest.yml >/dev/null
grep -F '${LOADTEST_GRAFANA_PORT:-13001}:3000' compose.loadtest.yml >/dev/null
grep -F '${LOADTEST_ALERTMANAGER_PORT:-19093}:9093' compose.loadtest.yml >/dev/null
grep -F '${LOADTEST_POSTGRES_EXPORTER_PORT:-19187}:9187' compose.loadtest.yml >/dev/null
grep -F 'BASE_URL: ${K6_BASE_URL:-http://aquila-bank-backend:8080}' compose.loadtest.yml >/dev/null

plan="$(
  LOADTEST_RUNNER_NAME=ci-k6 \
  LOADTEST_DB_PORT=25432 \
  LOADTEST_BACKEND_PORT=28080 \
  LOADTEST_PROMETHEUS_PORT=29090 \
  LOADTEST_GRAFANA_PORT=23001 \
  LOADTEST_ALERTMANAGER_PORT=29093 \
  LOADTEST_POSTGRES_EXPORTER_PORT=29187 \
    tools/test/run-k6-transaction-100m-loadtest.sh --print-plan
)"
grep -F "ports: db=25432 backend=28080 prometheus=29090 grafana=23001 alertmanager=29093 postgres-exporter=29187" <<<"${plan}" >/dev/null
grep -F "containers: postgres=aquila-bank-postgres-loadtest backend=aquila-bank-backend-loadtest" <<<"${plan}" >/dev/null
