#!/usr/bin/env bash
set -euo pipefail

REPORT_DIR="${REPORT_DIR:-build/reports/transaction-read-replica-staging-smoke}"
SUMMARY_MD="${REPORT_DIR}/summary.md"

STAGING_BASE_URL="${STAGING_BASE_URL:-}"
STAGING_REPLAY_TOKEN="${STAGING_REPLAY_TOKEN:-}"
STAGING_RDS_DATABASE_URL="${STAGING_RDS_DATABASE_URL:-}"
STAGING_RDS_REPLICA_DATABASE_URL="${STAGING_RDS_REPLICA_DATABASE_URL:-}"
TRANSACTION_ARCHIVE_ACCOUNT_ID="${TRANSACTION_ARCHIVE_ACCOUNT_ID:-}"
TRANSACTION_ARCHIVE_FROM="${TRANSACTION_ARCHIVE_FROM:-}"
TRANSACTION_ARCHIVE_TO="${TRANSACTION_ARCHIVE_TO:-}"
PAGE_LIMIT="${PAGE_LIMIT:-20}"
REQUEST_TIMEOUT_SECONDS="${REQUEST_TIMEOUT_SECONDS:-5}"
REPLICA_LAG_THRESHOLD_MS="${REPLICA_LAG_THRESHOLD_MS:-3000}"
ROUTE_METRIC_WAIT_SECONDS="${ROUTE_METRIC_WAIT_SECONDS:-10}"
PROMETHEUS_PATH="${PROMETHEUS_PATH:-/actuator/prometheus}"
STAGING_PROMETHEUS_BEARER_TOKEN="${STAGING_PROMETHEUS_BEARER_TOKEN:-}"

fail() {
  echo "::error::$*" >&2
  exit 1
}

notice() {
  echo "::notice::$*" >&2
}

print_plan() {
  cat <<'PLAN'
[transaction-read-replica-staging-smoke] required env:
- STAGING_BASE_URL
- STAGING_REPLAY_TOKEN
- STAGING_RDS_DATABASE_URL
- STAGING_RDS_REPLICA_DATABASE_URL
- TRANSACTION_ARCHIVE_ACCOUNT_ID
- TRANSACTION_ARCHIVE_FROM
- TRANSACTION_ARCHIVE_TO
[transaction-read-replica-staging-smoke] checks:
- primary and replica database URLs must differ
- replica database must report pg_is_in_recovery() = true
- replica lag must be <= REPLICA_LAG_THRESHOLD_MS
- archive read must increase aquila_transaction_read_replica_route_decisions_total{query_shape="archive",route="replica",reason="replica_healthy"}
PLAN
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

require_env() {
  local name="$1"
  local value="${!name:-}"
  [ -n "$value" ] || fail "Missing required environment variable: ${name}"
}

require_positive_integer() {
  local name="$1"
  local value="${!name:-}"
  [[ "$value" =~ ^[1-9][0-9]*$ ]] || fail "${name} must be a positive integer"
}

validate_inputs() {
  require_command curl
  require_command psql
  require_command awk
  require_command grep

  require_env STAGING_BASE_URL
  require_env STAGING_REPLAY_TOKEN
  require_env STAGING_RDS_DATABASE_URL
  require_env STAGING_RDS_REPLICA_DATABASE_URL
  require_env TRANSACTION_ARCHIVE_ACCOUNT_ID
  require_env TRANSACTION_ARCHIVE_FROM
  require_env TRANSACTION_ARCHIVE_TO

  require_positive_integer TRANSACTION_ARCHIVE_ACCOUNT_ID
  require_positive_integer PAGE_LIMIT
  require_positive_integer REQUEST_TIMEOUT_SECONDS
  require_positive_integer REPLICA_LAG_THRESHOLD_MS
  require_positive_integer ROUTE_METRIC_WAIT_SECONDS

  if [ "$PAGE_LIMIT" -gt 100 ]; then
    fail "PAGE_LIMIT must be 100 or less"
  fi
  if [ "$STAGING_RDS_DATABASE_URL" = "$STAGING_RDS_REPLICA_DATABASE_URL" ]; then
    fail "STAGING_RDS_REPLICA_DATABASE_URL must differ from STAGING_RDS_DATABASE_URL"
  fi

  STAGING_BASE_URL="${STAGING_BASE_URL%/}"
}

psql_scalar() {
  local database_url="$1"
  local sql="$2"
  psql "$database_url" -v ON_ERROR_STOP=1 --no-align --tuples-only --command "$sql"
}

measure_replica_lag() {
  local result in_recovery lag_ms
  # staging smoke는 실제 replica 연결을 검증해야 하므로 primary 호환 local shortcut은 허용하지 않습니다.
  result="$(
    psql_scalar "$STAGING_RDS_REPLICA_DATABASE_URL" \
      "SELECT pg_is_in_recovery()::text || '|' ||
              COALESCE(
                FLOOR(EXTRACT(EPOCH FROM (clock_timestamp() - pg_last_xact_replay_timestamp())) * 1000)::bigint,
                -1
              )::text;"
  )"
  in_recovery="${result%%|*}"
  lag_ms="${result#*|}"

  [ "$in_recovery" = "true" ] || [ "$in_recovery" = "t" ] \
    || fail "Configured replica database is not in recovery"
  [[ "$lag_ms" =~ ^-?[0-9]+$ ]] || fail "Replica lag is not numeric: ${lag_ms}"
  [ "$lag_ms" -ge 0 ] || fail "Replica lag is unknown"
  if [ "$lag_ms" -gt "$REPLICA_LAG_THRESHOLD_MS" ]; then
    fail "Replica lag ${lag_ms}ms exceeds threshold ${REPLICA_LAG_THRESHOLD_MS}ms"
  fi

  notice "Replica lag ${lag_ms}ms within threshold ${REPLICA_LAG_THRESHOLD_MS}ms"
  printf '%s\n' "$lag_ms"
}

curl_prometheus() {
  local args=(
    --silent
    --show-error
    --max-time "$REQUEST_TIMEOUT_SECONDS"
    "${STAGING_BASE_URL}${PROMETHEUS_PATH}"
  )
  if [ -n "$STAGING_PROMETHEUS_BEARER_TOKEN" ]; then
    args+=(--header "Authorization: Bearer ${STAGING_PROMETHEUS_BEARER_TOKEN}")
  fi
  curl "${args[@]}"
}

archive_replica_decision_count() {
  curl_prometheus |
    awk '
      /^aquila_transaction_read_replica_route_decisions_total\{/ &&
      /query_shape="archive"/ &&
      /route="replica"/ &&
      /reason="replica_healthy"/ {
        value = $NF
      }
      END {
        if (value == "") {
          print 0
        } else {
          print value
        }
      }'
}

request_archive_page() {
  local response http_status
  response="$(
    curl \
      --silent \
      --show-error \
      --output "${REPORT_DIR}/archive-response.json" \
      --write-out "%{http_code} %{time_total}" \
      --max-time "$REQUEST_TIMEOUT_SECONDS" \
      --get "${STAGING_BASE_URL}/api/v1/transactions/archive" \
      --header "Authorization: Bearer ${STAGING_REPLAY_TOKEN}" \
      --data-urlencode "accountId=${TRANSACTION_ARCHIVE_ACCOUNT_ID}" \
      --data-urlencode "from=${TRANSACTION_ARCHIVE_FROM}" \
      --data-urlencode "to=${TRANSACTION_ARCHIVE_TO}" \
      --data-urlencode "limit=${PAGE_LIMIT}"
  )" || fail "archive read request failed"

  http_status="${response%% *}"
  if [[ ! "$http_status" =~ ^2[0-9][0-9]$ ]]; then
    fail "archive read returned HTTP ${http_status}"
  fi
}

wait_for_replica_route_delta() {
  local before="$1"
  local after="$before"
  local deadline=$((SECONDS + ROUTE_METRIC_WAIT_SECONDS))

  while [ "$SECONDS" -le "$deadline" ]; do
    after="$(archive_replica_decision_count)"
    awk -v before="$before" -v after="$after" 'BEGIN { exit !(after > before) }' && {
      notice "Archive replica route counter increased from ${before} to ${after}"
      printf '%s\n' "$after"
      return 0
    }
    sleep 1
  done

  fail "Archive replica route decision counter did not increase from ${before}"
}

write_summary() {
  local lag_ms="$1"
  local before="$2"
  local after="$3"
  {
    echo "# Transaction Read Replica Staging Smoke"
    echo
    echo "- replica lag: ${lag_ms}ms / threshold ${REPLICA_LAG_THRESHOLD_MS}ms"
    echo "- archive replica route counter: ${before} -> ${after}"
    echo "- account id: ${TRANSACTION_ARCHIVE_ACCOUNT_ID}"
    echo "- page limit: ${PAGE_LIMIT}"
  } >"$SUMMARY_MD"
}

main() {
  if [ "${1:-}" = "--print-plan" ]; then
    print_plan
    return 0
  fi

  mkdir -p "$REPORT_DIR"
  validate_inputs

  local lag_ms before after
  lag_ms="$(measure_replica_lag)"
  before="$(archive_replica_decision_count)"
  request_archive_page
  after="$(wait_for_replica_route_delta "$before")"
  write_summary "$lag_ms" "$before" "$after"
}

main "$@"
