#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/archive-k6-transaction-100m-result.sh <summary-md> [summary-json]

Environment:
  PERFORMANCE_RESULT_NAME   output basename without .md, default summary-md basename
  PERFORMANCE_RESULT_OUTPUT_DIR default docs/performance-results
  PERFORMANCE_RESULT_ARTIFACT_DIR default build/reports/k6/promoted
  PERFORMANCE_RESULT_PURPOSE default ${K6_RUN_PURPOSE:-smoke}
  PERFORMANCE_RESULT_STATUS  default unknown

Examples:
  tools/test/archive-k6-transaction-100m-result.sh build/reports/k6/transaction-100m-summary.md
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ "$#" -lt 1 || "$#" -gt 2 ]]; then
  usage
  exit 1
fi

summary_md="$1"
summary_json="${2:-}"

if [[ ! -f "${summary_md}" ]]; then
  echo "summary markdown not found: ${summary_md}" >&2
  exit 1
fi
if [[ -n "${summary_json}" && ! -f "${summary_json}" ]]; then
  echo "summary json not found: ${summary_json}" >&2
  exit 1
fi

base_name="${PERFORMANCE_RESULT_NAME:-$(basename "${summary_md}" .md)}"
output_dir="${PERFORMANCE_RESULT_OUTPUT_DIR:-docs/performance-results}"
artifact_root="${PERFORMANCE_RESULT_ARTIFACT_DIR:-build/reports/k6/promoted}"
purpose="${PERFORMANCE_RESULT_PURPOSE:-${K6_RUN_PURPOSE:-smoke}}"
status="${PERFORMANCE_RESULT_STATUS:-unknown}"
output_path="${output_dir}/${base_name}.md"
artifact_dir="${artifact_root%/}/${base_name}"
mkdir -p "${output_dir}"

{
  echo "# ${base_name}"
  echo
  echo "## Archive Metadata"
  echo
  echo "- archivedAt: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "- resultPurpose: ${purpose}"
  echo "- resultStatus: ${status}"
  echo "- reportClass: transaction-100m-${purpose}"
  echo "- sourceMarkdown: ${summary_md}"
  if [[ -n "${summary_json}" ]]; then
    echo "- sourceJson: ${summary_json}"
  fi
  echo
  cat "${summary_md}"
} >"${output_path}"

mkdir -p "${artifact_dir}"
cp "${summary_md}" "${artifact_dir}/summary.md"
if [[ -n "${summary_json}" ]]; then
  cp "${summary_json}" "${artifact_dir}/summary.json"
fi
{
  printf 'RESULT_NAME=%q\n' "${base_name}"
  printf 'RESULT_PURPOSE=%q\n' "${purpose}"
  printf 'RESULT_STATUS=%q\n' "${status}"
  printf 'REPORT_CLASS=%q\n' "transaction-100m-${purpose}"
  printf 'SOURCE_MARKDOWN=%q\n' "${summary_md}"
  if [[ -n "${summary_json}" ]]; then
    printf 'SOURCE_JSON=%q\n' "${summary_json}"
  fi
  printf 'ARCHIVED_AT=%q\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} >"${artifact_dir}/manifest.env"

echo "${output_path}"
