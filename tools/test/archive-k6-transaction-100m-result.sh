#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/archive-k6-transaction-100m-result.sh <summary-md> [summary-json]

Environment:
  PERFORMANCE_RESULT_NAME   output basename without .md, default summary-md basename

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
output_dir="docs/performance-results"
output_path="${output_dir}/${base_name}.md"
mkdir -p "${output_dir}"

{
  echo "# ${base_name}"
  echo
  echo "## Archive Metadata"
  echo
  echo "- archivedAt: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "- sourceMarkdown: ${summary_md}"
  if [[ -n "${summary_json}" ]]; then
    echo "- sourceJson: ${summary_json}"
  fi
  echo
  cat "${summary_md}"
} >"${output_path}"

echo "${output_path}"
