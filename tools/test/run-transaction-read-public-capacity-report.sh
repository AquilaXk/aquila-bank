#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-public-capacity-report.sh [--print-plan]

Environment:
  PUBLIC_CAPACITY_REPORT_NAME                 default transaction-read-public-capacity-<timestamp>
  PUBLIC_CAPACITY_REPORT_OUTPUT_DIR           default docs/performance-results
  PUBLIC_CAPACITY_REPORT_ARRIVAL_SUMMARY_DIR  optional summary dir for arrival gate sample/live summaries
  PUBLIC_CAPACITY_REPORT_ARRIVAL_RUN_K6       true|false, default false
  PUBLIC_CAPACITY_REPORT_RESOURCE_SNAPSHOT_TSV optional resource snapshot TSV
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

name="${PUBLIC_CAPACITY_REPORT_NAME:-transaction-read-public-capacity-$(date +%Y-%m-%d-%H%M%S)}"
output_dir="${PUBLIC_CAPACITY_REPORT_OUTPUT_DIR:-docs/performance-results}"
output_path="${output_dir%/}/${name}.md"
arrival_summary_dir="${PUBLIC_CAPACITY_REPORT_ARRIVAL_SUMMARY_DIR:-}"
arrival_run_k6="${PUBLIC_CAPACITY_REPORT_ARRIVAL_RUN_K6:-false}"
resource_snapshot="${PUBLIC_CAPACITY_REPORT_RESOURCE_SNAPSHOT_TSV:-}"
arrival_gate="tools/test/run-oci-public-api-arrival-capacity-gate.sh"
matrix_gate="tools/test/run-oci-a1-edge-backend-budget-matrix.sh"
arrival_gate_name="${name}-arrival"
arrival_output_dir="build/reports/k6/${arrival_gate_name}"
arrival_report="${arrival_output_dir}/${arrival_gate_name}-arrival-capacity.md"
arrival_summary_tsv="${arrival_output_dir}/${arrival_gate_name}-arrival-capacity.tsv"
matrix_name="${name}-matrix"
matrix_output_dir="build/reports/oci-a1-budget"
matrix_report="${matrix_output_dir}/${matrix_name}-budget-matrix.md"

require_bool() {
  local name="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${name} must be true or false: ${value}" >&2
    exit 1
  fi
}

status_from_report() {
  local file="$1"
  if [[ ! -s "${file}" ]]; then
    echo "missing"
    return
  fi
  awk -F '=' '/gate_status=/ {print $2; exit}' "${file}"
}

arrival_table_markdown() {
  local file="$1"
  if [[ ! -s "${file}" ]]; then
    echo "| rate | status | total 429 | edge 429 | backend 429 | unknown 429 | 502 | 503 | edge delayed | accepted p95 ms | accepted 200 |"
    echo "| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
    echo "| n/a | missing | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a |"
    return
  fi
  awk -F '\t' '
    BEGIN {
      print "| rate | status | total 429 | edge 429 | backend 429 | unknown 429 | 502 | 503 | edge delayed | accepted p95 ms | accepted 200 |"
      print "| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
    }
    NR > 1 {
      printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $5, $6, $7, $8, $9, $11, $12
    }
  ' "${file}"
}

resource_table_markdown() {
  local file="$1"
  if [[ -z "${file}" || ! -s "${file}" ]]; then
    echo "| component | cpu_percent | memory_mib | note |"
    echo "| --- | ---: | ---: | --- |"
    echo "| n/a | n/a | n/a | resource snapshot missing |"
    return
  fi
  awk -F '\t' '
    BEGIN {
      print "| component | cpu_percent | memory_mib | note |"
      print "| --- | ---: | ---: | --- |"
    }
    NR > 1 {
      printf "| %s | %s | %s | %s |\n", $1, $2, $3, $4
    }
  ' "${file}"
}

next_bottleneck_notes() {
  local file="$1"
  if [[ ! -s "${file}" ]]; then
    echo "- arrival gate summary가 없어 live run artifact부터 확보합니다."
    return
  fi
  awk -F '\t' '
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        if ($i == "arrival_rate") rate_col = i
        if ($i == "status") status_col = i
        if ($i == "total_429_rate") total_col = i
        if ($i == "edge_429_rate") edge_col = i
        if ($i == "backend_429_rate") backend_col = i
        if ($i == "transaction_502_count") bad_gateway_col = i
        if ($i == "transaction_503_count") unavailable_col = i
        if ($i == "edge_delayed_rate") delayed_col = i
        if ($i == "accepted_p95_ms") p95_col = i
      }
      next
    }
    $rate_col == "10" {
      found = 1
      if (($bad_gateway_col + 0) > 0) print "- 502가 있으면 upstream keepalive/backend connection close 상관관계를 먼저 봅니다."
      if (($unavailable_col + 0) > 0) print "- 503이 있으면 backend admission과 app health를 먼저 봅니다."
      if (($delayed_col + 0) > 0.25) print "- edge delayed ratio가 25%를 넘으면 Nginx rate/burst/delay queue를 먼저 줄입니다."
      if (($edge_col + 0) >= 0.08) print "- edge 429가 8% 이상이면 Nginx transaction-read rate/burst/delay 조정이 1순위입니다."
      if (($backend_col + 0) >= 0.08) print "- backend 429가 8% 이상이면 admission adaptive max와 Hikari pending을 함께 봅니다."
      if (($p95_col + 0) > 350) print "- accepted p95가 350ms를 넘으면 Nginx upstream timing과 backend timer를 비교합니다."
      if ($status_col == "pass" && ($bad_gateway_col + 0) == 0 && ($unavailable_col + 0) == 0 && ($delayed_col + 0) < 0.25 && ($p95_col + 0) <= 350 && ($total_col + 0) < 0.10) {
        print "- arrival-10rps sample is inside 429/delay/p95/5xx budget; next live check is sustained VU16 soak source split."
      }
    }
    END {
      if (!found) print "- arrival-10rps row가 없어 10rps summary 수집부터 다시 실행합니다."
    }
  ' "${file}"
}

require_bool "PUBLIC_CAPACITY_REPORT_ARRIVAL_RUN_K6" "${arrival_run_k6}"

print_plan() {
  echo "[transaction-read-public-report] name=${name}"
  echo "[transaction-read-public-report] output=${output_path}"
  echo "[transaction-read-public-report] arrival_gate=${arrival_gate}"
  echo "[transaction-read-public-report] arrival_summary_dir=${arrival_summary_dir:-missing}"
  echo "[transaction-read-public-report] arrival_run_k6=${arrival_run_k6}"
  echo "[transaction-read-public-report] arrival_report=${arrival_report}"
  echo "[transaction-read-public-report] matrix_gate=${matrix_gate}"
  echo "[transaction-read-public-report] matrix_report=${matrix_report}"
  echo "[transaction-read-public-report] resource_snapshot=${resource_snapshot:-missing}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

mkdir -p "${output_dir}"

set +e
OCI_PUBLIC_ARRIVAL_GATE_NAME="${arrival_gate_name}" \
OCI_PUBLIC_ARRIVAL_SUMMARY_DIR="${arrival_summary_dir}" \
OCI_PUBLIC_ARRIVAL_OUTPUT_DIR="${arrival_output_dir}" \
OCI_PUBLIC_ARRIVAL_RUN_K6="${arrival_run_k6}" \
  "${arrival_gate}" >/dev/null
arrival_status_code=$?

OCI_A1_BUDGET_MATRIX_NAME="${matrix_name}" \
OCI_A1_BUDGET_MATRIX_OUTPUT_DIR="${matrix_output_dir}" \
  "${matrix_gate}" >/dev/null
matrix_status_code=$?
set -e

arrival_status="$(status_from_report "${arrival_report}")"
matrix_status="$(status_from_report "${matrix_report}")"
gate_status="pass"
if [[ "${arrival_status_code}" -ne 0 || "${matrix_status_code}" -ne 0 || "${arrival_status}" != "pass" || "${matrix_status}" != "pass" ]]; then
  gate_status="fail"
fi

{
  echo "# ${name}"
  echo
  echo "## Summary"
  echo
  echo "- gate_status=${gate_status}"
  echo "- runtime: OCI A1 Flex 4 OCPU / 24GB + data 200GB self-managed PostgreSQL 18"
  echo "- target: arrival-10rps 429 < 10%, edge delayed ratio < 25%, 502/503 = 0, accepted p95 < 350ms"
  echo
  echo "## Gates"
  echo
  echo "| Gate | Status | Report |"
  echo "| --- | --- | --- |"
  echo "| arrival capacity | ${arrival_status} | ${arrival_report} |"
  echo "| budget matrix | ${matrix_status} | ${matrix_report} |"
  echo
  echo "## K6 Arrival Table"
  echo
  arrival_table_markdown "${arrival_summary_tsv}"
  echo
  echo "## Nginx 429 Source Split"
  echo
  echo "- edge/backend/unknown 429과 502는 K6 summary custom metric에서 run id별 report로 분리합니다."
  echo "- source split detail은 arrival gate의 per-rate source report를 확인합니다."
  echo
  echo "## Resource Snapshot"
  echo
  resource_table_markdown "${resource_snapshot}"
  echo
  echo "## Next Bottleneck Candidates"
  echo
  next_bottleneck_notes "${arrival_summary_tsv}"
} >"${output_path}"

echo "${output_path}"

if [[ "${gate_status}" == "fail" ]]; then
  echo "transaction read public capacity report has failing gates: ${output_path}" >&2
  exit 1
fi
