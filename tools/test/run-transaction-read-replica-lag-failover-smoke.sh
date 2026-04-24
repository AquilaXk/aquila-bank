#!/usr/bin/env bash
set -euo pipefail

script="tools/ops/transaction-read-replica-staging-smoke.sh"
workflow=".github/workflows/transaction-read-replica-staging-smoke.yml"

echo "[transaction-read-replica-smoke] syntax: ${script}"
bash -n "${script}"

echo "[transaction-read-replica-smoke] workflow wiring"
ruby -e "require 'yaml'; YAML.load_file('${workflow}'); puts 'ok'" >/dev/null

echo "[transaction-read-replica-smoke] plan includes replica lag and route decision"
plan="$("${script}" --print-plan)"
grep -F "STAGING_RDS_DATABASE_URL" <<<"${plan}" >/dev/null
grep -F "STAGING_RDS_REPLICA_DATABASE_URL" <<<"${plan}" >/dev/null
grep -F "aquila_transaction_read_replica_route_decisions_total" <<<"${plan}" >/dev/null

echo "[transaction-read-replica-smoke] guard: missing env fails before psql"
if "${script}" >/dev/null 2>&1; then
  echo "missing env unexpectedly succeeded" >&2
  exit 1
fi

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

bin_dir="${temp_dir}/bin"
mkdir -p "${bin_dir}"

cat >"${bin_dir}/psql" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$*" == *"replica-lag-high"* ]]; then
  echo "true|6000"
else
  echo "true|25"
fi
EOF
chmod +x "${bin_dir}/psql"

cat >"${bin_dir}/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
is_prometheus=false
is_archive=false
output_file=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    *"/actuator/prometheus"*) is_prometheus=true ;;
    *"/api/v1/transactions/archive"*) is_archive=true ;;
    --output)
      shift
      output_file="$1"
      ;;
  esac
  shift || true
done

if [ "$is_prometheus" = true ]; then
  current=0
  if [ -f "${CURL_METRIC_COUNTER_PATH}" ]; then
    current="$(cat "${CURL_METRIC_COUNTER_PATH}")"
  fi
  next=$((current + 1))
  echo "${next}" >"${CURL_METRIC_COUNTER_PATH}"
  cat <<METRIC
aquila_transaction_read_replica_route_decisions_total{query_shape="archive",route="replica",reason="replica_healthy"} ${next}
METRIC
  exit 0
fi

if [ "$is_archive" = true ]; then
  [ -n "$output_file" ] && echo '{"items":[{"id":1}]}' >"$output_file"
  echo "200 0.001"
  exit 0
fi

echo "unexpected curl call" >&2
exit 1
EOF
chmod +x "${bin_dir}/curl"

cat >"${bin_dir}/awk" <<'EOF'
#!/usr/bin/env bash
exec /usr/bin/awk "$@"
EOF
chmod +x "${bin_dir}/awk"

cat >"${bin_dir}/grep" <<'EOF'
#!/usr/bin/env bash
exec /usr/bin/grep "$@"
EOF
chmod +x "${bin_dir}/grep"

echo "[transaction-read-replica-smoke] stub: healthy replica increments route metric"
PATH="${bin_dir}:${PATH}" \
  CURL_METRIC_COUNTER_PATH="${temp_dir}/metric-counter" \
  REPORT_DIR="${temp_dir}/report" \
  STAGING_BASE_URL="https://staging.example.com" \
  STAGING_REPLAY_TOKEN="token" \
  STAGING_RDS_DATABASE_URL="postgres://primary" \
  STAGING_RDS_REPLICA_DATABASE_URL="postgres://replica" \
  TRANSACTION_ARCHIVE_ACCOUNT_ID="101" \
  TRANSACTION_ARCHIVE_FROM="2026-03-01T00:00:00Z" \
  TRANSACTION_ARCHIVE_TO="2026-03-31T00:00:00Z" \
  ROUTE_METRIC_WAIT_SECONDS="1" \
  "${script}"

grep -F "archive replica route counter" "${temp_dir}/report/summary.md" >/dev/null

echo "[transaction-read-replica-smoke] stub: lag breach fails before archive request"
set +e
output="$(
  PATH="${bin_dir}:${PATH}" \
    CURL_METRIC_COUNTER_PATH="${temp_dir}/lag-metric-counter" \
    REPORT_DIR="${temp_dir}/lag-report" \
    STAGING_BASE_URL="https://staging.example.com" \
    STAGING_REPLAY_TOKEN="token" \
    STAGING_RDS_DATABASE_URL="postgres://primary" \
    STAGING_RDS_REPLICA_DATABASE_URL="postgres://replica-lag-high" \
    TRANSACTION_ARCHIVE_ACCOUNT_ID="101" \
    TRANSACTION_ARCHIVE_FROM="2026-03-01T00:00:00Z" \
    TRANSACTION_ARCHIVE_TO="2026-03-31T00:00:00Z" \
    REPLICA_LAG_THRESHOLD_MS="3000" \
    "${script}" 2>&1
)"
status=$?
set -e

if [ "$status" -eq 0 ]; then
  echo "lag breach unexpectedly succeeded" >&2
  exit 1
fi
grep -F "Replica lag 6000ms exceeds threshold 3000ms" <<<"${output}" >/dev/null
if [ -f "${temp_dir}/lag-report/archive-response.json" ]; then
  echo "archive request must not run when replica lag exceeds threshold" >&2
  exit 1
fi
