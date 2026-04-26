#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-jdbc-mapper-allocation.sh [--print-plan]

Wraps run-transaction-read-hotpath-profile.sh with a row-mapper allocation preset.
USAGE
}

mode="run"
if [[ "${1:-}" == "--print-plan" ]]; then
  mode="print-plan"
  shift
elif [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi
[[ "$#" -eq 0 ]] || { usage; exit 1; }

profile_name="${MAPPER_PROFILE_NAME:-transaction-read-jdbc-mapper-$(date +%Y-%m-%d-%H%M%S)}"
profile_duration="${MAPPER_PROFILE_DURATION:-90s}"
k6_duration="${K6_DURATION:-75s}"

echo "[transaction-jdbc-mapper-allocation] profile=${profile_name}"
echo "[transaction-jdbc-mapper-allocation] target=JdbcTransactionReadRepository,JDBC RowMapper,OffsetDateTime,enum"
echo "[transaction-jdbc-mapper-allocation] runner=tools/test/run-transaction-read-hotpath-profile.sh"
echo "[transaction-jdbc-mapper-allocation] jfr_views=hot-methods,cpu-time-hot-methods,allocation-by-class,allocation-by-site"

if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

PROFILE_NAME="${profile_name}" \
PROFILE_DURATION="${profile_duration}" \
K6_DURATION="${k6_duration}" \
  tools/test/run-transaction-read-hotpath-profile.sh
