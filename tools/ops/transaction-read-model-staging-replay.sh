#!/usr/bin/env bash
set -euo pipefail

REPORT_DIR="${REPORT_DIR:-build/reports/transaction-staging-replay}"
SUMMARY_JSON="${REPORT_DIR}/summary.json"
SUMMARY_MD="${REPORT_DIR}/summary.md"

STAGING_BASE_URL="${STAGING_BASE_URL:-}"
STAGING_REPLAY_TOKEN="${STAGING_REPLAY_TOKEN:-}"
STAGING_RDS_DATABASE_URL="${STAGING_RDS_DATABASE_URL:-}"
EXPECTED_TOTAL_ROWS="${EXPECTED_TOTAL_ROWS:-100000000}"
ITERATIONS="${ITERATIONS:-40}"
PAGE_LIMIT="${PAGE_LIMIT:-50}"
REQUEST_TIMEOUT_SECONDS="${REQUEST_TIMEOUT_SECONDS:-5}"
HOT_ACCOUNT_ID="${HOT_ACCOUNT_ID:-}"
HOT_FROM="${HOT_FROM:-}"
HOT_TO="${HOT_TO:-}"
COLD_ACCOUNT_ID="${COLD_ACCOUNT_ID:-}"
COLD_FROM="${COLD_FROM:-}"
COLD_TO="${COLD_TO:-}"
HOT_P95_THRESHOLD_MS="${HOT_P95_THRESHOLD_MS:-350}"
COLD_P95_THRESHOLD_MS="${COLD_P95_THRESHOLD_MS:-750}"

fail() {
  echo "::error::$*" >&2
  exit 1
}

notice() {
  echo "::notice::$*" >&2
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

psql_scalar() {
  local sql="$1"
  psql "$STAGING_RDS_DATABASE_URL" -v ON_ERROR_STOP=1 --no-align --tuples-only --command "$sql"
}

validate_inputs() {
  require_command curl
  require_command jq
  require_command psql
  require_command awk
  require_command sort

  require_env STAGING_BASE_URL
  require_env STAGING_REPLAY_TOKEN
  require_env STAGING_RDS_DATABASE_URL
  require_env HOT_ACCOUNT_ID
  require_env HOT_FROM
  require_env HOT_TO
  require_env COLD_ACCOUNT_ID
  require_env COLD_FROM
  require_env COLD_TO

  require_positive_integer EXPECTED_TOTAL_ROWS
  require_positive_integer ITERATIONS
  require_positive_integer PAGE_LIMIT
  require_positive_integer REQUEST_TIMEOUT_SECONDS
  require_positive_integer HOT_ACCOUNT_ID
  require_positive_integer COLD_ACCOUNT_ID
  require_positive_integer HOT_P95_THRESHOLD_MS
  require_positive_integer COLD_P95_THRESHOLD_MS

  if [ "$PAGE_LIMIT" -gt 100 ]; then
    fail "PAGE_LIMIT must be 100 or less"
  fi

  STAGING_BASE_URL="${STAGING_BASE_URL%/}"
}

verify_staging_distribution() {
  # 1억 건 검증에서 full count는 RDS에 부담이 커서 planner 통계 estimate로 gate를 둡니다.
  local estimated_total
  estimated_total="$(
    psql_scalar \
      "SELECT COALESCE(SUM(c.reltuples), 0)::bigint
       FROM pg_class c
       WHERE c.oid IN (
         'transaction_read_model'::regclass,
         'transaction_read_model_archive'::regclass
       );"
  )"

  [[ "$estimated_total" =~ ^[0-9]+$ ]] || fail "RDS row estimate is not numeric: ${estimated_total}"
  if [ "$estimated_total" -lt "$EXPECTED_TOTAL_ROWS" ]; then
    fail "RDS read model estimate ${estimated_total} is below expected ${EXPECTED_TOTAL_ROWS}"
  fi

  local hot_exists
  hot_exists="$(
    psql_scalar \
      "SELECT EXISTS (
         SELECT 1
         FROM transaction_read_model
         WHERE account_id = ${HOT_ACCOUNT_ID}
         LIMIT 1
       );"
  )"
  [ "$hot_exists" = "t" ] || fail "HOT_ACCOUNT_ID ${HOT_ACCOUNT_ID} has no hot rows"

  local cold_exists
  cold_exists="$(
    psql_scalar \
      "SELECT EXISTS (
         SELECT 1
         FROM transaction_read_model_archive
         WHERE account_id = ${COLD_ACCOUNT_ID}
         LIMIT 1
       );"
  )"
  [ "$cold_exists" = "t" ] || fail "COLD_ACCOUNT_ID ${COLD_ACCOUNT_ID} has no archive rows"

  notice "RDS read model estimate ${estimated_total} rows"
  echo "$estimated_total" >"${REPORT_DIR}/estimated-total-rows.txt"
}

record_latency() {
  local shape="$1"
  local latency_ms="$2"
  printf '%s\n' "$latency_ms" >>"${REPORT_DIR}/${shape}.ms"
}

request_page() {
  local shape="$1"
  local path="$2"
  local account_id="$3"
  local from="$4"
  local to="$5"
  local cursor="${6:-}"
  local iteration="$7"
  local body_file="${REPORT_DIR}/${shape}-${iteration}.json"
  local response http_status time_total latency_ms item_count next_cursor

  local curl_args=(
    --silent
    --show-error
    --output "$body_file"
    --write-out "%{http_code} %{time_total}"
    --max-time "$REQUEST_TIMEOUT_SECONDS"
    --get "${STAGING_BASE_URL}${path}"
    --header "Authorization: Bearer ${STAGING_REPLAY_TOKEN}"
    --data-urlencode "accountId=${account_id}"
    --data-urlencode "from=${from}"
    --data-urlencode "to=${to}"
    --data-urlencode "limit=${PAGE_LIMIT}"
  )

  if [ -n "$cursor" ]; then
    curl_args+=(--data-urlencode "cursor=${cursor}")
  fi

  if ! response="$(curl "${curl_args[@]}")"; then
    fail "${shape} iteration ${iteration} request failed"
  fi

  http_status="${response%% *}"
  time_total="${response#* }"
  if [[ ! "$http_status" =~ ^2[0-9][0-9]$ ]]; then
    fail "${shape} iteration ${iteration} returned HTTP ${http_status}"
  fi

  latency_ms="$(awk -v seconds="$time_total" 'BEGIN { printf "%.0f", seconds * 1000 }')"
  item_count="$(jq '.items | length' "$body_file")"
  if [ "$item_count" -le 0 ]; then
    fail "${shape} iteration ${iteration} returned no items"
  fi

  record_latency "$shape" "$latency_ms"
  next_cursor="$(jq -r '.nextCursor // ""' "$body_file")"
  printf '%s\n' "$next_cursor"
}

run_replay() {
  : >"${REPORT_DIR}/hot_first.ms"
  : >"${REPORT_DIR}/hot_cursor.ms"
  : >"${REPORT_DIR}/cold_first.ms"
  : >"${REPORT_DIR}/cold_cursor.ms"

  for iteration in $(seq 1 "$ITERATIONS"); do
    local hot_cursor cold_cursor
    hot_cursor="$(
      request_page hot_first "/api/v1/transactions" \
        "$HOT_ACCOUNT_ID" "$HOT_FROM" "$HOT_TO" "" "$iteration"
    )"
    [ -n "$hot_cursor" ] || fail "hot_first iteration ${iteration} did not return nextCursor"
    request_page hot_cursor "/api/v1/transactions" \
      "$HOT_ACCOUNT_ID" "$HOT_FROM" "$HOT_TO" "$hot_cursor" "$iteration" >/dev/null

    cold_cursor="$(
      request_page cold_first "/api/v1/transactions/archive" \
        "$COLD_ACCOUNT_ID" "$COLD_FROM" "$COLD_TO" "" "$iteration"
    )"
    [ -n "$cold_cursor" ] || fail "cold_first iteration ${iteration} did not return nextCursor"
    request_page cold_cursor "/api/v1/transactions/archive" \
      "$COLD_ACCOUNT_ID" "$COLD_FROM" "$COLD_TO" "$cold_cursor" "$iteration" >/dev/null
  done
}

p95_ms() {
  local file="$1"
  local count index
  count="$(wc -l <"$file" | tr -d ' ')"
  [ "$count" -gt 0 ] || fail "No latency samples in ${file}"
  index=$(((count * 95 + 99) / 100))
  sort -n "$file" | awk -v index="$index" 'NR == index { print; exit }'
}

write_summary() {
  local estimated_total hot_first hot_cursor cold_first cold_cursor failed
  estimated_total="$(cat "${REPORT_DIR}/estimated-total-rows.txt")"
  hot_first="$(p95_ms "${REPORT_DIR}/hot_first.ms")"
  hot_cursor="$(p95_ms "${REPORT_DIR}/hot_cursor.ms")"
  cold_first="$(p95_ms "${REPORT_DIR}/cold_first.ms")"
  cold_cursor="$(p95_ms "${REPORT_DIR}/cold_cursor.ms")"
  failed=false

  if [ "$hot_first" -gt "$HOT_P95_THRESHOLD_MS" ] || [ "$hot_cursor" -gt "$HOT_P95_THRESHOLD_MS" ]; then
    failed=true
  fi
  if [ "$cold_first" -gt "$COLD_P95_THRESHOLD_MS" ] || [ "$cold_cursor" -gt "$COLD_P95_THRESHOLD_MS" ]; then
    failed=true
  fi

  jq -n \
    --arg generatedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg baseUrl "$STAGING_BASE_URL" \
    --argjson expectedTotalRows "$EXPECTED_TOTAL_ROWS" \
    --argjson estimatedTotalRows "$estimated_total" \
    --argjson iterations "$ITERATIONS" \
    --argjson pageLimit "$PAGE_LIMIT" \
    --argjson hotP95ThresholdMs "$HOT_P95_THRESHOLD_MS" \
    --argjson coldP95ThresholdMs "$COLD_P95_THRESHOLD_MS" \
    --argjson hotFirstP95Ms "$hot_first" \
    --argjson hotCursorP95Ms "$hot_cursor" \
    --argjson coldFirstP95Ms "$cold_first" \
    --argjson coldCursorP95Ms "$cold_cursor" \
    --argjson failed "$failed" \
    '{
      generatedAt: $generatedAt,
      baseUrl: $baseUrl,
      expectedTotalRows: $expectedTotalRows,
      estimatedTotalRows: $estimatedTotalRows,
      iterations: $iterations,
      pageLimit: $pageLimit,
      thresholds: {
        hotP95Ms: $hotP95ThresholdMs,
        coldP95Ms: $coldP95ThresholdMs
      },
      results: {
        hotFirstP95Ms: $hotFirstP95Ms,
        hotCursorP95Ms: $hotCursorP95Ms,
        coldFirstP95Ms: $coldFirstP95Ms,
        coldCursorP95Ms: $coldCursorP95Ms
      },
      failed: $failed
    }' >"$SUMMARY_JSON"

  {
    echo "# Transaction Read Model Staging Replay"
    echo
    echo "- estimated total rows: ${estimated_total}"
    echo "- iterations: ${ITERATIONS}"
    echo "- page limit: ${PAGE_LIMIT}"
    echo "- hot first p95: ${hot_first}ms / threshold ${HOT_P95_THRESHOLD_MS}ms"
    echo "- hot cursor p95: ${hot_cursor}ms / threshold ${HOT_P95_THRESHOLD_MS}ms"
    echo "- cold first p95: ${cold_first}ms / threshold ${COLD_P95_THRESHOLD_MS}ms"
    echo "- cold cursor p95: ${cold_cursor}ms / threshold ${COLD_P95_THRESHOLD_MS}ms"
  } >"$SUMMARY_MD"

  if [ "$failed" = "true" ]; then
    fail "transaction read model staging replay p95 threshold exceeded"
  fi
}

main() {
  mkdir -p "$REPORT_DIR"
  validate_inputs
  verify_staging_distribution
  run_replay
  write_summary
}

main "$@"
