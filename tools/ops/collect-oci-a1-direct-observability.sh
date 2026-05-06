#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/ops/collect-oci-a1-direct-observability.sh [--print-plan]

Environment:
  OCI_A1_OBSERVABILITY_NAME                  default oci-a1-direct-observability-<timestamp>
  OCI_A1_OBSERVABILITY_OUTPUT_DIR            default build/reports/oci-a1-direct-observability/<name>
  OCI_A1_OBSERVABILITY_DOCKER_CONTEXTS       comma-separated Docker contexts; default current,default,desktop-linux,oci-a1-staging
  OCI_A1_OBSERVABILITY_TIMEOUT_SECONDS       per Docker command timeout, default 8
  OCI_A1_OBSERVABILITY_LOG_TAIL_LINES        docker logs tail lines, default 80
  OCI_A1_OBSERVABILITY_CONTAINER_NAME_REGEX  default aquila|nginx|postgres|backend|frontend
  OCI_A1_OBSERVABILITY_DOCKER_BIN            default docker

Read-only Docker commands used:
  docker context inspect
  docker info
  docker ps
  docker stats --no-stream
  docker logs
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

name="${OCI_A1_OBSERVABILITY_NAME:-oci-a1-direct-observability-$(date +%Y-%m-%d-%H%M%S)}"
output_dir="${OCI_A1_OBSERVABILITY_OUTPUT_DIR:-build/reports/oci-a1-direct-observability/${name}}"
docker_bin="${OCI_A1_OBSERVABILITY_DOCKER_BIN:-docker}"
timeout_seconds="${OCI_A1_OBSERVABILITY_TIMEOUT_SECONDS:-8}"
log_tail_lines="${OCI_A1_OBSERVABILITY_LOG_TAIL_LINES:-80}"
container_name_regex="${OCI_A1_OBSERVABILITY_CONTAINER_NAME_REGEX:-aquila|nginx|postgres|backend|frontend}"
context_csv="${OCI_A1_OBSERVABILITY_DOCKER_CONTEXTS:-}"

context_status_tsv="${output_dir}/${name}-context-status.tsv"
containers_tsv="${output_dir}/${name}-containers.tsv"
stats_tsv="${output_dir}/${name}-stats.tsv"
summary_json="${output_dir}/${name}-summary.json"
report_md="${output_dir}/${name}.md"
sanitized_logs_dir="${output_dir}/logs"
tmp_dir="${output_dir}/tmp"

require_positive_integer() {
  local key="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${key} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

require_positive_integer "OCI_A1_OBSERVABILITY_TIMEOUT_SECONDS" "${timeout_seconds}"
require_positive_integer "OCI_A1_OBSERVABILITY_LOG_TAIL_LINES" "${log_tail_lines}"

if [[ -z "${context_csv}" ]]; then
  current_context="$("${docker_bin}" context show 2>/dev/null || true)"
  context_csv="${current_context:-default},default,desktop-linux,oci-a1-staging"
fi

IFS=',' read -r -a raw_contexts <<<"${context_csv}"
contexts=()
for context in "${raw_contexts[@]}"; do
  context="$(printf '%s' "${context}" | xargs)"
  [[ -n "${context}" ]] || continue
  duplicate=false
  for existing in "${contexts[@]:-}"; do
    if [[ "${existing}" == "${context}" ]]; then
      duplicate=true
      break
    fi
  done
  [[ "${duplicate}" == "true" ]] || contexts+=("${context}")
done
if [[ "${#contexts[@]}" -eq 0 ]]; then
  echo "OCI_A1_OBSERVABILITY_DOCKER_CONTEXTS resolved to no contexts" >&2
  exit 1
fi

join_by_comma() {
  local IFS=,
  echo "$*"
}

safe_name() {
  printf '%s' "$1" | tr -c 'A-Za-z0-9._-' '-'
}

first_error_line() {
  local file="$1"
  if [[ -s "${file}" ]]; then
    head -1 "${file}" | tr '\t' ' ' | cut -c1-180
  else
    echo "ok"
  fi
}

sanitize_log() {
  perl -pe '
    s/\b(password|passwd|pwd)=\S+/$1=[REDACTED]/ig;
    s/\b(token|access_token|refresh_token)=\S+/$1=[REDACTED]/ig;
    s/Authorization:\s*Bearer\s+\S+/Authorization: Bearer [REDACTED]/ig;
    s#(jdbc:postgresql://[^:/@\s]+:)[^@\s]+@#$1[REDACTED]@#ig;
  '
}

run_bounded() {
  local timeout="$1"
  local stdout_file="$2"
  local stderr_file="$3"
  shift 3
  local pid elapsed status

  : >"${stdout_file}"
  : >"${stderr_file}"
  "$@" >"${stdout_file}" 2>"${stderr_file}" &
  pid="$!"
  elapsed=0
  while kill -0 "${pid}" 2>/dev/null; do
    if [[ "${elapsed}" -ge "${timeout}" ]]; then
      kill "${pid}" 2>/dev/null || true
      wait "${pid}" 2>/dev/null || true
      echo "timeout after ${timeout}s: $*" >>"${stderr_file}"
      return 124
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  wait "${pid}" || status="$?"
  return "${status:-0}"
}

run_docker() {
  local context="$1"
  local stdout_file="$2"
  local stderr_file="$3"
  shift 3
  run_bounded "${timeout_seconds}" "${stdout_file}" "${stderr_file}" \
    "${docker_bin}" --context "${context}" "$@"
}

append_status() {
  local context="$1"
  local command_name="$2"
  local status="$3"
  local exit_code="$4"
  local reason="$5"
  local artifact_ref="$6"
  printf "%s\t%s\t%s\t%s\t%s\t%s\n" \
    "${context}" "${command_name}" "${status}" "${exit_code}" "${reason}" "${artifact_ref}" >>"${context_status_tsv}"
}

print_plan() {
  echo "[oci-a1-direct-observability] name=${name}"
  echo "[oci-a1-direct-observability] docker_bin=${docker_bin}"
  echo "[oci-a1-direct-observability] docker_contexts=$(join_by_comma "${contexts[@]}")"
  echo "[oci-a1-direct-observability] timeout_seconds=${timeout_seconds}"
  echo "[oci-a1-direct-observability] log_tail_lines=${log_tail_lines}"
  echo "[oci-a1-direct-observability] container_name_regex=${container_name_regex}"
  echo "[oci-a1-direct-observability] output_dir=${output_dir}"
  echo "[oci-a1-direct-observability] context_status_tsv=${context_status_tsv}"
  echo "[oci-a1-direct-observability] containers_tsv=${containers_tsv}"
  echo "[oci-a1-direct-observability] stats_tsv=${stats_tsv}"
  echo "[oci-a1-direct-observability] summary_json=${summary_json}"
  echo "[oci-a1-direct-observability] report_md=${report_md}"
  echo "[oci-a1-direct-observability] sanitized_logs_dir=${sanitized_logs_dir}"
}

if [[ "${mode}" == "print-plan" ]]; then
  print_plan
  exit 0
fi

echo "[oci-a1-direct-observability] name=${name}"
echo "[oci-a1-direct-observability] docker_contexts=$(join_by_comma "${contexts[@]}")"
echo "[oci-a1-direct-observability] timeout_seconds=${timeout_seconds}"
echo "[oci-a1-direct-observability] output_dir=${output_dir}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

mkdir -p "${output_dir}" "${sanitized_logs_dir}" "${tmp_dir}"
printf "context\tcommand\tstatus\texit_code\treason\tartifact_ref\n" >"${context_status_tsv}"
printf "context\tname\timage\tstatus\tstate\tports\n" >"${containers_tsv}"
printf "context\tname\tcpu_percent\tmemory_usage\tmemory_percent\tpids\tstatus\n" >"${stats_tsv}"
context_jsonl="${tmp_dir}/contexts.jsonl"
: >"${context_jsonl}"

observable_contexts=0
failed_contexts=0
no_container_contexts=0

for context in "${contexts[@]}"; do
  context_safe="$(safe_name "${context}")"
  context_ok=true
  context_reason="ok"
  context_container_count=0
  context_log_count=0

  inspect_out="${tmp_dir}/${context_safe}-context-inspect.out"
  inspect_err="${tmp_dir}/${context_safe}-context-inspect.err"
  if run_bounded "${timeout_seconds}" "${inspect_out}" "${inspect_err}" "${docker_bin}" context inspect "${context}"; then
    append_status "${context}" "context-inspect" "pass" "0" "ok" "${inspect_out}"
  else
    code="$?"
    reason="$(first_error_line "${inspect_err}")"
    append_status "${context}" "context-inspect" "fail" "${code}" "${reason}" "${inspect_err}"
    context_ok=false
    context_reason="${reason}"
    failed_contexts=$((failed_contexts + 1))
    jq -n --arg name "${context}" --arg status "fail" --arg reason "${context_reason}" --argjson container_count 0 \
      '{name: $name, status: $status, reason: $reason, container_count: $container_count}' >>"${context_jsonl}"
    continue
  fi

  info_out="${tmp_dir}/${context_safe}-info.out"
  info_err="${tmp_dir}/${context_safe}-info.err"
  if run_docker "${context}" "${info_out}" "${info_err}" info --format '{{json .ServerVersion}}'; then
    append_status "${context}" "info" "pass" "0" "ok" "${info_out}"
  else
    code="$?"
    reason="$(first_error_line "${info_err}")"
    append_status "${context}" "info" "fail" "${code}" "${reason}" "${info_err}"
    context_ok=false
    context_reason="${reason}"
  fi

  ps_out="${tmp_dir}/${context_safe}-ps.jsonl"
  ps_err="${tmp_dir}/${context_safe}-ps.err"
  if run_docker "${context}" "${ps_out}" "${ps_err}" ps --format '{{json .}}'; then
    append_status "${context}" "ps" "pass" "0" "ok" "${ps_out}"
    container_names=()
    while IFS= read -r line; do
      [[ -n "${line}" ]] || continue
      name_value="$(jq -r '.Names // .Name // empty' <<<"${line}")"
      [[ -n "${name_value}" ]] || continue
      if [[ "${name_value}" =~ ${container_name_regex} ]]; then
        image_value="$(jq -r '.Image // ""' <<<"${line}")"
        status_value="$(jq -r '.Status // ""' <<<"${line}")"
        state_value="$(jq -r '.State // ""' <<<"${line}")"
        ports_value="$(jq -r '.Ports // ""' <<<"${line}" | tr '\t' ' ')"
        printf "%s\t%s\t%s\t%s\t%s\t%s\n" \
          "${context}" "${name_value}" "${image_value}" "${status_value}" "${state_value}" "${ports_value}" >>"${containers_tsv}"
        container_names+=("${name_value}")
      fi
    done <"${ps_out}"
    context_container_count="${#container_names[@]}"
    if [[ "${context_container_count}" -eq 0 ]]; then
      no_container_contexts=$((no_container_contexts + 1))
      append_status "${context}" "container-discovery" "pass" "0" "no matching containers" "${ps_out}"
    else
      stats_out="${tmp_dir}/${context_safe}-stats.tsv"
      stats_err="${tmp_dir}/${context_safe}-stats.err"
      if run_docker "${context}" "${stats_out}" "${stats_err}" stats --no-stream --format '{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.PIDs}}' "${container_names[@]}"; then
        append_status "${context}" "stats" "pass" "0" "ok" "${stats_out}"
        awk -F '\t' -v context="${context}" 'BEGIN { OFS = FS } {
          cpu = $2
          mem_pct = $4
          gsub("%", "", cpu)
          gsub("%", "", mem_pct)
          print context, $1, cpu, $3, mem_pct, $5, "pass"
        }' "${stats_out}" >>"${stats_tsv}"
      else
        code="$?"
        reason="$(first_error_line "${stats_err}")"
        append_status "${context}" "stats" "fail" "${code}" "${reason}" "${stats_err}"
        context_ok=false
        context_reason="${reason}"
      fi

      log_success_count=0
      for container in "${container_names[@]}"; do
        container_safe="$(safe_name "${container}")"
        log_raw="${tmp_dir}/${context_safe}-${container_safe}.raw.log"
        log_err="${tmp_dir}/${context_safe}-${container_safe}.log.err"
        log_sanitized="${sanitized_logs_dir}/${context_safe}-${container_safe}.log"
        if run_docker "${context}" "${log_raw}" "${log_err}" logs --tail="${log_tail_lines}" "${container}"; then
          sanitize_log <"${log_raw}" >"${log_sanitized}"
          append_status "${context}" "logs:${container}" "pass" "0" "ok" "${log_sanitized}"
          log_success_count=$((log_success_count + 1))
        else
          code="$?"
          reason="$(first_error_line "${log_err}")"
          append_status "${context}" "logs:${container}" "fail" "${code}" "${reason}" "${log_err}"
        fi
      done
      context_log_count="${log_success_count}"
      if [[ "${context_container_count}" -gt 0 && "${context_log_count}" -eq 0 ]]; then
        context_ok=false
        context_reason="no container logs collected"
      fi
    fi
  else
    code="$?"
    reason="$(first_error_line "${ps_err}")"
    append_status "${context}" "ps" "fail" "${code}" "${reason}" "${ps_err}"
    context_ok=false
    context_reason="${reason}"
  fi

  if [[ "${context_ok}" == "true" ]]; then
    observable_contexts=$((observable_contexts + 1))
    context_status="pass"
  else
    failed_contexts=$((failed_contexts + 1))
    context_status="fail"
  fi
  jq -n \
    --arg name "${context}" \
    --arg status "${context_status}" \
    --arg reason "${context_reason}" \
    --argjson container_count "${context_container_count}" \
    --argjson log_count "${context_log_count}" \
    '{name: $name, status: $status, reason: $reason, container_count: $container_count, log_count: $log_count}' >>"${context_jsonl}"
done

jq -s \
  --arg name "${name}" \
  --arg generated_at_utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --arg context_status_tsv "${context_status_tsv}" \
  --arg containers_tsv "${containers_tsv}" \
  --arg stats_tsv "${stats_tsv}" \
  --arg logs_dir "${sanitized_logs_dir}" \
  --argjson observable_contexts "${observable_contexts}" \
  --argjson failed_contexts "${failed_contexts}" \
  --argjson no_container_contexts "${no_container_contexts}" \
  '{
    name: $name,
    generated_at_utc: $generated_at_utc,
    observable_contexts: $observable_contexts,
    failed_contexts: $failed_contexts,
    no_container_contexts: $no_container_contexts,
    context_status_tsv: $context_status_tsv,
    containers_tsv: $containers_tsv,
    stats_tsv: $stats_tsv,
    logs_dir: $logs_dir,
    contexts: .
  }' "${context_jsonl}" >"${summary_json}"

{
  echo "# OCI A1 Direct Observability"
  echo
  echo "- name=${name}"
  echo "- observable_contexts=${observable_contexts}"
  echo "- failed_contexts=${failed_contexts}"
  echo "- no_container_contexts=${no_container_contexts}"
  echo "- context_status_tsv=${context_status_tsv}"
  echo "- containers_tsv=${containers_tsv}"
  echo "- stats_tsv=${stats_tsv}"
  echo "- sanitized_logs_dir=${sanitized_logs_dir}"
  echo
  echo "## Context Status"
  echo
  awk -F '\t' 'NR > 1 { printf "- %s %s %s: %s\n", $1, $2, $3, $5 }' "${context_status_tsv}"
} >"${report_md}"

echo "context_status_tsv=${context_status_tsv}"
echo "containers_tsv=${containers_tsv}"
echo "stats_tsv=${stats_tsv}"
echo "summary_json=${summary_json}"
echo "report_md=${report_md}"
echo "sanitized_logs_dir=${sanitized_logs_dir}"

if [[ "${observable_contexts}" -eq 0 ]]; then
  echo "no observable Docker contexts" >&2
  exit 1
fi
