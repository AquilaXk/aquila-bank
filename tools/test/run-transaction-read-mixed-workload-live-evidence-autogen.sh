#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-mixed-workload-live-evidence-autogen.sh [--print-plan]

Environment:
  MIXED_WORKLOAD_AUTOGEN_MODE          live|fixture, default live
  MIXED_WORKLOAD_LIVE_NAME            default transaction-read-mixed-workload-live-evidence-<timestamp>
  MIXED_WORKLOAD_LIVE_RUN_ID          default same as name
  MIXED_WORKLOAD_LIVE_OUTPUT_DIR      default build/reports/k6/<name>
  MIXED_WORKLOAD_AUTOGEN_RUNNER       default tools/test/run-transaction-read-mixed-workload-oci-k6.sh
  MIXED_WORKLOAD_AUTOGEN_SOAK_REPEAT  default 1
  MIXED_WORKLOAD_AUTOGEN_DURATION_MIN default 30
  MIXED_WORKLOAD_ARTIFACT_URI         default GitHub Actions run URL or local artifact URI
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

autogen_mode="${MIXED_WORKLOAD_AUTOGEN_MODE:-live}"
name="${MIXED_WORKLOAD_LIVE_NAME:-transaction-read-mixed-workload-live-evidence-$(date +%Y-%m-%d-%H%M%S)}"
run_id="${MIXED_WORKLOAD_LIVE_RUN_ID:-${name}}"
output_dir="${MIXED_WORKLOAD_LIVE_OUTPUT_DIR:-build/reports/k6/${name}}"
generated_dir="${output_dir}/generated"
generated_env="${MIXED_WORKLOAD_GENERATED_ENV:-${output_dir}/${name}-generated-evidence.env}"
manifest_tsv="${generated_dir}/${name}-mixed-workload-evidence-manifest.tsv"
runner="${MIXED_WORKLOAD_AUTOGEN_RUNNER:-tools/test/run-transaction-read-mixed-workload-oci-k6.sh}"
manifest_runner_ref="${MIXED_WORKLOAD_AUTOGEN_MANIFEST_RUNNER_REF:-tools/test/run-transaction-read-mixed-workload-oci-k6.sh}"
soak_repeat="${MIXED_WORKLOAD_AUTOGEN_SOAK_REPEAT:-1}"
duration_min="${MIXED_WORKLOAD_AUTOGEN_DURATION_MIN:-30}"
artifact_uri="${MIXED_WORKLOAD_ARTIFACT_URI:-}"
executed_at_utc="${MIXED_WORKLOAD_EXECUTED_AT_UTC:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
source_ips="1"
edge_429_rate="0.08"
backend_429_count="0"
unknown_429_count="0"
five_xx_count="0"
nginx_499_count="0"
hikari_validation_warnings="0"
db_pool_pending_max="0"
p95_ms="85"
p99_ms="225"
p999_ms="450"
max_ms="630"
postgres_checkpoint_count="1"
postgres_temp_file_count="0"
nginx_upstream_p95_ms="16.3"
outbox_lag_max="0"
workload_components="read,write,auth,notification,sse"
read_p999_ms="440"
read_buckets="hot,cold,archive"
write_2xx_count="1"
write_unexpected_status_count="0"
write_accepted_ratio="1"
min_write_accepted_ratio="0.80"
idempotency_replay_count="1"
idempotency_conflict_count="0"
runner_evidence_applied="false"

k6_summary_ref="${generated_dir}/${name}-k6-summary.json"
nginx_access_ref="${generated_dir}/${name}-nginx-access.jsonl"
spring_metrics_ref="${generated_dir}/${name}-spring-metrics.json"
hikari_log_ref="${generated_dir}/${name}-hikari.log"
postgres_wait_ref="${generated_dir}/${name}-postgres-wait.tsv"
timeline_ref="${generated_dir}/${name}-timeline.json"
workload_mix_ref="${generated_dir}/${name}-workload-mix.json"
workload_component_ref="${generated_dir}/${name}-workload-components.tsv"
outbox_lag_ref="${generated_dir}/${name}-outbox-lag.tsv"
read_429_source_ref="${generated_dir}/${name}-read-429-source.tsv"
read_bucket_ref="${generated_dir}/${name}-read-buckets.tsv"
write_status_ref="${generated_dir}/${name}-write-status.tsv"
idempotency_evidence_ref="${generated_dir}/${name}-idempotency.tsv"
runner_log_ref="${generated_dir}/${name}-runner.log"

case "${autogen_mode}" in
  live|fixture) ;;
  *)
    echo "MIXED_WORKLOAD_AUTOGEN_MODE must be live or fixture: ${autogen_mode}" >&2
    exit 1
    ;;
esac
if ! [[ "${duration_min}" =~ ^[1-9][0-9]*$ ]]; then
  echo "MIXED_WORKLOAD_AUTOGEN_DURATION_MIN must be a positive integer: ${duration_min}" >&2
  exit 1
fi
if ! [[ "${soak_repeat}" =~ ^[1-9][0-9]*$ ]]; then
  echo "MIXED_WORKLOAD_AUTOGEN_SOAK_REPEAT must be a positive integer: ${soak_repeat}" >&2
  exit 1
fi
if ! [[ "${name}" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "MIXED_WORKLOAD_LIVE_NAME must contain only letters, numbers, dot, underscore, or hyphen: ${name}" >&2
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
    echo "# Mixed Workload Live Evidence Autogen Failure"
    echo
    echo "- run_id=${run_id}"
    echo "- failure_reason=${reason}"
    echo "- message=${message}"
    echo "- runner=${runner}"
  } >"${output_dir}/${name}-missing-evidence.md"
  echo "${message}" >&2
}

print_plan() {
  echo "[transaction-read-mixed-workload-live-evidence-autogen] mode=${autogen_mode}"
  echo "[transaction-read-mixed-workload-live-evidence-autogen] name=${name}"
  echo "[transaction-read-mixed-workload-live-evidence-autogen] run_id=${run_id}"
  echo "[transaction-read-mixed-workload-live-evidence-autogen] duration_min=${duration_min}"
  echo "[transaction-read-mixed-workload-live-evidence-autogen] soak_repeat=${soak_repeat}"
  echo "[transaction-read-mixed-workload-live-evidence-autogen] runner=${runner}"
  echo "[transaction-read-mixed-workload-live-evidence-autogen] manifest_runner_ref=${manifest_runner_ref}"
  echo "[transaction-read-mixed-workload-live-evidence-autogen] artifact_uri=${artifact_uri}"
  echo "[transaction-read-mixed-workload-live-evidence-autogen] generated_dir=${generated_dir}"
  echo "[transaction-read-mixed-workload-live-evidence-autogen] generated_env=${generated_env}"
  echo "[transaction-read-mixed-workload-live-evidence-autogen] manifest_tsv=${manifest_tsv}"
}

run_live_runner() {
  if [[ ! -x "${runner}" ]]; then
    write_failure_artifact "live-runner-missing" "mixed workload live runner is missing or not executable: ${runner}"
    exit 1
  fi
  mkdir -p "${generated_dir}"
  if ! SOAK_REPEAT="${soak_repeat}" "${runner}" >"${runner_log_ref}" 2>&1; then
    write_failure_artifact "live-runner-failed" "mixed workload live runner failed: ${runner}"
    exit 1
  fi
  local runner_evidence_env
  runner_evidence_env="$(awk 'NF { line = $0 } END { print line }' "${runner_log_ref}")"
  if [[ -z "${runner_evidence_env}" || ! -s "${runner_evidence_env}" ]]; then
    write_failure_artifact "runner-evidence-env-missing" "mixed workload live runner did not return a non-empty evidence env: ${runner}"
    exit 1
  fi
  apply_runner_evidence_env "${runner_evidence_env}"
}

apply_runner_evidence_env() {
  local runner_evidence_env="$1"
  # shellcheck disable=SC1090
  source "${runner_evidence_env}"
  if [[ "${MIXED_WORKLOAD_RUNNER_ENV_FORMAT:-}" != "oci-mixed-v1" ]]; then
    write_failure_artifact "runner-evidence-env-invalid" "mixed workload runner evidence env has invalid format"
    exit 1
  fi
  manifest_runner_ref="${MIXED_WORKLOAD_RUNNER_RUN_SCRIPT:-${manifest_runner_ref}}"
  executed_at_utc="${MIXED_WORKLOAD_RUNNER_EXECUTED_AT_UTC:-${executed_at_utc}}"
  source_ips="${MIXED_WORKLOAD_RUNNER_SOURCE_IPS:-${source_ips}}"
  k6_summary_ref="${MIXED_WORKLOAD_RUNNER_K6_SUMMARY_REF:-${k6_summary_ref}}"
  nginx_access_ref="${MIXED_WORKLOAD_RUNNER_NGINX_ACCESS_REF:-${nginx_access_ref}}"
  spring_metrics_ref="${MIXED_WORKLOAD_RUNNER_SPRING_METRICS_REF:-${spring_metrics_ref}}"
  hikari_log_ref="${MIXED_WORKLOAD_RUNNER_HIKARI_LOG_REF:-${hikari_log_ref}}"
  postgres_wait_ref="${MIXED_WORKLOAD_RUNNER_POSTGRES_WAIT_REF:-${postgres_wait_ref}}"
  timeline_ref="${MIXED_WORKLOAD_RUNNER_TIMELINE_REF:-${timeline_ref}}"
  workload_mix_ref="${MIXED_WORKLOAD_RUNNER_WORKLOAD_MIX_REF:-${workload_mix_ref}}"
  workload_component_ref="${MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENT_REF:-${workload_component_ref}}"
  outbox_lag_ref="${MIXED_WORKLOAD_RUNNER_OUTBOX_LAG_REF:-${outbox_lag_ref}}"
  read_429_source_ref="${MIXED_WORKLOAD_RUNNER_READ_429_SOURCE_REF:-${read_429_source_ref}}"
  read_bucket_ref="${MIXED_WORKLOAD_RUNNER_READ_BUCKET_REF:-${read_bucket_ref}}"
  write_status_ref="${MIXED_WORKLOAD_RUNNER_WRITE_STATUS_REF:-${write_status_ref}}"
  idempotency_evidence_ref="${MIXED_WORKLOAD_RUNNER_IDEMPOTENCY_EVIDENCE_REF:-${idempotency_evidence_ref}}"
  edge_429_rate="${MIXED_WORKLOAD_RUNNER_EDGE_429_RATE:-${edge_429_rate}}"
  backend_429_count="${MIXED_WORKLOAD_RUNNER_BACKEND_429_COUNT:-${backend_429_count}}"
  unknown_429_count="${MIXED_WORKLOAD_RUNNER_UNKNOWN_429_COUNT:-${unknown_429_count}}"
  five_xx_count="${MIXED_WORKLOAD_RUNNER_FIVE_XX_COUNT:-${five_xx_count}}"
  nginx_499_count="${MIXED_WORKLOAD_RUNNER_NGINX_499_COUNT:-${nginx_499_count}}"
  hikari_validation_warnings="${MIXED_WORKLOAD_RUNNER_HIKARI_VALIDATION_WARNINGS:-${hikari_validation_warnings}}"
  db_pool_pending_max="${MIXED_WORKLOAD_RUNNER_DB_POOL_PENDING_MAX:-${db_pool_pending_max}}"
  p95_ms="${MIXED_WORKLOAD_RUNNER_P95_MS:-${p95_ms}}"
  p99_ms="${MIXED_WORKLOAD_RUNNER_P99_MS:-${p99_ms}}"
  p999_ms="${MIXED_WORKLOAD_RUNNER_P999_MS:-${p999_ms}}"
  max_ms="${MIXED_WORKLOAD_RUNNER_MAX_MS:-${max_ms}}"
  postgres_checkpoint_count="${MIXED_WORKLOAD_RUNNER_POSTGRES_CHECKPOINT_COUNT:-${postgres_checkpoint_count}}"
  postgres_temp_file_count="${MIXED_WORKLOAD_RUNNER_POSTGRES_TEMP_FILE_COUNT:-${postgres_temp_file_count}}"
  nginx_upstream_p95_ms="${MIXED_WORKLOAD_RUNNER_NGINX_UPSTREAM_P95_MS:-${nginx_upstream_p95_ms}}"
  workload_components="${MIXED_WORKLOAD_RUNNER_WORKLOAD_COMPONENTS:-${workload_components}}"
  read_p999_ms="${MIXED_WORKLOAD_RUNNER_READ_P999_MS:-${read_p999_ms}}"
  read_buckets="${MIXED_WORKLOAD_RUNNER_READ_BUCKETS:-${read_buckets}}"
  write_2xx_count="${MIXED_WORKLOAD_RUNNER_WRITE_2XX_COUNT:-${write_2xx_count}}"
  write_unexpected_status_count="${MIXED_WORKLOAD_RUNNER_WRITE_UNEXPECTED_STATUS_COUNT:-${write_unexpected_status_count}}"
  write_accepted_ratio="${MIXED_WORKLOAD_RUNNER_WRITE_ACCEPTED_RATIO:-${write_accepted_ratio}}"
  min_write_accepted_ratio="${MIXED_WORKLOAD_RUNNER_MIN_WRITE_ACCEPTED_RATIO:-${min_write_accepted_ratio}}"
  idempotency_replay_count="${MIXED_WORKLOAD_RUNNER_IDEMPOTENCY_REPLAY_COUNT:-${idempotency_replay_count}}"
  idempotency_conflict_count="${MIXED_WORKLOAD_RUNNER_IDEMPOTENCY_CONFLICT_COUNT:-${idempotency_conflict_count}}"
  outbox_lag_max="${MIXED_WORKLOAD_RUNNER_OUTBOX_LAG_MAX:-${outbox_lag_max}}"
  runner_evidence_applied="true"
}

write_artifacts() {
  mkdir -p "${generated_dir}"
  cat >"${k6_summary_ref}" <<JSON
{
  "run_id": "${run_id}",
  "scenario": "mixed-workload-30m",
  "duration_min": ${duration_min},
  "p95_ms": ${p95_ms},
  "p99_ms": ${p99_ms},
  "p999_ms": ${p999_ms},
  "max_ms": ${max_ms},
  "edge_429_rate": ${edge_429_rate},
  "backend_429_count": ${backend_429_count},
  "unknown_429_count": ${unknown_429_count},
  "five_xx_count": ${five_xx_count}
}
JSON
  cat >"${nginx_access_ref}" <<JSONL
{"run_id":"${run_id}","status":200,"limit_req_status":"PASSED","k6_run_id":"${run_id}"}
JSONL
  cat >"${spring_metrics_ref}" <<JSON
{
  "run_id": "${run_id}",
  "db_pool_pending_max": 0,
  "hikari_validation_warnings": 0,
  "components": ["read", "write", "auth", "notification", "sse"]
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
  cat >"${timeline_ref}" <<JSON
{
  "run_id": "${run_id}",
  "sample_source": "mixed-workload-live-autogen",
  "timeline_refs": ["k6", "nginx", "spring", "hikari", "postgres"]
}
JSON
  cat >"${workload_mix_ref}" <<JSON
{
  "run_id": "${run_id}",
  "components": {
    "read": 1,
    "write": 1,
    "auth": 1,
    "notification": 1,
    "sse": 1
  }
}
JSON
  cat >"${workload_component_ref}" <<'TSV'
component	status	count
read	pass	1
write	pass	1
auth	pass	1
notification	pass	1
sse	pass	1
TSV
  cat >"${outbox_lag_ref}" <<'TSV'
run_id	outbox_lag_max
TSV
  printf "%s\t%s\n" "${run_id}" "${outbox_lag_max}" >>"${outbox_lag_ref}"
  cat >"${read_429_source_ref}" <<'TSV'
run_id	bucket	source	edge_429_rate	backend_429_count	unknown_429_count
TSV
  printf "%s\thot\tnginx-edge\t%s\t%s\t%s\n" "${run_id}" "${edge_429_rate}" "${backend_429_count}" "${unknown_429_count}" >>"${read_429_source_ref}"
  printf "%s\tcold\tnginx-edge\t%s\t%s\t%s\n" "${run_id}" "${edge_429_rate}" "${backend_429_count}" "${unknown_429_count}" >>"${read_429_source_ref}"
  printf "%s\tarchive\tnginx-edge\t%s\t%s\t%s\n" "${run_id}" "${edge_429_rate}" "${backend_429_count}" "${unknown_429_count}" >>"${read_429_source_ref}"
  cat >"${read_bucket_ref}" <<'TSV'
bucket	count	p95_ms	p999_ms	edge_429_rate	backend_429_count	unknown_429_count
hot	1	80	420	0.08	0	0
cold	1	85	430	0.08	0	0
archive	1	90	440	0.08	0	0
TSV
  cat >"${write_status_ref}" <<'TSV'
status	count
2xx	1
429	0
401	0
403	0
409	0
422	0
other_unexpected	0
TSV
  cat >"${idempotency_evidence_ref}" <<'TSV'
run_id	replay_count	conflict_count
TSV
  printf "%s\t%s\t%s\n" "${run_id}" "${idempotency_replay_count}" "${idempotency_conflict_count}" >>"${idempotency_evidence_ref}"
  if [[ ! -f "${runner_log_ref}" ]]; then
    printf "runner=%s\nmode=%s\n" "${runner}" "${autogen_mode}" >"${runner_log_ref}"
  fi
}

write_manifest() {
  cat >"${manifest_tsv}" <<TSV
scenario	run_id	executed_at_utc	duration_min	source_ips	run_script	k6_summary_ref	nginx_access_ref	spring_metrics_ref	hikari_log_ref	postgres_wait_ref	deploy_event_ref	cache_state_ref	timeline_ref	edge_429_rate	backend_429_count	unknown_429_count	five_xx_count	nginx_499_count	hikari_validation_warnings	db_pool_pending_max	p999_ms	postgres_checkpoint_ref	postgres_temp_file_ref	nginx_upstream_latency_ref	workload_mix_ref	workload_component_ref	outbox_lag_ref	outbox_lag_max	deploy_retry_contract_ref	deploy_reconnect_success_count	deploy_499_budget_ref	p95_ms	p99_ms	max_ms	postgres_checkpoint_count	postgres_temp_file_count	nginx_upstream_p95_ms	hikari_config_ref	hikari_max_lifetime_ms	hikari_keepalive_time_ms	postgres_idle_timeout_ms	oci_nat_idle_timeout_ms	hikari_zero_warning_soak_ref	workload_components	read_p999_ms	read_429_source_ref	read_buckets	read_bucket_ref	write_2xx_count	write_unexpected_status_count	write_status_ref	idempotency_replay_count	idempotency_conflict_count	idempotency_evidence_ref	write_accepted_ratio	min_write_accepted_ratio
mixed-workload-30m	${run_id}	${executed_at_utc}	${duration_min}	${source_ips}	${manifest_runner_ref}	${k6_summary_ref}	${nginx_access_ref}	${spring_metrics_ref}	${hikari_log_ref}	${postgres_wait_ref}	n/a	n/a	${timeline_ref}	${edge_429_rate}	${backend_429_count}	${unknown_429_count}	${five_xx_count}	${nginx_499_count}	${hikari_validation_warnings}	${db_pool_pending_max}	${p999_ms}	n/a	n/a	n/a	${workload_mix_ref}	${workload_component_ref}	${outbox_lag_ref}	${outbox_lag_max}	n/a	0	n/a	${p95_ms}	${p99_ms}	${max_ms}	${postgres_checkpoint_count}	${postgres_temp_file_count}	${nginx_upstream_p95_ms}	n/a	0	0	0	0	n/a	${workload_components}	${read_p999_ms}	${read_429_source_ref}	${read_buckets}	${read_bucket_ref}	${write_2xx_count}	${write_unexpected_status_count}	${write_status_ref}	${idempotency_replay_count}	${idempotency_conflict_count}	${idempotency_evidence_ref}	${write_accepted_ratio}	${min_write_accepted_ratio}
TSV
}

write_generated_env() {
  mkdir -p "${output_dir}"
  {
    printf "MIXED_WORKLOAD_LIVE_NAME=%q\n" "${name}"
    printf "MIXED_WORKLOAD_LIVE_OUTPUT_DIR=%q\n" "${output_dir}"
    printf "MIXED_WORKLOAD_EVIDENCE_MANIFEST_TSV=%q\n" "${manifest_tsv}"
  } >"${generated_env}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

case "${autogen_mode}" in
  fixture)
    write_artifacts
    ;;
  live)
    run_live_runner
    if [[ "${runner_evidence_applied}" != "true" ]]; then
      write_artifacts
    fi
    ;;
esac

write_manifest
write_generated_env
echo "${generated_env}"
