#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/compare-transaction-100m-local-vs-staging-rds.sh [--print-plan|--dry-run]

Required for actual run:
  LOCAL_K6_SUMMARY_MD
  STAGING_RDS_K6_SUMMARY_MD

Environment:
  COMPARISON_RESULT_NAME       default transaction-100m-local-vs-staging-rds-<timestamp>
  COMPARISON_OUTPUT_DIR        default docs/performance-results
  LOCAL_K6_LABEL               default local-docker
  STAGING_RDS_K6_LABEL         default staging-rds-gp3

Examples:
  tools/test/compare-transaction-100m-local-vs-staging-rds.sh --print-plan
  LOCAL_K6_SUMMARY_MD=docs/performance-results/local.md STAGING_RDS_K6_SUMMARY_MD=docs/performance-results/staging.md \
    tools/test/compare-transaction-100m-local-vs-staging-rds.sh
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    --dry-run)
      mode="dry-run"
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

name="${COMPARISON_RESULT_NAME:-transaction-100m-local-vs-staging-rds-$(date +%Y-%m-%d-%H%M%S)}"
output_dir="${COMPARISON_OUTPUT_DIR:-docs/performance-results}"
output_path="${output_dir}/${name}.md"
local_summary="${LOCAL_K6_SUMMARY_MD:-}"
staging_summary="${STAGING_RDS_K6_SUMMARY_MD:-}"
local_label="${LOCAL_K6_LABEL:-local-docker}"
staging_label="${STAGING_RDS_K6_LABEL:-staging-rds-gp3}"

print_plan() {
  echo "[transaction-100m-comparison] mode=${mode}"
  echo "[transaction-100m-comparison] local=${local_summary:-missing}"
  echo "[transaction-100m-comparison] staging=${staging_summary:-missing}"
  echo "[transaction-100m-comparison] output=${output_path}"
}

require_file() {
  local path="$1"
  local label="$2"
  if [[ -z "${path}" || ! -f "${path}" ]]; then
    echo "${label} summary markdown not found: ${path:-missing}" >&2
    exit 1
  fi
}

metric_value() {
  local file="$1"
  local key="$2"
  local value
  value="$(awk -F ': ' -v key="- ${key}" '$1 == key {print $2}' "${file}" | tail -1)"
  printf "%s" "${value:-missing}"
}

write_metric_row() {
  local metric="$1"
  local local_value staging_value
  local_value="$(metric_value "${local_summary}" "${metric}")"
  staging_value="$(metric_value "${staging_summary}" "${metric}")"
  printf "| %s | %s | %s |\n" "${metric}" "${local_value}" "${staging_value}"
}

write_report() {
  require_file "${local_summary}" "LOCAL_K6_SUMMARY_MD"
  require_file "${staging_summary}" "STAGING_RDS_K6_SUMMARY_MD"
  mkdir -p "${output_dir}"
  {
    echo "# ${name}"
    echo
    echo "## Inputs"
    echo
    echo "- ${local_label}: ${local_summary}"
    echo "- ${staging_label}: ${staging_summary}"
    echo
    echo "## Metric Comparison"
    echo
    printf "| Metric | %s | %s |\n" "${local_label}" "${staging_label}"
    echo "| --- | ---: | ---: |"
    write_metric_row "http_req_failed rate"
    write_metric_row "checks rate"
    write_metric_row "transaction 429 rate"
    write_metric_row "hot first p95 ms"
    write_metric_row "hot cursor p95 ms"
    write_metric_row "cold first p95 ms"
    write_metric_row "cold cursor p95 ms"
    echo
    echo "## Notes"
    echo
    echo '- 두 입력은 `archive-k6-transaction-100m-result.sh`가 만든 Markdown 형식을 기준으로 비교합니다.'
    echo "- staging URL, token, raw Authorization header는 기록하지 않습니다."
  } >"${output_path}"
  echo "${output_path}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  echo "write ${output_path}"
  exit 0
fi
write_report
