#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-100m-multi-account-fixture.sh [--print-plan]

Environment:
  MULTI_ACCOUNT_FIXTURE_NAME            default transaction-100m-multi-account-<timestamp>
  MULTI_ACCOUNT_HOT_ACCOUNT_IDS         comma-separated hot account ids, min 2
  MULTI_ACCOUNT_COLD_ACCOUNT_IDS        comma-separated cold account ids, min 1
  MULTI_ACCOUNT_HOT_ROWS_PER_ACCOUNT    default 10000000
  MULTI_ACCOUNT_COLD_ROWS_PER_ACCOUNT   default 1000000
  MULTI_ACCOUNT_HOT_FROM                default 2026-04-01T00:00:00Z
  MULTI_ACCOUNT_HOT_TO                  default 2026-04-30T00:00:00Z
  MULTI_ACCOUNT_COLD_FROM               default 2026-01-01T00:00:00Z
  MULTI_ACCOUNT_COLD_TO                 default 2026-01-31T00:00:00Z
  MULTI_ACCOUNT_OUTPUT_DIR              default build/reports/k6/<name>
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
  shift
done

name="${MULTI_ACCOUNT_FIXTURE_NAME:-transaction-100m-multi-account-$(date +%Y-%m-%d-%H%M%S)}"
hot_ids="${MULTI_ACCOUNT_HOT_ACCOUNT_IDS:-}"
cold_ids="${MULTI_ACCOUNT_COLD_ACCOUNT_IDS:-}"
hot_rows_per_account="${MULTI_ACCOUNT_HOT_ROWS_PER_ACCOUNT:-10000000}"
cold_rows_per_account="${MULTI_ACCOUNT_COLD_ROWS_PER_ACCOUNT:-1000000}"
hot_from="${MULTI_ACCOUNT_HOT_FROM:-2026-04-01T00:00:00Z}"
hot_to="${MULTI_ACCOUNT_HOT_TO:-2026-04-30T00:00:00Z}"
cold_from="${MULTI_ACCOUNT_COLD_FROM:-2026-01-01T00:00:00Z}"
cold_to="${MULTI_ACCOUNT_COLD_TO:-2026-01-31T00:00:00Z}"
output_dir="${MULTI_ACCOUNT_OUTPUT_DIR:-build/reports/k6/${name}}"
manifest_json="${output_dir}/${name}-multi-account-fixture.json"
report_md="${output_dir}/${name}-multi-account-fixture.md"
k6_runner="tools/test/run-k6-transaction-100m-loadtest.sh"

require_positive_integer() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${key} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

csv_count() {
  local value="$1"
  awk -v value="${value}" 'BEGIN {
    split(value, items, ",")
    count = 0
    for (i in items) {
      gsub(/^ +| +$/, "", items[i])
      if (items[i] != "") count++
    }
    print count
  }'
}

validate_account_csv() {
  local key="$1"
  local value="$2"
  local minimum="$3"
  local count
  count="$(csv_count "${value}")"
  if ((count < minimum)); then
    echo "${key} must contain at least ${minimum} account ids" >&2
    exit 1
  fi
  IFS=',' read -r -a items <<<"${value}"
  local item
  for item in "${items[@]}"; do
    item="${item//[[:space:]]/}"
    if ! [[ "${item}" =~ ^[1-9][0-9]*$ ]]; then
      echo "${key} contains invalid account id: ${item}" >&2
      exit 1
    fi
  done
}

json_array() {
  local value="$1"
  local result=""
  IFS=',' read -r -a items <<<"${value}"
  local item
  for item in "${items[@]}"; do
    item="${item//[[:space:]]/}"
    if [[ -n "${result}" ]]; then
      result+=","
    fi
    result+="\"${item}\""
  done
  printf '[%s]' "${result}"
}

validate_account_csv "MULTI_ACCOUNT_HOT_ACCOUNT_IDS" "${hot_ids}" 2
validate_account_csv "MULTI_ACCOUNT_COLD_ACCOUNT_IDS" "${cold_ids}" 1
require_positive_integer "MULTI_ACCOUNT_HOT_ROWS_PER_ACCOUNT" "${hot_rows_per_account}"
require_positive_integer "MULTI_ACCOUNT_COLD_ROWS_PER_ACCOUNT" "${cold_rows_per_account}"

hot_count="$(csv_count "${hot_ids}")"
cold_count="$(csv_count "${cold_ids}")"
total_rows="$((hot_count * hot_rows_per_account + cold_count * cold_rows_per_account))"

print_plan() {
  echo "[transaction-100m-multi-account] name=${name}"
  echo "[transaction-100m-multi-account] hot_account_count=${hot_count}"
  echo "[transaction-100m-multi-account] cold_account_count=${cold_count}"
  echo "[transaction-100m-multi-account] total_rows=${total_rows}"
  echo "[transaction-100m-multi-account] k6_env=K6_HOT_ACCOUNT_IDS=${hot_ids} K6_COLD_ACCOUNT_IDS=${cold_ids}"
  echo "[transaction-100m-multi-account] k6_command=${k6_runner}"
  echo "[transaction-100m-multi-account] manifest_json=${manifest_json}"
  echo "[transaction-100m-multi-account] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

mkdir -p "${output_dir}"

cat >"${manifest_json}" <<JSON
{"name":"${name}","hotAccountIds":$(json_array "${hot_ids}"),"coldAccountIds":$(json_array "${cold_ids}"),"hotRowsPerAccount":${hot_rows_per_account},"coldRowsPerAccount":${cold_rows_per_account},"totalRows":${total_rows},"hotWindow":{"from":"${hot_from}","to":"${hot_to}"},"coldWindow":{"from":"${cold_from}","to":"${cold_to}"}}
JSON

cat >"${report_md}" <<REPORT
# Transaction 100M Multi-Account Fixture

## Summary

- fixture: ${name}
- hot account count: ${hot_count}
- cold account count: ${cold_count}
- total planned rows: ${total_rows}
- k6 hot accounts: ${hot_ids}
- k6 cold accounts: ${cold_ids}
- per-account fairness metric: aquila_transaction_fairness_429_count{account_id,account_group}
- global throughput and per-account fairness are reported separately.

## Replay Command

\`\`\`bash
K6_HOT_ACCOUNT_IDS=${hot_ids} \\
K6_COLD_ACCOUNT_IDS=${cold_ids} \\
K6_HOT_FROM=${hot_from} K6_HOT_TO=${hot_to} \\
K6_COLD_FROM=${cold_from} K6_COLD_TO=${cold_to} \\
K6_WORKLOAD_SHAPE=weighted-random \\
${k6_runner}
\`\`\`

## Artifacts

- manifest JSON: ${manifest_json}
REPORT

echo "${report_md}"
