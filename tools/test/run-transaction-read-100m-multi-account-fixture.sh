#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-100m-multi-account-fixture.sh [--print-plan]

Environment:
  MULTI_ACCOUNT_FIXTURE_NAME            default transaction-100m-multi-account-<timestamp>
  MULTI_ACCOUNT_RUN_ID                  default same as MULTI_ACCOUNT_FIXTURE_NAME
  MULTI_ACCOUNT_HOT_ACCOUNT_IDS         comma-separated hot account ids, min 2
  MULTI_ACCOUNT_COLD_ACCOUNT_IDS        comma-separated cold account ids, min 2
  MULTI_ACCOUNT_MIN_TOTAL_ACCOUNT_COUNT default 4
  MULTI_ACCOUNT_MIN_COLD_ACCOUNT_COUNT  default 2
  MULTI_ACCOUNT_TARGET_TOTAL_ROWS       default 100000000
  MULTI_ACCOUNT_HOT_ROWS_PER_ACCOUNT    default derived from target total rows
  MULTI_ACCOUNT_COLD_ROWS_PER_ACCOUNT   default 500000
  MULTI_ACCOUNT_ENFORCE_TARGET_TOTAL_ROWS default true
  MULTI_ACCOUNT_ACCOUNT_RESULT_TSV      TSV: account_group,account_id,planned_rows,accepted_p95_ms,rejected_429_rate
  MULTI_ACCOUNT_REQUIRE_ACCOUNT_RESULT_TSV default true
  MULTI_ACCOUNT_ARTIFACT_URI            required evidence artifact reference
  MULTI_ACCOUNT_REQUIRE_ARTIFACT_URI    default true
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
run_id="${MULTI_ACCOUNT_RUN_ID:-${name}}"
hot_ids="${MULTI_ACCOUNT_HOT_ACCOUNT_IDS:-}"
cold_ids="${MULTI_ACCOUNT_COLD_ACCOUNT_IDS:-}"
min_total_account_count="${MULTI_ACCOUNT_MIN_TOTAL_ACCOUNT_COUNT:-4}"
min_cold_account_count="${MULTI_ACCOUNT_MIN_COLD_ACCOUNT_COUNT:-2}"
target_total_rows="${MULTI_ACCOUNT_TARGET_TOTAL_ROWS:-100000000}"
hot_rows_per_account="${MULTI_ACCOUNT_HOT_ROWS_PER_ACCOUNT:-}"
cold_rows_per_account="${MULTI_ACCOUNT_COLD_ROWS_PER_ACCOUNT:-500000}"
enforce_target_total_rows="${MULTI_ACCOUNT_ENFORCE_TARGET_TOTAL_ROWS:-true}"
account_result_tsv="${MULTI_ACCOUNT_ACCOUNT_RESULT_TSV:-}"
require_account_result_tsv="${MULTI_ACCOUNT_REQUIRE_ACCOUNT_RESULT_TSV:-true}"
artifact_uri="${MULTI_ACCOUNT_ARTIFACT_URI:-}"
require_artifact_uri="${MULTI_ACCOUNT_REQUIRE_ARTIFACT_URI:-true}"
hot_from="${MULTI_ACCOUNT_HOT_FROM:-2026-04-01T00:00:00Z}"
hot_to="${MULTI_ACCOUNT_HOT_TO:-2026-04-30T00:00:00Z}"
cold_from="${MULTI_ACCOUNT_COLD_FROM:-2026-01-01T00:00:00Z}"
cold_to="${MULTI_ACCOUNT_COLD_TO:-2026-01-31T00:00:00Z}"
output_dir="${MULTI_ACCOUNT_OUTPUT_DIR:-build/reports/k6/${name}}"
manifest_json="${output_dir}/${name}-multi-account-fixture.json"
distribution_tsv="${output_dir}/${name}-multi-account-distribution.tsv"
account_summary_tsv="${output_dir}/${name}-multi-account-account-summary.tsv"
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

require_bool() {
  local key="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${key} must be true or false: ${value}" >&2
    exit 1
  fi
}

require_non_empty() {
  local key="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    echo "${key} is required" >&2
    exit 1
  fi
}

require_file() {
  local key="$1"
  local file="$2"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "${key} is required and must be a non-empty file: ${file:-missing}" >&2
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
validate_account_csv "MULTI_ACCOUNT_COLD_ACCOUNT_IDS" "${cold_ids}" "${min_cold_account_count}"
require_positive_integer "MULTI_ACCOUNT_MIN_TOTAL_ACCOUNT_COUNT" "${min_total_account_count}"
require_positive_integer "MULTI_ACCOUNT_MIN_COLD_ACCOUNT_COUNT" "${min_cold_account_count}"
require_positive_integer "MULTI_ACCOUNT_TARGET_TOTAL_ROWS" "${target_total_rows}"
require_positive_integer "MULTI_ACCOUNT_COLD_ROWS_PER_ACCOUNT" "${cold_rows_per_account}"
require_bool "MULTI_ACCOUNT_ENFORCE_TARGET_TOTAL_ROWS" "${enforce_target_total_rows}"
require_bool "MULTI_ACCOUNT_REQUIRE_ACCOUNT_RESULT_TSV" "${require_account_result_tsv}"
require_bool "MULTI_ACCOUNT_REQUIRE_ARTIFACT_URI" "${require_artifact_uri}"

hot_count="$(csv_count "${hot_ids}")"
cold_count="$(csv_count "${cold_ids}")"
total_account_count="$((hot_count + cold_count))"
if ((total_account_count < min_total_account_count)); then
  echo "multi-account fixture requires at least ${min_total_account_count} accounts: total=${total_account_count}" >&2
  exit 1
fi
cold_total_rows="$((cold_count * cold_rows_per_account))"
hot_rows_remainder=0
if [[ -z "${hot_rows_per_account}" ]]; then
  hot_total_rows="$((target_total_rows - cold_total_rows))"
  if ((hot_total_rows <= 0)); then
    echo "MULTI_ACCOUNT_TARGET_TOTAL_ROWS must exceed cold planned rows" >&2
    exit 1
  fi
  hot_rows_per_account="$((hot_total_rows / hot_count))"
  hot_rows_remainder="$((hot_total_rows % hot_count))"
else
  require_positive_integer "MULTI_ACCOUNT_HOT_ROWS_PER_ACCOUNT" "${hot_rows_per_account}"
  hot_total_rows="$((hot_count * hot_rows_per_account))"
fi
total_rows="$((hot_count * hot_rows_per_account + hot_rows_remainder + cold_total_rows))"
if [[ "${enforce_target_total_rows}" == "true" && "${total_rows}" -ne "${target_total_rows}" ]]; then
  echo "multi-account total rows must match target: total=${total_rows} target=${target_total_rows}" >&2
  exit 1
fi
if [[ "${require_account_result_tsv}" == "true" ]]; then
  require_file "MULTI_ACCOUNT_ACCOUNT_RESULT_TSV" "${account_result_tsv}"
fi
if [[ "${require_artifact_uri}" == "true" ]]; then
  require_non_empty "MULTI_ACCOUNT_ARTIFACT_URI" "${artifact_uri}"
fi

print_plan() {
  echo "[transaction-100m-multi-account] name=${name}"
  echo "[transaction-100m-multi-account] run_id=${run_id}"
  echo "[transaction-100m-multi-account] hot_account_count=${hot_count}"
  echo "[transaction-100m-multi-account] cold_account_count=${cold_count}"
  echo "[transaction-100m-multi-account] total_account_count=${total_account_count}"
  echo "[transaction-100m-multi-account] minimum_total_account_count=${min_total_account_count}"
  echo "[transaction-100m-multi-account] target_total_rows=${target_total_rows}"
  echo "[transaction-100m-multi-account] total_rows=${total_rows}"
  echo "[transaction-100m-multi-account] hot_rows_per_account=${hot_rows_per_account} hot_rows_remainder=${hot_rows_remainder}"
  echo "[transaction-100m-multi-account] cold_rows_per_account=${cold_rows_per_account}"
  echo "[transaction-100m-multi-account] account_result_tsv=${account_result_tsv:-missing}"
  echo "[transaction-100m-multi-account] artifact_uri=${artifact_uri:-missing}"
  echo "[transaction-100m-multi-account] k6_env=K6_HOT_ACCOUNT_IDS=${hot_ids} K6_COLD_ACCOUNT_IDS=${cold_ids}"
  echo "[transaction-100m-multi-account] k6_command=${k6_runner}"
  echo "[transaction-100m-multi-account] manifest_json=${manifest_json}"
  echo "[transaction-100m-multi-account] distribution_tsv=${distribution_tsv}"
  echo "[transaction-100m-multi-account] account_summary_tsv=${account_summary_tsv}"
  echo "[transaction-100m-multi-account] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

mkdir -p "${output_dir}"

write_distribution_tsv() {
  {
    printf "account_group\taccount_id\tplanned_rows\n"
    IFS=',' read -r -a hot_items <<<"${hot_ids}"
    local index=0
    local item planned_rows
    for item in "${hot_items[@]}"; do
      item="${item//[[:space:]]/}"
      planned_rows="${hot_rows_per_account}"
      if ((index < hot_rows_remainder)); then
        planned_rows="$((planned_rows + 1))"
      fi
      printf "hot\t%s\t%s\n" "${item}" "${planned_rows}"
      index="$((index + 1))"
    done
    IFS=',' read -r -a cold_items <<<"${cold_ids}"
    for item in "${cold_items[@]}"; do
      item="${item//[[:space:]]/}"
      printf "cold\t%s\t%s\n" "${item}" "${cold_rows_per_account}"
    done
  } >"${distribution_tsv}"
}

write_account_summary_tsv() {
  {
    printf "scope\taccount_count\tplanned_rows\tplanned_row_skew_ratio\taccepted_p95_spread_ms\trejected_429_spread\n"
    if [[ -z "${account_result_tsv}" ]]; then
      printf "all\t0\t0\t0.000\t0\t0\n"
      return 0
    fi
    require_file "MULTI_ACCOUNT_ACCOUNT_RESULT_TSV" "${account_result_tsv}"
    awk -F '\t' -v expected_count="$((hot_count + cold_count))" -v expected_total="${total_rows}" '
      NR == 1 {
        for (i = 1; i <= NF; i++) col[$i] = i
        split("account_group account_id planned_rows accepted_p95_ms rejected_429_rate", required, " ")
        for (i in required) {
          if (!(required[i] in col)) {
            printf "missing required column: %s\n", required[i] > "/dev/stderr"
            exit 2
          }
        }
        next
      }
      {
        count++
        planned_rows = $(col["planned_rows"]) + 0
        rows += planned_rows
        if (count == 1 || planned_rows < min_rows) min_rows = planned_rows
        if (count == 1 || planned_rows > max_rows) max_rows = planned_rows
        p95 = $(col["accepted_p95_ms"]) + 0
        rejected = $(col["rejected_429_rate"]) + 0
        if (count == 1 || p95 < min_p95) min_p95 = p95
        if (count == 1 || p95 > max_p95) max_p95 = p95
        if (count == 1 || rejected < min_rejected) min_rejected = rejected
        if (count == 1 || rejected > max_rejected) max_rejected = rejected
      }
      END {
        if (count < expected_count) {
          printf "account result row count below expected: count=%s expected=%s\n", count, expected_count > "/dev/stderr"
          exit 3
        }
        if (rows != expected_total) {
          printf "account result planned rows mismatch: rows=%s expected=%s\n", rows, expected_total > "/dev/stderr"
          exit 4
        }
        skew_ratio = (min_rows > 0) ? max_rows / min_rows : 0
        printf "all\t%d\t%d\t%.3f\t%.0f\t%.3f\n", count, rows, skew_ratio, max_p95 - min_p95, max_rejected - min_rejected
      }
    ' "${account_result_tsv}"
  } >"${account_summary_tsv}"
}

write_distribution_tsv
write_account_summary_tsv

cat >"${manifest_json}" <<JSON
{"name":"${name}","runId":"${run_id}","artifactUri":"${artifact_uri}","hotAccountIds":$(json_array "${hot_ids}"),"coldAccountIds":$(json_array "${cold_ids}"),"minimumTotalAccountCount":${min_total_account_count},"targetTotalRows":${target_total_rows},"hotRowsPerAccount":${hot_rows_per_account},"hotRowsRemainder":${hot_rows_remainder},"coldRowsPerAccount":${cold_rows_per_account},"totalRows":${total_rows},"hotWindow":{"from":"${hot_from}","to":"${hot_to}"},"coldWindow":{"from":"${cold_from}","to":"${cold_to}"}}
JSON

cat >"${report_md}" <<REPORT
# Transaction 100M Multi-Account Fixture

## Summary

- fixture: ${name}
- actual execution run id: ${run_id}
- artifact reference: ${artifact_uri}
- hot account count: ${hot_count}
- cold account count: ${cold_count}
- total account count: ${total_account_count}
- minimum total account count: ${min_total_account_count}
- target total rows: ${target_total_rows}
- total planned rows: ${total_rows}
- hot rows per account: ${hot_rows_per_account} (+1 for first ${hot_rows_remainder} hot accounts)
- cold rows per account: ${cold_rows_per_account}
- k6 hot accounts: ${hot_ids}
- k6 cold accounts: ${cold_ids}
- per-account fairness metric: aquila_transaction_fairness_429_count{account_id,account_group}
- global throughput and per-account fairness are reported separately.
- account result summary: ${account_summary_tsv}

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
- distribution TSV: ${distribution_tsv}
- account result summary TSV: ${account_summary_tsv}
REPORT

echo "${report_md}"
