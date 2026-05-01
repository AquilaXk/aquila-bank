#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-nginx-failure-correlation-gate.sh [--print-plan]

Environment:
  FAILURE_CORRELATION_NAME                   default transaction-read-failure-correlation-<timestamp>
  FAILURE_CORRELATION_ACCESS_LOG             required Nginx JSON access log
  FAILURE_CORRELATION_OUTPUT_DIR             default build/reports/k6/<gate>
  FAILURE_CORRELATION_TARGET_499             default 0
  FAILURE_CORRELATION_TARGET_502             default 0
  FAILURE_CORRELATION_CLIENT_TIMEOUT_SECONDS default 30
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

name="${FAILURE_CORRELATION_NAME:-transaction-read-failure-correlation-$(date +%Y-%m-%d-%H%M%S)}"
access_log="${FAILURE_CORRELATION_ACCESS_LOG:-}"
output_dir="${FAILURE_CORRELATION_OUTPUT_DIR:-build/reports/k6/${name}}"
target_499_count="${FAILURE_CORRELATION_TARGET_499:-0}"
target_502_count="${FAILURE_CORRELATION_TARGET_502:-0}"
client_timeout_seconds="${FAILURE_CORRELATION_CLIENT_TIMEOUT_SECONDS:-30}"
summary_tsv="${output_dir}/${name}-failure-correlation.tsv"
report_md="${output_dir}/${name}-failure-correlation.md"

require_non_negative_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${name} must be a non-negative integer: ${value}" >&2
    exit 1
  fi
}

print_plan() {
  echo "[transaction-read-failure-correlation] name=${name}"
  echo "[transaction-read-failure-correlation] access_log=${access_log:-missing}"
  echo "[transaction-read-failure-correlation] output_dir=${output_dir}"
  echo "[transaction-read-failure-correlation] target_499_count=${target_499_count}"
  echo "[transaction-read-failure-correlation] target_502_count=${target_502_count}"
  echo "[transaction-read-failure-correlation] client_timeout_seconds=${client_timeout_seconds}"
  echo "[transaction-read-failure-correlation] summary_tsv=${summary_tsv}"
  echo "[transaction-read-failure-correlation] report_md=${report_md}"
}

require_non_negative_integer "FAILURE_CORRELATION_TARGET_499" "${target_499_count}"
require_non_negative_integer "FAILURE_CORRELATION_TARGET_502" "${target_502_count}"
require_non_negative_integer "FAILURE_CORRELATION_CLIENT_TIMEOUT_SECONDS" "${client_timeout_seconds}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  if [[ -z "${access_log}" || ! -s "${access_log}" ]]; then
    echo "FAILURE_CORRELATION_ACCESS_LOG is required" >&2
    exit 1
  fi
  exit 0
fi

if [[ -z "${access_log}" || ! -s "${access_log}" ]]; then
  echo "FAILURE_CORRELATION_ACCESS_LOG is required" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"
{
  printf "run\tstatus\tnginx_499_count\tupstream_502_count\tk6_timeout_count\tnginx_keepalive_close_count\tupstream_close_count\trequest_time_0_2ms_502_count\tcause\n"
  jq -r -s \
    --argjson target499 "${target_499_count}" \
    --argjson target502 "${target_502_count}" \
    --argjson clientTimeout "${client_timeout_seconds}" '
      def request_time: (.request_time | tonumber? // 0);
      def status_code: (.status | tonumber? // 0);
      def is_transaction_read: ((.request // "") | contains("/api/v1/transactions"));
      def is_502: ((status_code == 502) or (((.upstream_status // "") | tostring) | contains("502")));
      def immediate_502: (is_502 and request_time <= 0.002);
      map(select(is_transaction_read))
      | group_by(.k6_run_id // "unknown")
      | .[]
      | {
          run: (.[0].k6_run_id // "unknown"),
          c499: ([.[] | select(status_code == 499)] | length),
          c502: ([.[] | select(is_502)] | length),
          timeout: ([.[] | select(status_code == 499 and request_time >= $clientTimeout)] | length),
          keepalive: ([.[] | select(immediate_502)] | length),
          upstream_close: ([.[] | select(is_502 and (immediate_502 | not))] | length),
          immediate: ([.[] | select(immediate_502)] | length)
        }
      | .status = (if (.c499 > $target499 or .c502 > $target502) then "fail" else "pass" end)
      | .cause = (
          if .timeout > 0 then "k6-timeout"
          elif .keepalive > 0 then "nginx-keepalive-close"
          elif .upstream_close > 0 then "upstream-close"
          elif (.c499 > 0 or .c502 > 0) then "unclassified"
          else "none"
          end
        )
      | [.run, .status, .c499, .c502, .timeout, .keepalive, .upstream_close, .immediate, .cause]
      | @tsv
    ' "${access_log}"
} >"${summary_tsv}"

fail_count="$(awk -F '\t' 'NR > 1 && $2 == "fail" { count++ } END { print count + 0 }' "${summary_tsv}")"
gate_status="pass"
if [[ "${fail_count}" != "0" ]]; then
  gate_status="fail"
fi

status_table="$(awk -F '\t' '
  BEGIN {
    print "| Run | Status | 499 | 502 | k6 timeout | Nginx keepalive close | upstream close | 0-2ms 502 | Cause |"
    print "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $5, $6, $7, $8, $9
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read Nginx Failure Correlation Gate

## Summary

- gate_status=${gate_status}
- 499 target: ${target_499_count}
- 502 target: ${target_502_count}
- client timeout boundary seconds: ${client_timeout_seconds}

## Result Table

${status_table}

## Contract Notes

- request_time 0~2ms 502는 stale upstream keepalive 또는 backend immediate close 후보로 분류한다.
- 499가 client timeout boundary 이상에서 닫히면 k6 timeout 후보로 분류한다.
- 502/499는 overload 방어 budget이 아니라 zero-fail gate로 유지한다.

## Artifacts

- summary TSV: ${summary_tsv}
- access log: ${access_log}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read failure correlation failed: ${summary_tsv}" >&2
  exit 1
fi
