#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/compare-ec2-direct-vs-nginx-latency.sh [--print-plan]

Environment:
  EC2_DIRECT_K6_SUMMARY_MD required
  EC2_NGINX_K6_SUMMARY_MD required
  EC2_COMPARISON_NAME default ec2-direct-vs-nginx-<timestamp>
  EC2_COMPARISON_OUTPUT_MD default build/reports/ec2-local-db/<name>/<name>.md
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan) mode="print-plan" ;;
    -h|--help) usage; exit 0 ;;
    *) usage; exit 1 ;;
  esac
  shift
done

direct_md="${EC2_DIRECT_K6_SUMMARY_MD:-}"
nginx_md="${EC2_NGINX_K6_SUMMARY_MD:-}"
name="${EC2_COMPARISON_NAME:-ec2-direct-vs-nginx-$(date +%Y-%m-%d-%H%M%S)}"
output_md="${EC2_COMPARISON_OUTPUT_MD:-build/reports/ec2-local-db/${name}/${name}.md}"

require_file() {
  local key="$1"
  local path="$2"
  if [[ -z "${path}" || ! -f "${path}" ]]; then
    echo "${key} file is required: ${path:-missing}" >&2
    exit 1
  fi
}

metric_value() {
  local path="$1"
  local label="$2"
  awk -F': ' -v label="- ${label}: " '$0 ~ "^" label {print $2; found=1; exit} END {exit !found}' "${path}"
}

delta_value() {
  local direct="$1"
  local nginx="$2"
  awk -v direct="${direct}" -v nginx="${nginx}" 'BEGIN {
    if (direct == "n/a" || nginx == "n/a") {
      print "n/a";
      exit;
    }
    printf "%.3f", nginx - direct;
  }'
}

print_plan() {
  echo "[ec2-direct-vs-nginx] mode=${mode}"
  echo "[ec2-direct-vs-nginx] direct_md=${direct_md:-missing}"
  echo "[ec2-direct-vs-nginx] nginx_md=${nginx_md:-missing}"
  echo "[ec2-direct-vs-nginx] output_md=${output_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

require_file "EC2_DIRECT_K6_SUMMARY_MD" "${direct_md}"
require_file "EC2_NGINX_K6_SUMMARY_MD" "${nginx_md}"
mkdir -p "$(dirname "${output_md}")"

labels=(
  "hot first p95 ms"
  "hot first p99 ms"
  "hot cursor p95 ms"
  "hot cursor p99 ms"
  "cold first p95 ms"
  "cold first p99 ms"
  "cold cursor p95 ms"
  "cold cursor p99 ms"
  "transaction 429 rate"
  "transaction 503 rate"
  "transaction 503 count"
)

{
  echo "# EC2 Direct vs Nginx 100m Latency Comparison"
  echo
  echo "- direct summary: ${direct_md}"
  echo "- nginx summary: ${nginx_md}"
  echo
  echo "| metric | direct | nginx | nginx-direct |"
  echo "|---|---:|---:|---:|"
  for label in "${labels[@]}"; do
    direct_value="$(metric_value "${direct_md}" "${label}")"
    nginx_value="$(metric_value "${nginx_md}" "${label}")"
    delta="$(delta_value "${direct_value}" "${nginx_value}")"
    printf "| %s | %s | %s | %s |\n" "${label}" "${direct_value}" "${nginx_value}" "${delta}"
  done
} >"${output_md}"

echo "${output_md}"
