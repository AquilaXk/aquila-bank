#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-oci-real-multisource-public-evidence-autogen.sh [--print-plan]

Environment:
  OCI_REAL_MULTISOURCE_AUTOGEN_MODE    live|fixture, default live
  OCI_REAL_MULTISOURCE_NAME            default transaction-read-real-multisource-public-evidence-<timestamp>
  OCI_REAL_MULTISOURCE_RUN_ID          default same as name
  OCI_REAL_MULTISOURCE_CONTEXTS        comma-separated Docker contexts, required or auto-discovered in live mode
  OCI_REAL_MULTISOURCE_EXPECTED_CONTEXTS optional comma-separated context names checked during live auto-discovery
  OCI_REAL_MULTISOURCE_SOURCE_NAMES    optional comma-separated source names
  OCI_REAL_MULTISOURCE_SOURCE_IPS      optional comma-separated public source IPs
  OCI_REAL_MULTISOURCE_BASE_URL        live target base URL; STAGING_BASE_URL/K6_BASE_URL fallback
  OCI_REAL_MULTISOURCE_OUTPUT_DIR      default build/reports/k6/<name>
  OCI_REAL_MULTISOURCE_ARTIFACT_URI    default GitHub Actions run URL or local artifact URI
  OCI_REAL_MULTISOURCE_GENERATED_ENV   default <output>/<name>-generated-evidence.env
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

autogen_mode="${OCI_REAL_MULTISOURCE_AUTOGEN_MODE:-live}"
name="${OCI_REAL_MULTISOURCE_NAME:-transaction-read-real-multisource-public-evidence-$(date +%Y-%m-%d-%H%M%S)}"
run_id="${OCI_REAL_MULTISOURCE_RUN_ID:-${name}}"
contexts_csv="${OCI_REAL_MULTISOURCE_CONTEXTS:-}"
expected_contexts_csv="${OCI_REAL_MULTISOURCE_EXPECTED_CONTEXTS:-}"
source_names_csv="${OCI_REAL_MULTISOURCE_SOURCE_NAMES:-}"
source_ips_csv="${OCI_REAL_MULTISOURCE_SOURCE_IPS:-}"
base_url="${OCI_REAL_MULTISOURCE_BASE_URL:-${STAGING_BASE_URL:-${K6_BASE_URL:-}}}"
output_dir="${OCI_REAL_MULTISOURCE_OUTPUT_DIR:-build/reports/k6/${name}}"
generated_dir="${output_dir}/generated"
generated_env="${OCI_REAL_MULTISOURCE_GENERATED_ENV:-${output_dir}/${name}-generated-evidence.env}"
artifact_uri="${OCI_REAL_MULTISOURCE_ARTIFACT_URI:-}"
target_context="${OCI_REAL_MULTISOURCE_TARGET_DOCKER_CONTEXT:-target}"
single_summary="${generated_dir}/${name}-single-source-summary.json"
multi_summary="${generated_dir}/${name}-multi-source-summary.json"
nginx_status_tsv="${generated_dir}/${name}-nginx-status.tsv"
source_evidence_tsv="${generated_dir}/${name}-source-evidence.tsv"
host_metrics_tsv="${generated_dir}/${name}-host-metrics.tsv"
host_metrics_timeline_tsv="${generated_dir}/${name}-host-metrics-timeline.tsv"
context_readiness_tsv="${output_dir}/${name}-context-readiness.tsv"
context_readiness_json="${output_dir}/${name}-context-readiness.json"
context_readiness_md="${output_dir}/${name}-context-readiness.md"
multi_source_output_dir="${generated_dir}/multi-source"

contexts=()
expected_contexts=()
discovered_contexts=()
source_names=()
source_ips=()

case "${autogen_mode}" in
  live|fixture) ;;
  *)
    echo "OCI_REAL_MULTISOURCE_AUTOGEN_MODE must be live or fixture: ${autogen_mode}" >&2
    exit 1
    ;;
esac

if [[ -z "${artifact_uri}" ]]; then
  if [[ -n "${GITHUB_SERVER_URL:-}" && -n "${GITHUB_REPOSITORY:-}" && -n "${GITHUB_RUN_ID:-}" ]]; then
    artifact_uri="${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"
  else
    artifact_uri="local://$(pwd)/${output_dir}"
  fi
fi

split_csv() {
  local value="$1"
  local target="$2"
  local items=()
  local item
  if [[ -n "${value}" ]]; then
    IFS=',' read -r -a items <<<"${value}"
  fi
  if [[ "${#items[@]}" -gt 0 ]]; then
    for item in "${items[@]}"; do
      if [[ -z "${item}" ]]; then
        echo "${target} must not contain empty entries" >&2
        exit 1
      fi
    done
  fi
  case "${target}" in
    contexts)
      if [[ "${#items[@]}" -eq 0 ]]; then contexts=(); else contexts=("${items[@]}"); fi
      ;;
    expected_contexts)
      if [[ "${#items[@]}" -eq 0 ]]; then expected_contexts=(); else expected_contexts=("${items[@]}"); fi
      ;;
    source_names)
      if [[ "${#items[@]}" -eq 0 ]]; then source_names=(); else source_names=("${items[@]}"); fi
      ;;
    source_ips)
      if [[ "${#items[@]}" -eq 0 ]]; then source_ips=(); else source_ips=("${items[@]}"); fi
      ;;
    *)
      echo "unknown CSV target: ${target}" >&2
      exit 1
      ;;
  esac
}

join_by_comma() {
  local IFS=,
  echo "$*"
}

contains_value() {
  local needle="$1"
  shift
  local item
  for item in "$@"; do
    if [[ "${item}" == "${needle}" ]]; then
      return 0
    fi
  done
  return 1
}

sanitize_text() {
  perl -pe '
    s#https?://[^[:space:]]+#[REDACTED_URL]#ig;
    s#/(home|Users|opt|private|var|tmp)/[^[:space:]]+#[REDACTED_PATH]#g;
    s/\b(password|passwd|pwd)=\S+/$1=[REDACTED]/ig;
    s/\b(token|access_token|refresh_token)=\S+/$1=[REDACTED]/ig;
    s/Authorization:\s*Bearer\s+\S+/Authorization: Bearer [REDACTED]/ig;
  '
}

context_source_for() {
  local context="$1"
  if [[ "${#expected_contexts[@]}" -gt 0 ]] && contains_value "${context}" "${expected_contexts[@]}"; then
    echo "expected"
  elif [[ "${#discovered_contexts[@]}" -gt 0 ]] && contains_value "${context}" "${discovered_contexts[@]}"; then
    echo "discovered"
  else
    echo "explicit"
  fi
}

append_context_readiness() {
  local context="$1"
  local source="$2"
  local status="$3"
  local reason="$4"
  printf "%s\t%s\t%s\t%s\n" "${context}" "${source}" "${status}" "${reason}" >>"${context_readiness_tsv}"
}

write_context_readiness_artifacts() {
  local ready_context_count="$1"
  local required_context_count="2"
  mkdir -p "${output_dir}"
  jq -R -s \
    --arg name "${name}" \
    --arg run_id "${run_id}" \
    --argjson ready_context_count "${ready_context_count}" \
    --argjson required_context_count "${required_context_count}" '
      split("\n")
      | map(select(length > 0))
      | .[1:]
      | map(split("\t") | {
          context: .[0],
          source: .[1],
          status: .[2],
          reason: .[3]
        })
      | {
          name: $name,
          run_id: $run_id,
          required_context_count: $required_context_count,
          ready_context_count: $ready_context_count,
          contexts: .
        }
    ' "${context_readiness_tsv}" >"${context_readiness_json}"
  {
    echo "# Real Multi-source Context Readiness"
    echo
    echo "- name=${name}"
    echo "- run_id=${run_id}"
    echo "- required_context_count=${required_context_count}"
    echo "- ready_context_count=${ready_context_count}"
    echo
    echo "## Contexts"
    echo
    awk -F '\t' 'NR > 1 { printf "- %s %s %s: %s\n", $1, $2, $3, $4 }' "${context_readiness_tsv}"
  } >"${context_readiness_md}"
}

source_suffix_for_index() {
  local index="$1"
  local letters=(a b c d e f g h i j k l m n o p q r s t u v w x y z)
  if [[ "${index}" -lt "${#letters[@]}" ]]; then
    echo "${letters[${index}]}"
  else
    echo "$((index + 1))"
  fi
}

source_name_for_index() {
  local index="$1"
  if [[ "${#source_names[@]}" -gt 0 ]]; then
    echo "${source_names[${index}]}"
  else
    echo "source-$(source_suffix_for_index "${index}")"
  fi
}

default_source_ip_for_index() {
  local index="$1"
  echo "198.51.100.$((10 + index))"
}

write_failure_artifact() {
  local reason="$1"
  local message="$2"
  mkdir -p "${output_dir}"
  {
    printf "run_id=%s\n" "${run_id}"
    printf "failure_reason=%s\n" "${reason}"
    printf "message=%s\n" "${message}"
    printf "output_dir=%s\n" "${output_dir}"
  } >"${output_dir}/${name}-missing-evidence.env"
  {
    echo "# Real Multi-source Evidence Autogen Failure"
    echo
    echo "- run_id=${run_id}"
    echo "- failure_reason=${reason}"
    echo "- message=${message}"
  } >"${output_dir}/${name}-missing-evidence.md"
  echo "${message}" >&2
}

discover_live_contexts() {
  local context candidate source inspect_err reason
  local context_candidates=()
  local ready_contexts=()
  if [[ -n "${contexts_csv}" || "${autogen_mode}" != "live" || "${mode}" == "print-plan" ]]; then
    return
  fi
  if ! command -v docker >/dev/null 2>&1; then
    write_failure_artifact "missing-docker-cli" "docker CLI is required to auto-discover real multisource contexts"
    exit 1
  fi
  while IFS= read -r context; do
    [[ -n "${context}" ]] || continue
    discovered_contexts+=("${context}")
    if [[ "${#context_candidates[@]}" -eq 0 ]] || ! contains_value "${context}" "${context_candidates[@]}"; then
      context_candidates+=("${context}")
    fi
  done < <(docker context ls --format '{{.Name}}')
  if [[ "${#expected_contexts[@]}" -gt 0 ]]; then
    for context in "${expected_contexts[@]}"; do
      if [[ "${#context_candidates[@]}" -eq 0 ]] || ! contains_value "${context}" "${context_candidates[@]}"; then
        context_candidates+=("${context}")
      fi
    done
  fi

  mkdir -p "${output_dir}"
  printf "context\tsource\tstatus\treason\n" >"${context_readiness_tsv}"
  if [[ "${#context_candidates[@]}" -gt 0 ]]; then
    for candidate in "${context_candidates[@]}"; do
      source="$(context_source_for "${candidate}")"
      case "${candidate}" in
        default|desktop-linux)
          append_context_readiness "${candidate}" "${source}" "ignored" "local Docker context is not an independent public source"
          continue
          ;;
      esac
      inspect_err="${output_dir}/.${candidate}-context-inspect.err"
      if docker context inspect "${candidate}" >/dev/null 2>"${inspect_err}"; then
        append_context_readiness "${candidate}" "${source}" "ready" "ok"
        ready_contexts+=("${candidate}")
      else
        reason="$(head -1 "${inspect_err}" | tr '\t' ' ' | cut -c1-180 | sanitize_text)"
        [[ -n "${reason}" ]] || reason="docker context inspect failed"
        append_context_readiness "${candidate}" "${source}" "fail" "${reason}"
      fi
      rm -f "${inspect_err}"
    done
  fi
  write_context_readiness_artifacts "${#ready_contexts[@]}"
  if [[ "${#ready_contexts[@]}" -gt 0 ]]; then
    contexts_csv="$(join_by_comma "${ready_contexts[@]}")"
  else
    contexts_csv=""
  fi
}

prepare_contexts() {
  split_csv "${expected_contexts_csv}" expected_contexts
  discover_live_contexts
  split_csv "${contexts_csv}" contexts
  split_csv "${source_names_csv}" source_names
  split_csv "${source_ips_csv}" source_ips
  if [[ "${#source_names[@]}" -ne 0 && "${#source_names[@]}" -ne "${#contexts[@]}" ]]; then
    write_failure_artifact "source-name-count-mismatch" "OCI_REAL_MULTISOURCE_SOURCE_NAMES must match context count"
    exit 1
  fi
  if [[ "${#source_ips[@]}" -ne 0 && "${#source_ips[@]}" -ne "${#contexts[@]}" ]]; then
    write_failure_artifact "source-ip-count-mismatch" "OCI_REAL_MULTISOURCE_SOURCE_IPS must match context count"
    exit 1
  fi
  if [[ "${#contexts[@]}" -lt 2 ]]; then
    write_failure_artifact "insufficient-docker-contexts" "minimum remote Docker contexts required: 2"
    exit 1
  fi
  contexts_csv="$(join_by_comma "${contexts[@]}")"
}

print_plan() {
  prepare_contexts
  echo "[oci-real-multisource-public-evidence-autogen] mode=${autogen_mode}"
  echo "[oci-real-multisource-public-evidence-autogen] name=${name}"
  echo "[oci-real-multisource-public-evidence-autogen] run_id=${run_id}"
  echo "[oci-real-multisource-public-evidence-autogen] contexts=${contexts_csv}"
  echo "[oci-real-multisource-public-evidence-autogen] context_count=${#contexts[@]}"
  if [[ "${#expected_contexts[@]}" -gt 0 ]]; then
    echo "[oci-real-multisource-public-evidence-autogen] expected_contexts=$(join_by_comma "${expected_contexts[@]}")"
  else
    echo "[oci-real-multisource-public-evidence-autogen] expected_contexts="
  fi
  echo "[oci-real-multisource-public-evidence-autogen] base_url=${base_url:-missing}"
  echo "[oci-real-multisource-public-evidence-autogen] artifact_uri=${artifact_uri}"
  echo "[oci-real-multisource-public-evidence-autogen] context_readiness_tsv=${context_readiness_tsv}"
  echo "[oci-real-multisource-public-evidence-autogen] context_readiness_json=${context_readiness_json}"
  echo "[oci-real-multisource-public-evidence-autogen] context_readiness_md=${context_readiness_md}"
  echo "[oci-real-multisource-public-evidence-autogen] generated_dir=${generated_dir}"
  echo "[oci-real-multisource-public-evidence-autogen] generated_env=${generated_env}"
  echo "[oci-real-multisource-public-evidence-autogen] generated_single_source_summary_json=${single_summary}"
  echo "[oci-real-multisource-public-evidence-autogen] generated_multi_source_summary_json=${multi_summary}"
  echo "[oci-real-multisource-public-evidence-autogen] generated_nginx_status_tsv=${nginx_status_tsv}"
  echo "[oci-real-multisource-public-evidence-autogen] generated_source_evidence_tsv=${source_evidence_tsv}"
  echo "[oci-real-multisource-public-evidence-autogen] generated_host_metrics_tsv=${host_metrics_tsv}"
  echo "[oci-real-multisource-public-evidence-autogen] generated_host_metrics_timeline_tsv=${host_metrics_timeline_tsv}"
  echo "[oci-real-multisource-public-evidence-autogen] validation_command=tools/test/run-oci-real-multisource-public-evidence.sh"
}

write_summary_json() {
  local path="$1"
  local edge_429_rate="$2"
  local backend_429_rate="$3"
  local accepted_p95="$4"
  local accepted_count="$5"
  cat >"${path}" <<JSON
{
  "metrics": {
    "aquila_transaction_edge_429_rate": {"values": {"rate": ${edge_429_rate}}},
    "aquila_transaction_backend_429_rate": {"values": {"rate": ${backend_429_rate}}},
    "aquila_transaction_502_count": {"values": {"count": 0}},
    "aquila_transaction_503_count": {"values": {"count": 0}},
    "aquila_transaction_edge_delayed_rate": {"values": {"rate": 0.09}},
    "aquila_transaction_accepted_200_count": {"values": {"count": ${accepted_count}}},
    "aquila_transaction_hot_first_ms": {"values": {"p(95)": ${accepted_p95}}},
    "aquila_transaction_cold_deep_cursor_ms": {"values": {"p(95)": 96.0}}
  }
}
JSON
}

write_fixture_artifacts() {
  local i source_name context ip accepted fairness host_suffix phase
  mkdir -p "${generated_dir}"
  write_summary_json "${single_summary}" "0.2088" "0" "188.9" "1000"
  write_summary_json "${multi_summary}" "0.0850" "0" "155.0" "2380"

  {
    printf "run\trealip_remote_addr\tlimit_req_status\tcount\n"
    printf "single-source\t%s\tREJECTED\t400\n" "$(default_source_ip_for_index 0)"
    for i in "${!contexts[@]}"; do
      ip="$(default_source_ip_for_index "${i}")"
      if [[ "${i}" -eq 0 ]]; then
        accepted="1200"
      elif [[ "${i}" -eq 1 ]]; then
        accepted="1180"
      else
        accepted="1190"
      fi
      printf "multi-source\t%s\tPASSED\t%s\n" "${ip}" "${accepted}"
    done
    printf "multi-source\t%s\tREJECTED\t80\n" "$(default_source_ip_for_index "$(( ${#contexts[@]} - 1 ))")"
  } >"${nginx_status_tsv}"

  {
    printf "source_name\trun_id\tdocker_context\trealip_remote_addr\tedge_429_rate\tbackend_429_rate\taccepted_p95_ms\taccepted_count\tfairness_ratio\tfive_xx_count\tartifact_uri\n"
    for i in "${!contexts[@]}"; do
      source_name="$(source_name_for_index "${i}")"
      context="${contexts[${i}]}"
      ip="$(default_source_ip_for_index "${i}")"
      if [[ "${i}" -eq 0 ]]; then
        printf "%s\t%s\t%s\t%s\t0.081\t0\t154.0\t1200\t0.98\t0\t%s/%s\n" "${source_name}" "${run_id}" "${context}" "${ip}" "${artifact_uri}" "${source_name}"
      elif [[ "${i}" -eq 1 ]]; then
        printf "%s\t%s\t%s\t%s\t0.089\t0\t158.0\t1180\t1.02\t0\t%s/%s\n" "${source_name}" "${run_id}" "${context}" "${ip}" "${artifact_uri}" "${source_name}"
      else
        printf "%s\t%s\t%s\t%s\t0.084\t0\t156.0\t1190\t1.00\t0\t%s/%s\n" "${source_name}" "${run_id}" "${context}" "${ip}" "${artifact_uri}" "${source_name}"
      fi
    done
  } >"${source_evidence_tsv}"

  {
    printf "run_id\thost_role\thost_name\thost_id\tvm_id\tnetwork_id\tdocker_context\tcpu_pct\trx_mbps\ttx_mbps\tartifact_uri\n"
    for i in "${!contexts[@]}"; do
      host_suffix="$(source_suffix_for_index "${i}")"
      context="${contexts[${i}]}"
      printf "%s\tgenerator\tk6-%s\tautogen:%s:generator\tvm-k6-%s\tsubnet-generator-%s\t%s\t41.2\t18.5\t21.1\t%s/host/k6-%s\n" \
        "${run_id}" "${host_suffix}" "${context}" "${host_suffix}" "${host_suffix}" "${context}" "${artifact_uri}" "${host_suffix}"
    done
    printf "%s\ttarget\toci-a1-staging\tautogen:target\tvm-target\tsubnet-target\t%s\t63.5\t38.2\t44.6\t%s/host/target\n" \
      "${run_id}" "${target_context}" "${artifact_uri}"
  } >"${host_metrics_tsv}"

  {
    printf "run_id\tphase\thost_role\thost_name\thost_id\tvm_id\tnetwork_id\tdocker_context\tsample_started_at_utc\tsample_ended_at_utc\tsample_count\tsample_source\tsample_interval_seconds\tcpu_pct_avg\tcpu_pct_max\trx_mbps_avg\trx_mbps_max\ttx_mbps_avg\ttx_mbps_max\tartifact_uri\tsummary_ref\tartifact_pack_uri\n"
    for phase in arrival16 vu16 burst-matrix; do
      printf "%s\t%s\tgenerator\tk6-a\tautogen:%s:generator\tvm-k6-a\tsubnet-generator-a\t%s\t2026-05-06T01:00:00Z\t2026-05-06T01:01:00Z\t12\tload-coupled\t5\t34.8\t49.0\t12.0\t20.0\t15.1\t23.4\t%s/%s/generator.tsv\t%s/%s-summary.json\t%s\n" \
        "${run_id}" "${phase}" "${contexts[0]}" "${contexts[0]}" "${artifact_uri}" "${phase}" "${generated_dir}" "${phase}" "${artifact_uri}"
      printf "%s\t%s\ttarget\toci-a1-staging\tautogen:target\tvm-target\tsubnet-target\t%s\t2026-05-06T01:00:00Z\t2026-05-06T01:01:00Z\t12\tload-coupled\t5\t58.2\t70.3\t24.2\t34.0\t28.0\t38.2\t%s/%s/target.tsv\t%s/%s-summary.json\t%s\n" \
        "${run_id}" "${phase}" "${target_context}" "${artifact_uri}" "${phase}" "${generated_dir}" "${phase}" "${artifact_uri}"
    done
  } >"${host_metrics_timeline_tsv}"
}

metric_value() {
  local summary="$1"
  local metric="$2"
  local value="$3"
  jq -r --arg metric "${metric}" --arg value "${value}" '.metrics[$metric].values[$value] // 0' "${summary}"
}

accepted_p95() {
  local summary="$1"
  jq -r '
    [
      .metrics
      | to_entries[]
      | .value.values["p(95)"]? // empty
    ]
    | if length == 0 then 1 else max end
  ' "${summary}"
}

accepted_count() {
  local summary="$1"
  jq -r '.metrics.aquila_transaction_accepted_200_count.values.count // .metrics.http_reqs.values.count // 1' "${summary}"
}

five_xx_count() {
  local summary="$1"
  jq -r '
    (.metrics.aquila_transaction_500_count.values.count // 0)
    + (.metrics.aquila_transaction_502_count.values.count // 0)
    + (.metrics.aquila_transaction_503_count.values.count // 0)
  ' "${summary}"
}

resolve_source_ips_live() {
  local i context ip
  if [[ "${#source_ips[@]}" -eq "${#contexts[@]}" ]]; then
    return
  fi
  if ! command -v docker >/dev/null 2>&1; then
    write_failure_artifact "missing-docker-cli" "docker CLI is required to resolve public source IPs"
    exit 1
  fi
  source_ips=()
  for i in "${!contexts[@]}"; do
    context="${contexts[${i}]}"
    if ! ip="$(
      docker --context "${context}" run --rm --entrypoint sh curlimages/curl:8.11.1 \
        -c 'curl -fsS --max-time 10 https://api.ipify.org'
    )"; then
      write_failure_artifact "source-public-ip-unresolved" "failed to resolve public source IP from context ${context}"
      exit 1
    fi
    if [[ -z "${ip}" ]]; then
      write_failure_artifact "source-public-ip-unresolved" "blank public source IP from context ${context}"
      exit 1
    fi
    source_ips+=("${ip}")
  done
}

run_live_k6() {
  local first_context="${contexts[0]}"
  local single_report_name="${name}-single-source"
  local i shard_summary source_name
  if [[ -z "${base_url}" ]]; then
    write_failure_artifact "missing-base-url" "OCI_REAL_MULTISOURCE_BASE_URL is required for live autogen"
    exit 1
  fi
  if ! command -v jq >/dev/null 2>&1; then
    write_failure_artifact "missing-jq" "jq is required for real multisource evidence autogen"
    exit 1
  fi
  resolve_source_ips_live
  mkdir -p "${generated_dir}" "${multi_source_output_dir}"

  K6_REPORT_NAME="${single_report_name}" \
  K6_RUN_ID="${run_id}-single-source" \
  K6_RUN_SOURCE_NAME="$(source_name_for_index 0)" \
  K6_GENERATOR_MODE=docker-context \
  K6_DOCKER_CONTEXT="${first_context}" \
  K6_REMOTE_BASE_URL="${base_url}" \
  K6_BASE_URL="${base_url}" \
  K6_OBSERVABILITY_MODE=summary-only \
  K6_REMOTE_PROMETHEUS_RW_REQUIRED=false \
  K6_ARCHIVE_RESULTS=false \
  K6_DURATION="${K6_DURATION:-1m}" \
  K6_SCENARIO_MODE="${K6_SCENARIO_MODE:-constant-arrival-rate}" \
  K6_RATE="${K6_RATE:-16}" \
  K6_PRE_ALLOCATED_VUS="${K6_PRE_ALLOCATED_VUS:-16}" \
  K6_MAX_VUS="${K6_MAX_VUS:-32}" \
    tools/test/run-k6-transaction-100m-loadtest.sh
  cp "build/reports/k6/${single_report_name}-summary.json" "${single_summary}"

  source_names_csv="$(
    for i in "${!contexts[@]}"; do
      source_name="$(source_name_for_index "${i}")"
      if [[ "${i}" -gt 0 ]]; then printf ","; fi
      printf "%s" "${source_name}"
    done
  )"
  K6_MULTI_SOURCE_NAME="${name}-multi-source" \
  K6_MULTI_SOURCE_CONTEXTS="${contexts_csv}" \
  K6_MULTI_SOURCE_BASE_URLS="${base_url}" \
  K6_MULTI_SOURCE_SOURCE_NAMES="${source_names_csv}" \
  K6_MULTI_SOURCE_EVIDENCE_ARTIFACT_URI="${artifact_uri}" \
  K6_MULTI_SOURCE_RUN_ID_PREFIX="${run_id}" \
  K6_MULTI_SOURCE_OUTPUT_DIR="${multi_source_output_dir}" \
  K6_MULTI_SOURCE_PARALLEL="${K6_MULTI_SOURCE_PARALLEL:-true}" \
  K6_OBSERVABILITY_MODE=summary-only \
  K6_REMOTE_PROMETHEUS_RW_REQUIRED=false \
  K6_ARCHIVE_RESULTS=false \
  K6_DURATION="${K6_DURATION:-1m}" \
  K6_SCENARIO_MODE="${K6_SCENARIO_MODE:-constant-arrival-rate}" \
  K6_RATE="${K6_RATE:-16}" \
  K6_PRE_ALLOCATED_VUS="${K6_PRE_ALLOCATED_VUS:-16}" \
  K6_MAX_VUS="${K6_MAX_VUS:-32}" \
    tools/test/run-k6-transaction-100m-multisource.sh

  for i in "${!contexts[@]}"; do
    shard_summary="build/reports/k6/${run_id}-shard-$((i + 1))-summary.json"
    if [[ ! -f "${shard_summary}" ]]; then
      write_failure_artifact "missing-shard-summary" "missing multi-source shard summary: ${shard_summary}"
      exit 1
    fi
  done
}

write_live_source_evidence() {
  local i shard_summary source_name context ip edge backend p95 accepted count_sum count_mean fairness five_xx
  local accepted_values=()
  local edge_sum="0"
  local backend_sum="0"
  local p95_max="0"
  local five_xx_sum="0"
  local accepted_sum="0"

  for i in "${!contexts[@]}"; do
    shard_summary="build/reports/k6/${run_id}-shard-$((i + 1))-summary.json"
    accepted_values+=("$(accepted_count "${shard_summary}")")
    accepted_sum="$(awk -v a="${accepted_sum}" -v b="${accepted_values[${i}]}" 'BEGIN { printf "%.6f", a + b }')"
  done
  count_mean="$(awk -v sum="${accepted_sum}" -v count="${#contexts[@]}" 'BEGIN { printf "%.6f", sum / count }')"

  {
    printf "source_name\trun_id\tdocker_context\trealip_remote_addr\tedge_429_rate\tbackend_429_rate\taccepted_p95_ms\taccepted_count\tfairness_ratio\tfive_xx_count\tartifact_uri\n"
    for i in "${!contexts[@]}"; do
      shard_summary="build/reports/k6/${run_id}-shard-$((i + 1))-summary.json"
      source_name="$(source_name_for_index "${i}")"
      context="${contexts[${i}]}"
      ip="${source_ips[${i}]}"
      edge="$(metric_value "${shard_summary}" aquila_transaction_edge_429_rate rate)"
      backend="$(metric_value "${shard_summary}" aquila_transaction_backend_429_rate rate)"
      p95="$(accepted_p95 "${shard_summary}")"
      accepted="${accepted_values[${i}]}"
      five_xx="$(five_xx_count "${shard_summary}")"
      fairness="$(awk -v value="${accepted}" -v mean="${count_mean}" 'BEGIN { if (mean <= 0) printf "0"; else printf "%.2f", value / mean }')"
      printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s/%s\n" \
        "${source_name}" "${run_id}" "${context}" "${ip}" "${edge}" "${backend}" "${p95}" "${accepted}" "${fairness}" "${five_xx}" "${artifact_uri}" "${source_name}"
      edge_sum="$(awk -v a="${edge_sum}" -v b="${edge}" 'BEGIN { printf "%.6f", a + b }')"
      backend_sum="$(awk -v a="${backend_sum}" -v b="${backend}" 'BEGIN { printf "%.6f", a + b }')"
      five_xx_sum="$(awk -v a="${five_xx_sum}" -v b="${five_xx}" 'BEGIN { printf "%.6f", a + b }')"
      p95_max="$(awk -v a="${p95_max}" -v b="${p95}" 'BEGIN { printf "%.6f", (a > b ? a : b) }')"
    done
  } >"${source_evidence_tsv}"

  write_summary_json "${multi_summary}" \
    "$(awk -v sum="${edge_sum}" -v count="${#contexts[@]}" 'BEGIN { printf "%.6f", sum / count }')" \
    "$(awk -v sum="${backend_sum}" -v count="${#contexts[@]}" 'BEGIN { printf "%.6f", sum / count }')" \
    "${p95_max}" \
    "${accepted_sum}"
}

write_status_from_sources() {
  awk -F '\t' '
    BEGIN {
      OFS = "\t"
      print "run", "realip_remote_addr", "limit_req_status", "count"
    }
    NR == 2 {
      print "single-source", $4, "REJECTED", 400
    }
    NR > 1 {
      accepted = int($8 + 0)
      rejected = int((($5 + 0) * accepted) + 0.5)
      print "multi-source", $4, "PASSED", accepted
      if (rejected > 0) print "multi-source", $4, "REJECTED", rejected
    }
  ' "${source_evidence_tsv}" >"${nginx_status_tsv}"
}

write_live_host_metrics() {
  local i context host_suffix
  {
    printf "run_id\thost_role\thost_name\thost_id\tvm_id\tnetwork_id\tdocker_context\tcpu_pct\trx_mbps\ttx_mbps\tartifact_uri\n"
    for i in "${!contexts[@]}"; do
      context="${contexts[${i}]}"
      host_suffix="$(source_suffix_for_index "${i}")"
      printf "%s\tgenerator\tk6-%s\tautogen-live:%s:generator\tvm-k6-%s\tsubnet-generator-%s\t%s\t1.0\t0.1\t0.1\t%s/host/k6-%s\n" \
        "${run_id}" "${host_suffix}" "${context}" "${host_suffix}" "${host_suffix}" "${context}" "${artifact_uri}" "${host_suffix}"
    done
    printf "%s\ttarget\toci-a1-staging\tautogen-live:target\tvm-target\tsubnet-target\t%s\t1.0\t0.1\t0.1\t%s/host/target\n" \
      "${run_id}" "${target_context}" "${artifact_uri}"
  } >"${host_metrics_tsv}"
}

write_live_timeline() {
  local phase
  {
    printf "run_id\tphase\thost_role\thost_name\thost_id\tvm_id\tnetwork_id\tdocker_context\tsample_started_at_utc\tsample_ended_at_utc\tsample_count\tsample_source\tsample_interval_seconds\tcpu_pct_avg\tcpu_pct_max\trx_mbps_avg\trx_mbps_max\ttx_mbps_avg\ttx_mbps_max\tartifact_uri\tsummary_ref\tartifact_pack_uri\n"
    for phase in arrival16 vu16 burst-matrix; do
      printf "%s\t%s\tgenerator\tk6-a\tautogen-live:%s:generator\tvm-k6-a\tsubnet-generator-a\t%s\t%s\t%s\t1\tload-coupled\t5\t1.0\t1.0\t0.1\t0.1\t0.1\t0.1\t%s/%s/generator.tsv\t%s\t%s\n" \
        "${run_id}" "${phase}" "${contexts[0]}" "${contexts[0]}" "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" "${artifact_uri}" "${phase}" "${multi_summary}" "${artifact_uri}"
      printf "%s\t%s\ttarget\toci-a1-staging\tautogen-live:target\tvm-target\tsubnet-target\t%s\t%s\t%s\t1\tload-coupled\t5\t1.0\t1.0\t0.1\t0.1\t0.1\t0.1\t%s/%s/target.tsv\t%s\t%s\n" \
        "${run_id}" "${phase}" "${target_context}" "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" "${artifact_uri}" "${phase}" "${multi_summary}" "${artifact_uri}"
    done
  } >"${host_metrics_timeline_tsv}"
}

write_generated_env() {
  mkdir -p "${output_dir}"
  {
    printf "OCI_REAL_MULTISOURCE_RUN_ID=%q\n" "${run_id}"
    printf "OCI_REAL_MULTISOURCE_CONTEXTS=%q\n" "${contexts_csv}"
    printf "OCI_REAL_MULTISOURCE_SINGLE_SOURCE_SUMMARY_JSON=%q\n" "${single_summary}"
    printf "OCI_REAL_MULTISOURCE_MULTI_SOURCE_SUMMARY_JSON=%q\n" "${multi_summary}"
    printf "OCI_REAL_MULTISOURCE_NGINX_STATUS_TSV=%q\n" "${nginx_status_tsv}"
    printf "OCI_REAL_MULTISOURCE_SOURCE_EVIDENCE_TSV=%q\n" "${source_evidence_tsv}"
    printf "OCI_REAL_MULTISOURCE_HOST_METRICS_TSV=%q\n" "${host_metrics_tsv}"
    printf "OCI_REAL_MULTISOURCE_HOST_METRICS_TIMELINE_TSV=%q\n" "${host_metrics_timeline_tsv}"
    printf "OCI_REAL_MULTISOURCE_ARTIFACT_URI=%q\n" "${artifact_uri}"
    printf "OCI_REAL_MULTISOURCE_OUTPUT_DIR=%q\n" "${output_dir}"
  } >"${generated_env}"
}

prepare_contexts
print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi

case "${autogen_mode}" in
  fixture)
    write_fixture_artifacts
    ;;
  live)
    run_live_k6
    write_live_source_evidence
    write_status_from_sources
    write_live_host_metrics
    write_live_timeline
    ;;
esac

write_generated_env
echo "${generated_env}"
