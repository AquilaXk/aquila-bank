#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-ec2-local-db-resource-snapshot.sh [--print-plan|--dry-run]

Environment:
  EC2_LOCAL_DB_CAPACITY_ENV_FILE optional env file
  EC2_RESOURCE_SNAPSHOT_NAME default <run-id>-resource
  EC2_RESOURCE_OUTPUT_DIR default build/reports/ec2-local-db/<name>
  EC2_RESOURCE_DOCKER_STATS default true
  EC2_RESOURCE_DOCKER_DISCOVERY default true
  EC2_RESOURCE_DOCKER_LABELS default com.aquilabank.service=nginx,com.aquilabank.service=backend,com.aquilabank.service=postgres
  EC2_RESOURCE_DOCKER_NAME_REGEX default ^(aquila-bank-nginx|aquila-bank-backend-[ab]|aquila-postgres)$
  EC2_RESOURCE_CLOUDWATCH_ENABLED default false
  EC2_CAPACITY_AWS_REGION required when CloudWatch enabled
  EC2_CAPACITY_EC2_INSTANCE_ID optional CloudWatch EC2 target
  EC2_CAPACITY_EBS_VOLUME_ID optional CloudWatch EBS target
  EC2_CAPACITY_CLOUDWATCH_START_TIME / END_TIME required when CloudWatch enabled
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan) mode="print-plan" ;;
    --dry-run) mode="dry-run" ;;
    -h|--help) usage; exit 0 ;;
    *) usage; exit 1 ;;
  esac
  shift
done

if [[ -n "${EC2_LOCAL_DB_CAPACITY_ENV_FILE:-}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${EC2_LOCAL_DB_CAPACITY_ENV_FILE}"
  set +a
fi

run_id="${EC2_CAPACITY_RUN_ID:-ec2-local-db-100m-$(date +%Y-%m-%d-%H%M%S)}"
name="${EC2_RESOURCE_SNAPSHOT_NAME:-${run_id}-resource}"
output_dir="${EC2_RESOURCE_OUTPUT_DIR:-build/reports/ec2-local-db/${name}}"
docker_stats="${EC2_RESOURCE_DOCKER_STATS:-true}"
cloudwatch_enabled="${EC2_RESOURCE_CLOUDWATCH_ENABLED:-false}"
docker_discovery="${EC2_RESOURCE_DOCKER_DISCOVERY:-true}"
docker_labels_csv="${EC2_RESOURCE_DOCKER_LABELS:-com.aquilabank.service=nginx,com.aquilabank.service=backend,com.aquilabank.service=postgres}"
docker_name_regex="${EC2_RESOURCE_DOCKER_NAME_REGEX:-^(aquila-bank-nginx|aquila-bank-backend-[ab]|aquila-postgres)$}"
docker_containers_csv="${EC2_RESOURCE_DOCKER_CONTAINERS:-aquila-bank-nginx,aquila-bank-backend-a,aquila-bank-backend-b,aquila-postgres}"
region="${EC2_CAPACITY_AWS_REGION:-${AWS_REGION:-}}"
ec2_instance_id="${EC2_CAPACITY_EC2_INSTANCE_ID:-}"
ebs_volume_id="${EC2_CAPACITY_EBS_VOLUME_ID:-}"
start_time="${EC2_CAPACITY_CLOUDWATCH_START_TIME:-}"
end_time="${EC2_CAPACITY_CLOUDWATCH_END_TIME:-}"
docker_stats_tsv="${output_dir}/${name}-docker-stats.tsv"
manifest_env="${output_dir}/${name}-manifest.env"

require_bool() {
  local key="$1"
  local value="$2"
  if [[ "${value}" != "true" && "${value}" != "false" ]]; then
    echo "${key} must be true or false: ${value}" >&2
    exit 1
  fi
}

require_bool "EC2_RESOURCE_DOCKER_STATS" "${docker_stats}"
require_bool "EC2_RESOURCE_DOCKER_DISCOVERY" "${docker_discovery}"
require_bool "EC2_RESOURCE_CLOUDWATCH_ENABLED" "${cloudwatch_enabled}"
if [[ "${cloudwatch_enabled}" == "true" ]]; then
  if [[ -z "${region}" || -z "${start_time}" || -z "${end_time}" ]]; then
    echo "EC2_CAPACITY_AWS_REGION, EC2_CAPACITY_CLOUDWATCH_START_TIME, and EC2_CAPACITY_CLOUDWATCH_END_TIME are required when CloudWatch is enabled" >&2
    exit 1
  fi
  if [[ -z "${ec2_instance_id}" && -z "${ebs_volume_id}" ]]; then
    echo "EC2_CAPACITY_EC2_INSTANCE_ID or EC2_CAPACITY_EBS_VOLUME_ID is required when CloudWatch is enabled" >&2
    exit 1
  fi
fi

print_plan() {
  echo "[ec2-resource-snapshot] mode=${mode}"
  echo "[ec2-resource-snapshot] run_id=${run_id}"
  echo "[ec2-resource-snapshot] name=${name}"
  echo "[ec2-resource-snapshot] output_dir=${output_dir}"
  echo "[ec2-resource-snapshot] docker_stats=${docker_stats}"
  echo "[ec2-resource-snapshot] docker_discovery=${docker_discovery}"
  echo "[ec2-resource-snapshot] docker_labels=${docker_labels_csv}"
  echo "[ec2-resource-snapshot] docker_name_regex=${docker_name_regex}"
  echo "[ec2-resource-snapshot] docker_fallback_containers=${docker_containers_csv}"
  echo "[ec2-resource-snapshot] cloudwatch_enabled=${cloudwatch_enabled}"
  echo "[ec2-resource-snapshot] region=${region:-missing}"
  echo "[ec2-resource-snapshot] ec2_instance_id=${ec2_instance_id:-missing}"
  echo "[ec2-resource-snapshot] ebs_volume_id=${ebs_volume_id:-missing}"
  echo "[ec2-resource-snapshot] cloudwatch_window=${start_time:-missing}..${end_time:-missing}"
  echo "[ec2-resource-snapshot] docker_stats_tsv=${docker_stats_tsv}"
  echo "[ec2-resource-snapshot] manifest_env=${manifest_env}"
}

write_manifest() {
  mkdir -p "${output_dir}"
  {
    echo "EC2_CAPACITY_RUN_ID=${run_id}"
    echo "EC2_RESOURCE_SNAPSHOT_NAME=${name}"
    echo "EC2_DOCKER_STATS_TSV=${docker_stats_tsv}"
    echo "EC2_DOCKER_DISCOVERY=${docker_discovery}"
    echo "EC2_DOCKER_LABELS=${docker_labels_csv}"
    echo "EC2_DOCKER_NAME_REGEX=${docker_name_regex}"
    echo "EC2_RESOURCE_CLOUDWATCH_ENABLED=${cloudwatch_enabled}"
    echo "EC2_CAPACITY_EC2_INSTANCE_ID=${ec2_instance_id}"
    echo "EC2_CAPACITY_EBS_VOLUME_ID=${ebs_volume_id}"
  } >"${manifest_env}"
}

trim_value() {
  printf '%s' "$1" | xargs
}

append_unique_container() {
  local container="$1"
  local existing
  container="$(trim_value "${container}")"
  [[ -n "${container}" ]] || return 0
  for existing in "${resolved_containers[@]-}"; do
    [[ -n "${existing}" ]] || continue
    if [[ "${existing}" == "${container}" ]]; then
      return 0
    fi
  done
  resolved_containers+=("${container}")
  resolved_count=$((resolved_count + 1))
}

collect_containers_by_label() {
  local label container
  IFS=',' read -r -a labels <<<"${docker_labels_csv}"
  for label in "${labels[@]}"; do
    label="$(trim_value "${label}")"
    [[ -n "${label}" ]] || continue
    while IFS= read -r container; do
      append_unique_container "${container}"
    done < <(docker ps --filter "label=${label}" --format '{{.Names}}' 2>/dev/null || true)
  done
}

collect_containers_by_name() {
  local container
  while IFS= read -r container; do
    append_unique_container "${container}"
  done < <(docker ps --format '{{.Names}}' 2>/dev/null | awk -v regex="${docker_name_regex}" '$0 ~ regex' || true)
}

append_fallback_containers() {
  local container
  IFS=',' read -r -a containers <<<"${docker_containers_csv}"
  for container in "${containers[@]}"; do
    append_unique_container "${container}"
  done
}

resolve_docker_containers() {
  local -a resolved_containers=()
  local resolved_count=0
  # blue-green 전환 중 inactive slot 이름은 제외하고, label 없는 기존 컨테이너는 running name으로 보완한다.
  if [[ "${docker_discovery}" == "true" ]]; then
    collect_containers_by_label
    collect_containers_by_name
  fi
  if [[ "${resolved_count}" -eq 0 ]]; then
    append_fallback_containers
  fi
  if [[ "${resolved_count}" -gt 0 ]]; then
    printf '%s\n' "${resolved_containers[@]}"
  fi
}

write_docker_stats() {
  if [[ "${docker_stats}" != "true" ]]; then
    return 0
  fi
  if ! command -v docker >/dev/null 2>&1; then
    echo "docker is required for EC2 docker stats snapshot" >&2
    exit 1
  fi
  mkdir -p "${output_dir}"
  printf "container\tcpu_percent\tmemory_usage\tmemory_percent\tpids\n" >"${docker_stats_tsv}"
  local container stats
  while IFS= read -r container; do
    [[ -n "${container}" ]] || continue
    if stats="$(docker stats --no-stream --format '{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.PIDs}}' "${container}" 2>/dev/null)"; then
      printf "%s\t%s\n" "${container}" "${stats}" >>"${docker_stats_tsv}"
    else
      printf "%s\tmissing\tmissing\tmissing\tmissing\n" "${container}" >>"${docker_stats_tsv}"
    fi
  done < <(resolve_docker_containers)
}

write_cloudwatch_snapshot() {
  if [[ "${cloudwatch_enabled}" != "true" ]]; then
    return 0
  fi
  CAPACITY_CLOUDWATCH_NAME="${name}" \
  CAPACITY_CLOUDWATCH_OUTPUT_DIR="${output_dir}" \
  CAPACITY_CLOUDWATCH_REGION="${region}" \
  CAPACITY_CLOUDWATCH_START_TIME="${start_time}" \
  CAPACITY_CLOUDWATCH_END_TIME="${end_time}" \
  CAPACITY_CLOUDWATCH_EC2_INSTANCE_ID="${ec2_instance_id}" \
  CAPACITY_CLOUDWATCH_EBS_VOLUME_ID="${ebs_volume_id}" \
    tools/test/run-capacity-cloudwatch-credit-gp3-snapshot.sh >/dev/null
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  echo "[ec2-resource-snapshot] dry-run passed"
  exit 0
fi

write_manifest
write_docker_stats
write_cloudwatch_snapshot
echo "${manifest_env}"
