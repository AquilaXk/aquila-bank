#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/archive-admission-guard-telemetry-snapshot.sh <summary-tsv> [raw-tsv]

Environment:
  ADMISSION_TELEMETRY_RESULT_NAME   output basename without .md, default summary-tsv basename

Examples:
  tools/test/archive-admission-guard-telemetry-snapshot.sh \
    build/reports/admission/<name>/http-admission-summary.tsv \
    build/reports/admission/<name>/http-admission-raw.tsv
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

summary_tsv="$1"
raw_tsv="${2:-}"
if [[ ! -f "${summary_tsv}" ]]; then
  echo "admission telemetry summary not found: ${summary_tsv}" >&2
  exit 1
fi
if [[ -n "${raw_tsv}" && ! -f "${raw_tsv}" ]]; then
  echo "admission telemetry raw sample not found: ${raw_tsv}" >&2
  exit 1
fi

base_name="${ADMISSION_TELEMETRY_RESULT_NAME:-$(basename "${summary_tsv}" .tsv)}"
output_dir="docs/performance-results"
output_path="${output_dir}/${base_name}.md"
mkdir -p "${output_dir}"

{
  echo "# ${base_name}"
  echo
  echo "## Admission Guard Telemetry"
  echo
  echo "- archivedAt: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "- sourceSummary: ${summary_tsv}"
  if [[ -n "${raw_tsv}" ]]; then
    echo "- sourceRaw: ${raw_tsv}"
  fi
  echo "- secretPolicy: token, Authorization header, 운영 URL은 기록하지 않음"
  echo
  echo "## Summary"
  echo
  echo '```tsv'
  cat "${summary_tsv}"
  echo '```'
  if [[ -n "${raw_tsv}" ]]; then
    echo
    echo "## Raw Sample"
    echo
    echo '```tsv'
    head -50 "${raw_tsv}"
    echo '```'
  fi
} >"${output_path}"

echo "${output_path}"
