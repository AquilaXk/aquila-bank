#!/usr/bin/env bash
set -euo pipefail

collector="tools/ops/collect-oci-a1-direct-observability.sh"

echo "[oci-a1-direct-observability] shell syntax"
bash -n "${collector}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

stub_docker="${temp_dir}/docker-stub.sh"
cat >"${stub_docker}" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

context="default"
if [[ "${1:-}" == "--context" ]]; then
  context="$2"
  shift 2
fi

case "${1:-}" in
  context)
    case "${2:-}" in
      show)
        echo "default"
        ;;
      inspect)
        if [[ "${3:-}" == "bad" ]]; then
          echo "context inspect failed: ${3} /home/github-runner/.docker token=raw-token http://internal.example.test/context" >&2
          exit 42
        fi
        echo "[]"
        ;;
      *)
        echo "unsupported context command: $*" >&2
        exit 2
        ;;
    esac
    ;;
  info)
    if [[ "${context}" == "bad" ]]; then
      echo "docker info failed: ${context}" >&2
      exit 43
    fi
    echo '"29.2.1"'
    ;;
  ps)
    if [[ "${context}" == "empty" ]]; then
      exit 0
    fi
    echo '{"Names":"aquila-bank-backend-a","Image":"backend:latest","Status":"Up 1 hour","State":"running","Ports":"8080/tcp"}'
    echo '{"Names":"aquila-postgres","Image":"postgres:18","Status":"Up 1 hour","State":"running","Ports":"5432/tcp"}'
    ;;
  stats)
    shift
    while [[ "$#" -gt 0 ]]; do
      case "$1" in
        --no-stream|--format)
          shift
          if [[ "${1:-}" != "--no-stream" && "${1:-}" != "--format" ]]; then
            shift || true
          fi
          ;;
        aquila-bank-backend-a)
          printf "aquila-bank-backend-a\t2.30%%\t120MiB / 512MiB\t5.50%%\t23\n"
          shift
          ;;
        aquila-postgres)
          printf "aquila-postgres\t3.10%%\t240MiB / 2GiB\t11.70%%\t31\n"
          shift
          ;;
        *)
          shift
          ;;
      esac
    done
    ;;
  logs)
    shift
    if [[ "${1:-}" == --tail=* ]]; then
      shift
    fi
    container="${1:-unknown}"
    echo "container=${container} password=super-secret token=abc123 Authorization: Bearer raw-token"
    echo "jdbc:postgresql://user:raw-pass@db:5432/aquila"
    ;;
  *)
    echo "unsupported docker command: $*" >&2
    exit 2
    ;;
esac
SH
chmod +x "${stub_docker}"

echo "[oci-a1-direct-observability] print plan"
plan="$(
  OCI_A1_OBSERVABILITY_DOCKER_BIN="${stub_docker}" \
  OCI_A1_OBSERVABILITY_NAME=direct-observability-check \
  OCI_A1_OBSERVABILITY_DOCKER_CONTEXTS=default,bad \
  OCI_A1_OBSERVABILITY_OUTPUT_DIR="${temp_dir}/plan" \
    bash "${collector}" --print-plan
)"
grep -F "name=direct-observability-check" <<<"${plan}" >/dev/null
grep -F "docker_contexts=default,bad" <<<"${plan}" >/dev/null
grep -F "timeout_seconds=8" <<<"${plan}" >/dev/null
grep -F "container_name_regex=aquila|nginx|postgres|backend|frontend" <<<"${plan}" >/dev/null
grep -F "context_status_tsv=${temp_dir}/plan/direct-observability-check-context-status.tsv" <<<"${plan}" >/dev/null
grep -F "sanitized_logs_dir=${temp_dir}/plan/logs" <<<"${plan}" >/dev/null

echo "[oci-a1-direct-observability] partial context failure still writes artifacts"
output="$(
  OCI_A1_OBSERVABILITY_DOCKER_BIN="${stub_docker}" \
  OCI_A1_OBSERVABILITY_NAME=direct-observability-check \
  OCI_A1_OBSERVABILITY_DOCKER_CONTEXTS=default,bad \
  OCI_A1_OBSERVABILITY_OUTPUT_DIR="${temp_dir}/output" \
    bash "${collector}"
)"
context_status_tsv="$(grep -F "context_status_tsv=" <<<"${output}" | cut -d= -f2-)"
containers_tsv="$(grep -F "containers_tsv=" <<<"${output}" | cut -d= -f2-)"
stats_tsv="$(grep -F "stats_tsv=" <<<"${output}" | cut -d= -f2-)"
summary_json="$(grep -F "summary_json=" <<<"${output}" | cut -d= -f2-)"
report_md="$(grep -F "report_md=" <<<"${output}" | cut -d= -f2-)"

test -s "${context_status_tsv}"
test -s "${containers_tsv}"
test -s "${stats_tsv}"
test -s "${summary_json}"
test -s "${report_md}"
grep -F $'context\tcommand\tstatus\texit_code\treason\tartifact_ref' "${context_status_tsv}" >/dev/null
grep -F $'default\tinfo\tpass\t0\tok\t' "${context_status_tsv}" >/dev/null
grep -F $'bad\tcontext-inspect\tfail\t42\tcontext inspect failed' "${context_status_tsv}" >/dev/null
grep -F $'context\tname\timage\tstatus\tstate\tports' "${containers_tsv}" >/dev/null
grep -F $'default\taquila-bank-backend-a\tbackend:latest\tUp 1 hour\trunning\t8080/tcp' "${containers_tsv}" >/dev/null
grep -F $'context\tname\tcpu_percent\tmemory_usage\tmemory_percent\tpids\tstatus' "${stats_tsv}" >/dev/null
grep -F $'default\taquila-postgres\t3.10\t240MiB / 2GiB\t11.70\t31\tpass' "${stats_tsv}" >/dev/null
grep -F "observable_contexts=1" "${report_md}" >/dev/null
jq -e '.observable_contexts == 1 and .failed_contexts == 1 and .contexts[0].name == "default"' "${summary_json}" >/dev/null

log_file="${temp_dir}/output/logs/default-aquila-bank-backend-a.log"
test -s "${log_file}"
grep -F "[REDACTED]" "${log_file}" >/dev/null
if grep -E "super-secret|abc123|raw-token|raw-pass" "${log_file}" >/dev/null; then
  echo "sanitized log still contains secret-like value" >&2
  exit 1
fi
if find "${temp_dir}/output" -type f -name '*.raw.log' | grep -q .; then
  echo "raw log leaked into artifact output directory" >&2
  exit 1
fi
if grep -R -E "super-secret|abc123|raw-token|raw-pass" "${temp_dir}/output" >/dev/null; then
  echo "artifact output still contains secret-like value" >&2
  exit 1
fi

echo "[oci-a1-direct-observability] empty observable context passes without logs"
empty_output="$(
  OCI_A1_OBSERVABILITY_DOCKER_BIN="${stub_docker}" \
  OCI_A1_OBSERVABILITY_NAME=direct-observability-empty \
  OCI_A1_OBSERVABILITY_DOCKER_CONTEXTS=empty \
  OCI_A1_OBSERVABILITY_OUTPUT_DIR="${temp_dir}/empty" \
    bash "${collector}"
)"
empty_report="$(grep -F "report_md=" <<<"${empty_output}" | cut -d= -f2-)"
grep -F "observable_contexts=1" "${empty_report}" >/dev/null
grep -F "no matching containers" "${empty_report}" >/dev/null

echo "[oci-a1-direct-observability] all contexts fail"
if OCI_A1_OBSERVABILITY_DOCKER_BIN="${stub_docker}" \
  OCI_A1_OBSERVABILITY_NAME=direct-observability-fail \
  OCI_A1_OBSERVABILITY_DOCKER_CONTEXTS=bad \
  OCI_A1_OBSERVABILITY_OUTPUT_DIR="${temp_dir}/fail" \
    bash "${collector}" >"${temp_dir}/fail.log" 2>&1; then
  echo "direct observability unexpectedly passed with all contexts failed" >&2
  exit 1
fi
grep -F "no observable Docker contexts" "${temp_dir}/fail.log" >/dev/null
grep -F "observable_contexts=0" "${temp_dir}/fail/direct-observability-fail.md" >/dev/null

echo "[oci-a1-direct-observability] expected context fail policy"
if OCI_A1_OBSERVABILITY_DOCKER_BIN="${stub_docker}" \
  OCI_A1_OBSERVABILITY_NAME=direct-observability-expected \
  OCI_A1_OBSERVABILITY_DOCKER_CONTEXTS=default,bad \
  OCI_A1_OBSERVABILITY_EXPECTED_CONTEXTS=default,bad \
  OCI_A1_OBSERVABILITY_EXPECTED_CONTEXT_POLICY=fail \
  OCI_A1_OBSERVABILITY_OUTPUT_DIR="${temp_dir}/expected" \
    bash "${collector}" >"${temp_dir}/expected.log" 2>&1; then
  echo "direct observability unexpectedly passed with expected context failure" >&2
  exit 1
fi
expected_summary="${temp_dir}/expected/direct-observability-expected-summary.json"
expected_report="${temp_dir}/expected/direct-observability-expected.md"
test -s "${expected_summary}"
test -s "${expected_report}"
jq -e '.summary_status == "fail" and .expected_context_failures == 1 and (.expected_contexts | length == 2)' "${expected_summary}" >/dev/null
grep -F "summary_status=fail" "${expected_report}" >/dev/null
grep -F "expected_context_failures=1" "${expected_report}" >/dev/null
if grep -R -E "/home/github-runner|raw-token|http://internal\\.example\\.test" "${temp_dir}/expected" >/dev/null; then
  echo "expected context artifact contains unsanitized failure reason" >&2
  exit 1
fi
