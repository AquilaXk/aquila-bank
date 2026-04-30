#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-client-close-499-gate.sh [--print-plan]

Environment:
  CLIENT_CLOSE_499_NAME       default transaction-read-client-close-499-<timestamp>
  CLIENT_CLOSE_499_INPUT_TSV  required TSV: run,nginx_499_count,k6_timeout_count,nginx_keepalive_close_count,upstream_close_count
  CLIENT_CLOSE_499_OUTPUT_DIR default build/reports/k6/<gate>
  CLIENT_CLOSE_499_TARGET     default 0
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

name="${CLIENT_CLOSE_499_NAME:-transaction-read-client-close-499-$(date +%Y-%m-%d-%H%M%S)}"
input_tsv="${CLIENT_CLOSE_499_INPUT_TSV:-}"
output_dir="${CLIENT_CLOSE_499_OUTPUT_DIR:-build/reports/k6/${name}}"
target_499_count="${CLIENT_CLOSE_499_TARGET:-0}"
summary_tsv="${output_dir}/${name}-499.tsv"
report_md="${output_dir}/${name}-499.md"

require_non_negative_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[0-9]+$ ]]; then
    echo "${name} must be a non-negative integer: ${value}" >&2
    exit 1
  fi
}

print_plan() {
  echo "[transaction-read-499] name=${name}"
  echo "[transaction-read-499] input_tsv=${input_tsv:-missing}"
  echo "[transaction-read-499] output_dir=${output_dir}"
  echo "[transaction-read-499] target_499_count=${target_499_count}"
  echo "[transaction-read-499] summary_tsv=${summary_tsv}"
  echo "[transaction-read-499] report_md=${report_md}"
}

require_non_negative_integer "CLIENT_CLOSE_499_TARGET" "${target_499_count}"

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
    echo "CLIENT_CLOSE_499_INPUT_TSV is required" >&2
    exit 1
  fi
  exit 0
fi

if [[ -z "${input_tsv}" || ! -s "${input_tsv}" ]]; then
  echo "CLIENT_CLOSE_499_INPUT_TSV is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"
awk -F '\t' -v target="${target_499_count}" '
  NR == 1 {
    for (i = 1; i <= NF; i++) {
      col[$i] = i
    }
    print "run\tstatus\tnginx_499_count\tcause\tk6_timeout_count\tnginx_keepalive_close_count\tupstream_close_count"
    next
  }
  {
    count = $col["nginx_499_count"] + 0
    k6_timeout = $col["k6_timeout_count"] + 0
    keepalive = $col["nginx_keepalive_close_count"] + 0
    upstream = $col["upstream_close_count"] + 0
    cause = "none"
    if (count > 0) {
      if (k6_timeout > 0) {
        cause = "k6-timeout"
      } else if (keepalive > 0) {
        cause = "nginx-keepalive-close"
      } else if (upstream > 0) {
        cause = "upstream-close"
      } else {
        cause = "unclassified"
      }
    }
    status = count > target ? "fail" : "pass"
    if (status == "fail") fail_count++
    printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
      $col["run"], status, count, cause, k6_timeout, keepalive, upstream
  }
  END {
    print "# fail_count=" (fail_count + 0) > "/dev/stderr"
  }
' "${input_tsv}" >"${summary_tsv}" 2>"${output_dir}/${name}-499.meta"

fail_count="$(awk -F '=' '/^# fail_count=/ { print $2 }' "${output_dir}/${name}-499.meta")"
gate_status="pass"
if [[ "${fail_count}" != "0" ]]; then
  gate_status="fail"
fi

status_table="$(awk -F '\t' '
  BEGIN {
    print "| Run | Status | 499 | Cause | k6 timeout | Nginx keepalive close | upstream close |"
    print "| --- | --- | ---: | --- | ---: | ---: | ---: |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $5, $6, $7
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read 499 Client-Close Gate

## Summary

- gate_status=${gate_status}
- 499 target: ${target_499_count}
- cause labels: k6-timeout, nginx-keepalive-close, upstream-close, unclassified

## Result Table

${status_table}

## Contract Notes

- k6 timeout은 client deadline이 Nginx delay/backoff보다 짧은 경우로 분류한다.
- Nginx keepalive close는 edge keepalive/connection reuse 경계로 분류한다.
- upstream close는 backend connection reset 또는 upstream response close 경계로 분류한다.

## Artifacts

- summary TSV: ${summary_tsv}
- input TSV: ${input_tsv}
REPORT

echo "${report_md}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read 499 client-close gate failed: ${summary_tsv}" >&2
  exit 1
fi
