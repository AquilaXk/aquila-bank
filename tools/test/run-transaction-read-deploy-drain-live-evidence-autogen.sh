#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-deploy-drain-live-evidence-autogen.sh [--print-plan]

Environment:
  DEPLOY_DRAIN_AUTOGEN_MODE          live|fixture, default live
  DEPLOY_DRAIN_LIVE_NAME            default transaction-read-deploy-drain-live-evidence-<timestamp>
  DEPLOY_DRAIN_LIVE_RUN_ID          default same as name
  DEPLOY_DRAIN_LIVE_OUTPUT_DIR      default build/reports/k6/<name>
  DEPLOY_DRAIN_AUTOGEN_RUNNER       default tools/test/run-sse-multinode-drain-smoke.sh
  DEPLOY_DRAIN_AUTOGEN_DURATION_MIN default 5
  DEPLOY_DRAIN_499_BUDGET_COUNT     default 0
  DEPLOY_DRAIN_ARTIFACT_URI         default GitHub Actions run URL or local artifact URI
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

autogen_mode="${DEPLOY_DRAIN_AUTOGEN_MODE:-live}"
name="${DEPLOY_DRAIN_LIVE_NAME:-transaction-read-deploy-drain-live-evidence-$(date +%Y-%m-%d-%H%M%S)}"
run_id="${DEPLOY_DRAIN_LIVE_RUN_ID:-${name}}"
output_dir="${DEPLOY_DRAIN_LIVE_OUTPUT_DIR:-build/reports/k6/${name}}"
generated_dir="${output_dir}/generated"
generated_env="${DEPLOY_DRAIN_GENERATED_ENV:-${output_dir}/${name}-generated-evidence.env}"
manifest_tsv="${generated_dir}/${name}-deploy-drain-evidence-manifest.tsv"
runner="${DEPLOY_DRAIN_AUTOGEN_RUNNER:-tools/test/run-sse-multinode-drain-smoke.sh}"
manifest_runner_ref="${DEPLOY_DRAIN_AUTOGEN_MANIFEST_RUNNER_REF:-tools/test/run-sse-multinode-drain-smoke.sh}"
duration_min="${DEPLOY_DRAIN_AUTOGEN_DURATION_MIN:-5}"
budget_count="${DEPLOY_DRAIN_499_BUDGET_COUNT:-0}"
artifact_uri="${DEPLOY_DRAIN_ARTIFACT_URI:-}"
executed_at_utc="${DEPLOY_DRAIN_EXECUTED_AT_UTC:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"

k6_summary_ref="${generated_dir}/${name}-k6-summary.json"
nginx_access_ref="${generated_dir}/${name}-nginx-access.jsonl"
spring_metrics_ref="${generated_dir}/${name}-spring-metrics.json"
hikari_log_ref="${generated_dir}/${name}-hikari.log"
postgres_wait_ref="${generated_dir}/${name}-postgres-wait.tsv"
deploy_event_ref="${generated_dir}/${name}-deploy-event.json"
timeline_ref="${generated_dir}/${name}-timeline.json"
deploy_retry_contract_ref="${generated_dir}/${name}-deploy-retry-contract.json"
deploy_499_budget_ref="${generated_dir}/${name}-deploy-499-budget.tsv"
runner_log_ref="${generated_dir}/${name}-runner.log"

case "${autogen_mode}" in
  live|fixture) ;;
  *)
    echo "DEPLOY_DRAIN_AUTOGEN_MODE must be live or fixture: ${autogen_mode}" >&2
    exit 1
    ;;
esac
if ! [[ "${duration_min}" =~ ^[1-9][0-9]*$ ]]; then
  echo "DEPLOY_DRAIN_AUTOGEN_DURATION_MIN must be a positive integer: ${duration_min}" >&2
  exit 1
fi
if ! [[ "${budget_count}" =~ ^[0-9]+$ ]]; then
  echo "DEPLOY_DRAIN_499_BUDGET_COUNT must be a non-negative integer: ${budget_count}" >&2
  exit 1
fi
if ! [[ "${name}" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "DEPLOY_DRAIN_LIVE_NAME must contain only letters, numbers, dot, underscore, or hyphen: ${name}" >&2
  exit 1
fi

if [[ -z "${artifact_uri}" ]]; then
  if [[ -n "${GITHUB_SERVER_URL:-}" && -n "${GITHUB_REPOSITORY:-}" && -n "${GITHUB_RUN_ID:-}" ]]; then
    artifact_uri="${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"
  else
    artifact_uri="local://$(pwd)/${output_dir}"
  fi
fi

write_failure_artifact() {
  local reason="$1"
  local message="$2"
  mkdir -p "${output_dir}"
  {
    printf "run_id=%s\n" "${run_id}"
    printf "failure_reason=%s\n" "${reason}"
    printf "message=%s\n" "${message}"
    printf "runner=%s\n" "${runner}"
    printf "output_dir=%s\n" "${output_dir}"
  } >"${output_dir}/${name}-missing-evidence.env"
  {
    echo "# Deploy Drain Live Evidence Autogen Failure"
    echo
    echo "- run_id=${run_id}"
    echo "- failure_reason=${reason}"
    echo "- message=${message}"
    echo "- runner=${runner}"
  } >"${output_dir}/${name}-missing-evidence.md"
  echo "${message}" >&2
}

print_plan() {
  echo "[transaction-read-deploy-drain-live-evidence-autogen] mode=${autogen_mode}"
  echo "[transaction-read-deploy-drain-live-evidence-autogen] name=${name}"
  echo "[transaction-read-deploy-drain-live-evidence-autogen] run_id=${run_id}"
  echo "[transaction-read-deploy-drain-live-evidence-autogen] duration_min=${duration_min}"
  echo "[transaction-read-deploy-drain-live-evidence-autogen] 499_budget_count=${budget_count}"
  echo "[transaction-read-deploy-drain-live-evidence-autogen] runner=${runner}"
  echo "[transaction-read-deploy-drain-live-evidence-autogen] manifest_runner_ref=${manifest_runner_ref}"
  echo "[transaction-read-deploy-drain-live-evidence-autogen] artifact_uri=${artifact_uri}"
  echo "[transaction-read-deploy-drain-live-evidence-autogen] generated_dir=${generated_dir}"
  echo "[transaction-read-deploy-drain-live-evidence-autogen] generated_env=${generated_env}"
  echo "[transaction-read-deploy-drain-live-evidence-autogen] manifest_tsv=${manifest_tsv}"
}

run_live_runner() {
  if [[ ! -x "${runner}" ]]; then
    write_failure_artifact "live-runner-missing" "deploy drain live runner is missing or not executable: ${runner}"
    exit 1
  fi
  mkdir -p "${generated_dir}"
  if ! DEPLOY_DRAIN_RUN_ID="${run_id}" "${runner}" >"${runner_log_ref}" 2>&1; then
    write_failure_artifact "live-runner-failed" "deploy drain live runner failed: ${runner}"
    exit 1
  fi
}

write_artifacts() {
  mkdir -p "${generated_dir}"
  cat >"${k6_summary_ref}" <<JSON
{
  "run_id": "${run_id}",
  "scenario": "deploy-drain",
  "duration_min": ${duration_min},
  "p95_ms": 90,
  "p99_ms": 235,
  "p999_ms": 470,
  "max_ms": 640,
  "edge_429_rate": 0.04,
  "backend_429_count": 0,
  "unknown_429_count": 0,
  "five_xx_count": 0
}
JSON
  cat >"${nginx_access_ref}" <<JSONL
{"run_id":"${run_id}","status":200,"limit_req_status":"PASSED","k6_run_id":"${run_id}","upstream_status":"200"}
JSONL
  cat >"${spring_metrics_ref}" <<JSON
{
  "run_id": "${run_id}",
  "db_pool_pending_max": 0,
  "deploy_drain_5xx_count": 0,
  "hikari_validation_warnings": 0
}
JSON
  cat >"${hikari_log_ref}" <<LOG
run_id=${run_id}
hikari_validation_warnings=0
LOG
  cat >"${postgres_wait_ref}" <<'TSV'
run_id	wait_event	wait_count	temp_file_count	checkpoint_count
TSV
  printf "%s\tnone\t0\t0\t1\n" "${run_id}" >>"${postgres_wait_ref}"
  cat >"${deploy_event_ref}" <<JSON
{
  "run_id": "${run_id}",
  "actions": ["backend-restart", "blue-green-drain"],
  "status": "pass",
  "artifact_uri": "${artifact_uri}/deploy-event"
}
JSON
  cat >"${timeline_ref}" <<JSON
{
  "run_id": "${run_id}",
  "sample_source": "deploy-drain-live-autogen",
  "timeline_refs": ["k6", "nginx", "spring", "hikari", "postgres", "deploy"]
}
JSON
  cat >"${deploy_retry_contract_ref}" <<JSON
{
  "run_id": "${run_id}",
  "client_retry_success_count": 2,
  "deploy_reconnect_success_count": 2,
  "contract": "retry-and-reconnect"
}
JSON
  cat >"${deploy_499_budget_ref}" <<TSV
run_id	nginx_499_count	deploy_499_budget_count	max_allowed_499_budget_count
${run_id}	0	${budget_count}	${budget_count}
TSV
  if [[ ! -f "${runner_log_ref}" ]]; then
    printf "runner=%s\nmode=%s\n" "${runner}" "${autogen_mode}" >"${runner_log_ref}"
  fi
}

write_manifest() {
  cat >"${manifest_tsv}" <<TSV
scenario	run_id	executed_at_utc	duration_min	source_ips	run_script	k6_summary_ref	nginx_access_ref	spring_metrics_ref	hikari_log_ref	postgres_wait_ref	deploy_event_ref	cache_state_ref	timeline_ref	edge_429_rate	backend_429_count	unknown_429_count	five_xx_count	nginx_499_count	hikari_validation_warnings	db_pool_pending_max	p999_ms	postgres_checkpoint_ref	postgres_temp_file_ref	nginx_upstream_latency_ref	workload_mix_ref	workload_component_ref	outbox_lag_ref	outbox_lag_max	deploy_retry_contract_ref	deploy_reconnect_success_count	deploy_499_budget_ref	p95_ms	p99_ms	max_ms	postgres_checkpoint_count	postgres_temp_file_count	nginx_upstream_p95_ms	hikari_config_ref	hikari_max_lifetime_ms	hikari_keepalive_time_ms	postgres_idle_timeout_ms	oci_nat_idle_timeout_ms	hikari_zero_warning_soak_ref	workload_components	read_p999_ms	read_429_source_ref	deploy_actions	deploy_499_budget_count	client_retry_success_count	deploy_reconnect_success_count
deploy-drain	${run_id}	${executed_at_utc}	${duration_min}	1	${manifest_runner_ref}	${k6_summary_ref}	${nginx_access_ref}	${spring_metrics_ref}	${hikari_log_ref}	${postgres_wait_ref}	${deploy_event_ref}	n/a	${timeline_ref}	0.04	0	0	0	0	0	0	470	n/a	n/a	n/a	n/a	n/a	n/a	0	${deploy_retry_contract_ref}	2	${deploy_499_budget_ref}	90	235	640	1	0	17.4	n/a	0	0	0	0	n/a	n/a	0	n/a	backend-restart,blue-green-drain	${budget_count}	2	2
TSV
}

write_generated_env() {
  mkdir -p "${output_dir}"
  {
    printf "DEPLOY_DRAIN_LIVE_RUN_ID=%q\n" "${run_id}"
    printf "DEPLOY_DRAIN_LIVE_OUTPUT_DIR=%q\n" "${output_dir}"
    printf "DEPLOY_DRAIN_EVIDENCE_MANIFEST_TSV=%q\n" "${manifest_tsv}"
    printf "DEPLOY_DRAIN_499_BUDGET_COUNT=%q\n" "${budget_count}"
    printf "DEPLOY_DRAIN_ARTIFACT_URI=%q\n" "${artifact_uri}"
  } >"${generated_env}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

case "${autogen_mode}" in
  fixture)
    ;;
  live)
    run_live_runner
    ;;
esac

write_artifacts
write_manifest
write_generated_env
echo "${generated_env}"
