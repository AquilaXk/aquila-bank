#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/compare-ec2-direct-vs-nginx-latency.sh [--print-plan]

Environment:
  EC2_DIRECT_K6_SUMMARY_MD required
  EC2_NGINX_K6_SUMMARY_MD required
  EC2_LOCAL_LOOPBACK_K6_SUMMARY_MD optional
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
local_loopback_md="${EC2_LOCAL_LOOPBACK_K6_SUMMARY_MD:-}"
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

metric_value_or_na() {
  local path="$1"
  local label="$2"
  awk -F': ' -v label="- ${label}: " '$0 ~ "^" label {print $2; found=1; exit} END {if (!found) print "n/a"}' "${path}"
}

delta_value() {
  local direct="$1"
  local target="$2"
  awk -v direct="${direct}" -v target="${target}" 'BEGIN {
    if (direct == "n/a" || target == "n/a") {
      print "n/a";
      exit;
    }
    printf "%.3f", target - direct;
  }'
}

same_or_mixed_run_id() {
  local direct="$1"
  local nginx="$2"
  local local_loopback="$3"
  if [[ "${direct}" == "n/a" && "${nginx}" == "n/a" && "${local_loopback}" == "n/a" ]]; then
    echo "n/a"
    return
  fi
  if [[ "${local_loopback}" == "n/a" ]]; then
    if [[ "${direct}" == "${nginx}" ]]; then
      echo "${direct}"
    else
      echo "mixed direct=${direct} nginx=${nginx}"
    fi
    return
  fi
  if [[ "${direct}" == "${nginx}" && "${direct}" == "${local_loopback}" ]]; then
    echo "${direct}"
  else
    echo "mixed direct=${direct} nginx=${nginx} local_loopback=${local_loopback}"
  fi
}

print_plan() {
  echo "[ec2-direct-vs-nginx] mode=${mode}"
  echo "[ec2-direct-vs-nginx] direct_md=${direct_md:-missing}"
  echo "[ec2-direct-vs-nginx] nginx_md=${nginx_md:-missing}"
  echo "[ec2-direct-vs-nginx] local_loopback_md=${local_loopback_md:-optional-missing}"
  echo "[ec2-direct-vs-nginx] output_md=${output_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

require_file "EC2_DIRECT_K6_SUMMARY_MD" "${direct_md}"
require_file "EC2_NGINX_K6_SUMMARY_MD" "${nginx_md}"
if [[ -n "${local_loopback_md}" ]]; then
  require_file "EC2_LOCAL_LOOPBACK_K6_SUMMARY_MD" "${local_loopback_md}"
fi
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

direct_run_id="$(metric_value_or_na "${direct_md}" "run id")"
nginx_run_id="$(metric_value_or_na "${nginx_md}" "run id")"
if [[ -n "${local_loopback_md}" ]]; then
  local_loopback_run_id="$(metric_value_or_na "${local_loopback_md}" "run id")"
else
  local_loopback_run_id="n/a"
fi
run_id="$(same_or_mixed_run_id "${direct_run_id}" "${nginx_run_id}" "${local_loopback_run_id}")"

{
  if [[ -n "${local_loopback_md}" ]]; then
    echo "# EC2 Direct vs Nginx vs Local Loopback 100m Latency Comparison"
  else
    echo "# EC2 Direct vs Nginx 100m Latency Comparison"
  fi
  echo
  echo "- run id: ${run_id}"
  echo "- direct summary: ${direct_md}"
  echo "- nginx summary: ${nginx_md}"
  if [[ -n "${local_loopback_md}" ]]; then
    echo "- local loopback summary: ${local_loopback_md}"
  fi
  echo
  if [[ -n "${local_loopback_md}" ]]; then
    echo "| metric | direct | nginx | local_loopback | nginx-direct | local-direct |"
    echo "|---|---:|---:|---:|---:|---:|"
  else
    echo "| metric | direct | nginx | nginx-direct |"
    echo "|---|---:|---:|---:|"
  fi
  for label in "${labels[@]}"; do
    direct_value="$(metric_value "${direct_md}" "${label}")"
    nginx_value="$(metric_value "${nginx_md}" "${label}")"
    nginx_delta="$(delta_value "${direct_value}" "${nginx_value}")"
    if [[ -n "${local_loopback_md}" ]]; then
      local_loopback_value="$(metric_value "${local_loopback_md}" "${label}")"
      local_delta="$(delta_value "${direct_value}" "${local_loopback_value}")"
      printf "| %s | %s | %s | %s | %s | %s |\n" \
        "${label}" \
        "${direct_value}" \
        "${nginx_value}" \
        "${local_loopback_value}" \
        "${nginx_delta}" \
        "${local_delta}"
    else
      printf "| %s | %s | %s | %s |\n" \
        "${label}" \
        "${direct_value}" \
        "${nginx_value}" \
        "${nginx_delta}"
    fi
  done
} >"${output_md}"

echo "${output_md}"
