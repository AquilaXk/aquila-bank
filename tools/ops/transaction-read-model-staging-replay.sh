#!/usr/bin/env bash
set -euo pipefail

REPORT_DIR="${REPORT_DIR:-build/reports/transaction-staging-replay}"
SUMMARY_JSON="${REPORT_DIR}/summary.json"
SUMMARY_MD="${REPORT_DIR}/summary.md"

STAGING_BASE_URL="${STAGING_BASE_URL:-}"
STAGING_REPLAY_TOKEN="${STAGING_REPLAY_TOKEN:-}"
STAGING_OCI_A1_DATABASE_URL="${STAGING_OCI_A1_DATABASE_URL:-}"
STAGING_RDS_DATABASE_URL="${STAGING_RDS_DATABASE_URL:-}"
STAGING_DATABASE_URL="${STAGING_OCI_A1_DATABASE_URL:-${STAGING_RDS_DATABASE_URL}}"
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
PLANNER_STATS_GUARD_SCRIPT="${PLANNER_STATS_GUARD_SCRIPT:-tools/ops/transaction-read-model-planner-stats-freshness-guard.sh}"
PLANNER_STATS_MAX_AGE_HOURS="${PLANNER_STATS_MAX_AGE_HOURS:-24}"
PLANNER_STATS_MAX_MODIFIED_RATIO="${PLANNER_STATS_MAX_MODIFIED_RATIO:-0.05}"
PLANNER_STATS_AUTO_ANALYZE="${PLANNER_STATS_AUTO_ANALYZE:-true}"
PLANNER_STATS_ANALYZE_SCRIPT="${PLANNER_STATS_ANALYZE_SCRIPT:-tools/ops/transaction-read-model-chunk-lifecycle.sh}"

fail() {
  echo "::error::$*" >&2
  exit 1
}

notice() {
  echo "::notice::$*" >&2
}

fixture_restore_guidance() {
  printf '%s' "Restore OCI A1 100m fixture before replay: tools/test/run-transaction-100m-fresh-volume-restore-k6.sh --dry-run, then tools/ops/transaction-read-model-chunk-lifecycle.sh --action analyze --target both."
}

write_distribution_failure_report() {
  local status="$1"
  local estimated_total="$2"
  local detail="$3"
  local guidance="$4"

  echo "$estimated_total" >"${REPORT_DIR}/estimated-total-rows.txt"
  jq -n \
    --arg generatedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg baseUrl "$STAGING_BASE_URL" \
    --arg phase "distribution" \
    --arg status "$status" \
    --arg detail "$detail" \
    --arg guidance "$guidance" \
    --argjson expectedTotalRows "$EXPECTED_TOTAL_ROWS" \
    --argjson estimatedTotalRows "$estimated_total" \
    '{
      generatedAt: $generatedAt,
      baseUrl: $baseUrl,
      phase: $phase,
      status: $status,
      detail: $detail,
      guidance: $guidance,
      expectedTotalRows: $expectedTotalRows,
      estimatedTotalRows: $estimatedTotalRows,
      failed: true
    }' >"$SUMMARY_JSON"

  {
    echo "# Transaction Read Model Staging Replay"
    echo
    echo "- status: failed"
    echo "- phase: distribution"
    echo "- failure: ${status}"
    echo "- estimated total rows: ${estimated_total}"
    echo "- expected total rows: ${EXPECTED_TOTAL_ROWS}"
    echo "- detail: ${detail}"
    echo "- guidance: ${guidance}"
  } >"$SUMMARY_MD"
}

write_planner_guard_failure_report() {
  local detail guidance
  detail="Planner stats freshness guard failed"
  guidance="Run ANALYZE on reported tables before replay."

  jq -n \
    --arg generatedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg baseUrl "$STAGING_BASE_URL" \
    --arg phase "planner-stats" \
    --arg status "planner_stats_guard_failed" \
    --arg detail "$detail" \
    --arg guidance "$guidance" \
    '{
      generatedAt: $generatedAt,
      baseUrl: $baseUrl,
      phase: $phase,
      status: $status,
      detail: $detail,
      guidance: $guidance,
      failed: true
    }' >"$SUMMARY_JSON"

  {
    echo "# Transaction Read Model Staging Replay"
    echo
    echo "- status: failed"
    echo "- phase: planner-stats"
    echo "- failure: planner_stats_guard_failed"
    echo "- detail: ${detail}"
    echo "- guidance: ${guidance}"
  } >"$SUMMARY_MD"
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

require_ratio_between_zero_and_one() {
  local name="$1"
  local value="${!name:-}"

  [[ "$value" =~ ^([0-9]+([.][0-9]+)?|[.][0-9]+)$ ]] || fail "${name} must be a decimal ratio between 0 and 1"
  awk -v value="$value" 'BEGIN { exit !(value > 0 && value < 1) }' \
    || fail "${name} must be greater than 0 and less than 1"
}

require_bool() {
  local name="$1"
  local value="${!name:-}"
  case "$value" in
    true|false) ;;
    *) fail "${name} must be true or false" ;;
  esac
}

psql_scalar() {
  local sql="$1"
  psql "$STAGING_DATABASE_URL" -v ON_ERROR_STOP=1 --no-align --tuples-only --command "$sql"
}

validate_inputs() {
  require_command curl
  require_command jq
  require_command psql
  require_command awk
  require_command sort

  require_env STAGING_BASE_URL
  require_env STAGING_REPLAY_TOKEN
  require_env STAGING_DATABASE_URL
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
  require_positive_integer PLANNER_STATS_MAX_AGE_HOURS
  require_ratio_between_zero_and_one PLANNER_STATS_MAX_MODIFIED_RATIO
  require_bool PLANNER_STATS_AUTO_ANALYZE

  if [ "$PAGE_LIMIT" -gt 100 ]; then
    fail "PAGE_LIMIT must be 100 or less"
  fi

  [ -x "$PLANNER_STATS_GUARD_SCRIPT" ] || fail "Planner stats guard script is not executable: ${PLANNER_STATS_GUARD_SCRIPT}"
  if [ "$PLANNER_STATS_AUTO_ANALYZE" = "true" ]; then
    [ -x "$PLANNER_STATS_ANALYZE_SCRIPT" ] || fail "Planner stats analyze script is not executable: ${PLANNER_STATS_ANALYZE_SCRIPT}"
  fi

  STAGING_BASE_URL="${STAGING_BASE_URL%/}"
}

run_planner_stats_analyze() {
  notice "Planner stats freshness guard failed; running ANALYZE before replay."
  # stats refresh는 replay evidence 정확도 목적이며, chunk lifecycle script의 lock/statement timeout을 사용합니다.
  DATABASE_URL="$STAGING_DATABASE_URL" \
    "$PLANNER_STATS_ANALYZE_SCRIPT" --action analyze --target both
}

run_planner_stats_guard() {
  # `reltuples` estimate는 stale stats에 취약해서 replay 전에 ANALYZE 필요 여부를 먼저 차단합니다.
  if DATABASE_URL="$STAGING_DATABASE_URL" \
    STATS_MAX_AGE_HOURS="$PLANNER_STATS_MAX_AGE_HOURS" \
    STATS_MAX_MODIFIED_RATIO="$PLANNER_STATS_MAX_MODIFIED_RATIO" \
    "$PLANNER_STATS_GUARD_SCRIPT"; then
    return 0
  fi

  if [ "$PLANNER_STATS_AUTO_ANALYZE" = "true" ]; then
    run_planner_stats_analyze
    if DATABASE_URL="$STAGING_DATABASE_URL" \
      STATS_MAX_AGE_HOURS="$PLANNER_STATS_MAX_AGE_HOURS" \
      STATS_MAX_MODIFIED_RATIO="$PLANNER_STATS_MAX_MODIFIED_RATIO" \
      "$PLANNER_STATS_GUARD_SCRIPT"; then
      return 0
    fi
  fi

  write_planner_guard_failure_report
  fail "Planner stats freshness guard failed. Run ANALYZE on reported tables before replay."
}

verify_staging_distribution() {
  # 1억 건 검증에서 full count는 OCI A1 PostgreSQL에 부담이 커서 planner 통계 estimate로 gate를 둡니다.
  local estimated_total
  estimated_total="$(
    psql_scalar \
      "WITH target_parent(table_name) AS (
         VALUES
           ('transaction_read_model'),
           ('transaction_read_model_archive')
       ),
       leaf AS (
         SELECT tree.relid
         FROM target_parent target
         CROSS JOIN LATERAL pg_partition_tree(('public.' || target.table_name)::regclass) tree
         WHERE tree.isleaf
       )
       SELECT COALESCE(SUM(GREATEST(c.reltuples, 0))::bigint, 0)
       FROM leaf
       JOIN pg_class c
         ON c.oid = leaf.relid;"
  )"

  [[ "$estimated_total" =~ ^[0-9]+$ ]] || fail "OCI A1 row estimate is not numeric: ${estimated_total}"
  if [ "$estimated_total" -lt "$EXPECTED_TOTAL_ROWS" ]; then
    local status detail guidance
    status="estimate_below_expected"
    if [ "$estimated_total" -eq 0 ]; then
      status="fixture_missing"
    fi
    detail="OCI A1 read model estimate ${estimated_total} is below expected ${EXPECTED_TOTAL_ROWS}"
    guidance="$(fixture_restore_guidance)"
    write_distribution_failure_report "$status" "$estimated_total" "$detail" "$guidance"
    fail "${status}: ${detail}. ${guidance}"
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
  if [ "$hot_exists" != "t" ]; then
    local detail guidance
    detail="HOT_ACCOUNT_ID ${HOT_ACCOUNT_ID} has no hot rows"
    guidance="$(fixture_restore_guidance)"
    write_distribution_failure_report "hot_account_missing" "$estimated_total" "$detail" "$guidance"
    fail "hot_account_missing: ${detail}. ${guidance}"
  fi

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
  if [ "$cold_exists" != "t" ]; then
    local detail guidance
    detail="COLD_ACCOUNT_ID ${COLD_ACCOUNT_ID} has no archive rows"
    guidance="$(fixture_restore_guidance)"
    write_distribution_failure_report "cold_account_missing" "$estimated_total" "$detail" "$guidance"
    fail "cold_account_missing: ${detail}. ${guidance}"
  fi

  notice "OCI A1 read model estimate ${estimated_total} rows"
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
  local count percentile_index
  count="$(wc -l <"$file" | tr -d ' ')"
  [ "$count" -gt 0 ] || fail "No latency samples in ${file}"
  percentile_index=$(((count * 95 + 99) / 100))
  sort -n "$file" | awk -v percentile_index="$percentile_index" 'NR == percentile_index { print; exit }'
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
  run_planner_stats_guard
  verify_staging_distribution
  run_replay
  write_summary
}

main "$@"
