#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-capacity-cloudwatch-credit-gp3-snapshot.sh [--print-plan|--dry-run]

Environment:
  CAPACITY_CLOUDWATCH_NAME default capacity-cloudwatch-<timestamp>
  CAPACITY_CLOUDWATCH_OUTPUT_DIR default build/reports/cloudwatch/<name>
  CAPACITY_CLOUDWATCH_REGION default AWS_REGION
  CAPACITY_CLOUDWATCH_START_TIME required ISO-8601 UTC
  CAPACITY_CLOUDWATCH_END_TIME required ISO-8601 UTC
  CAPACITY_CLOUDWATCH_PERIOD_SECONDS default 60
  CAPACITY_CLOUDWATCH_EC2_INSTANCE_ID optional
  CAPACITY_CLOUDWATCH_RDS_IDENTIFIER optional
  CAPACITY_CLOUDWATCH_EBS_VOLUME_ID optional gp3 volume id

Examples:
  CAPACITY_CLOUDWATCH_START_TIME=2026-04-28T00:00:00Z \
  CAPACITY_CLOUDWATCH_END_TIME=2026-04-28T00:30:00Z \
    tools/test/run-capacity-cloudwatch-credit-gp3-snapshot.sh
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    --dry-run)
      mode="dry-run"
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

name="${CAPACITY_CLOUDWATCH_NAME:-capacity-cloudwatch-$(date +%Y-%m-%d-%H%M%S)}"
output_dir="${CAPACITY_CLOUDWATCH_OUTPUT_DIR:-build/reports/cloudwatch/${name}}"
region="${CAPACITY_CLOUDWATCH_REGION:-${AWS_REGION:-}}"
start_time="${CAPACITY_CLOUDWATCH_START_TIME:-}"
end_time="${CAPACITY_CLOUDWATCH_END_TIME:-}"
period_seconds="${CAPACITY_CLOUDWATCH_PERIOD_SECONDS:-60}"
ec2_instance_id="${CAPACITY_CLOUDWATCH_EC2_INSTANCE_ID:-}"
rds_identifier="${CAPACITY_CLOUDWATCH_RDS_IDENTIFIER:-}"
ebs_volume_id="${CAPACITY_CLOUDWATCH_EBS_VOLUME_ID:-}"
snapshot_tsv="${output_dir}/${name}-cloudwatch-capacity-snapshot.tsv"

require_positive_integer() {
  local name="$1"
  local value="$2"
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "${name} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

require_time() {
  local name="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    echo "${name} is required" >&2
    exit 1
  fi
  if ! [[ "${value}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]]; then
    echo "${name} must be UTC ISO-8601 such as 2026-04-28T00:00:00Z: ${value}" >&2
    exit 1
  fi
}

require_positive_integer "CAPACITY_CLOUDWATCH_PERIOD_SECONDS" "${period_seconds}"
require_time "CAPACITY_CLOUDWATCH_START_TIME" "${start_time}"
require_time "CAPACITY_CLOUDWATCH_END_TIME" "${end_time}"

print_plan() {
  echo "[capacity-cloudwatch] mode=${mode}"
  echo "[capacity-cloudwatch] name=${name}"
  echo "[capacity-cloudwatch] region=${region:-missing}"
  echo "[capacity-cloudwatch] start_time=${start_time}"
  echo "[capacity-cloudwatch] end_time=${end_time}"
  echo "[capacity-cloudwatch] period_seconds=${period_seconds}"
  echo "[capacity-cloudwatch] ec2_instance_id=${ec2_instance_id:-missing}"
  echo "[capacity-cloudwatch] rds_identifier=${rds_identifier:-missing}"
  echo "[capacity-cloudwatch] ebs_volume_id=${ebs_volume_id:-missing}"
  echo "[capacity-cloudwatch] snapshot_tsv=${snapshot_tsv}"
}

emit_metric_plan() {
  if [[ -n "${ec2_instance_id}" ]]; then
    printf "AWS/EC2\tCPUCreditBalance\tInstanceId\t%s\tAverage,Maximum\n" "${ec2_instance_id}"
    printf "AWS/EC2\tCPUCreditUsage\tInstanceId\t%s\tAverage,Maximum\n" "${ec2_instance_id}"
    printf "AWS/EC2\tCPUUtilization\tInstanceId\t%s\tAverage,Maximum\n" "${ec2_instance_id}"
  fi
  if [[ -n "${rds_identifier}" ]]; then
    printf "AWS/RDS\tCPUCreditBalance\tDBInstanceIdentifier\t%s\tAverage,Maximum\n" "${rds_identifier}"
    printf "AWS/RDS\tFreeableMemory\tDBInstanceIdentifier\t%s\tAverage,Minimum\n" "${rds_identifier}"
    printf "AWS/RDS\tReadIOPS\tDBInstanceIdentifier\t%s\tAverage,Maximum\n" "${rds_identifier}"
    printf "AWS/RDS\tWriteIOPS\tDBInstanceIdentifier\t%s\tAverage,Maximum\n" "${rds_identifier}"
    printf "AWS/RDS\tDiskQueueDepth\tDBInstanceIdentifier\t%s\tAverage,Maximum\n" "${rds_identifier}"
  fi
  if [[ -n "${ebs_volume_id}" ]]; then
    printf "AWS/EBS\tVolumeReadOps\tVolumeId\t%s\tSum,Maximum\n" "${ebs_volume_id}"
    printf "AWS/EBS\tVolumeWriteOps\tVolumeId\t%s\tSum,Maximum\n" "${ebs_volume_id}"
    printf "AWS/EBS\tVolumeQueueLength\tVolumeId\t%s\tAverage,Maximum\n" "${ebs_volume_id}"
  fi
}

cloudwatch_metric_value() {
  local namespace="$1"
  local metric="$2"
  local dimension_name="$3"
  local dimension_value="$4"
  local statistic="$5"
  aws cloudwatch get-metric-statistics \
    --region "${region}" \
    --namespace "${namespace}" \
    --metric-name "${metric}" \
    --dimensions "Name=${dimension_name},Value=${dimension_value}" \
    --statistics "${statistic}" \
    --start-time "${start_time}" \
    --end-time "${end_time}" \
    --period "${period_seconds}" \
    | jq -r --arg statistic "${statistic}" '
        [.Datapoints[]?[$statistic] // empty]
        | if length == 0 then "n/a" else (add / length) end
      '
}

write_metric_rows() {
  mkdir -p "${output_dir}"
  printf "namespace\tmetric\tdimension_name\tdimension_value\tstatistic\tvalue\n" >"${snapshot_tsv}"
  while IFS=$'\t' read -r namespace metric dimension_name dimension_value statistics_csv; do
    IFS=',' read -r -a statistics <<<"${statistics_csv}"
    local statistic
    for statistic in "${statistics[@]}"; do
      printf "%s\t%s\t%s\t%s\t%s\t%s\n" \
        "${namespace}" "${metric}" "${dimension_name}" "${dimension_value}" "${statistic}" \
        "$(cloudwatch_metric_value "${namespace}" "${metric}" "${dimension_name}" "${dimension_value}" "${statistic}")" >>"${snapshot_tsv}"
    done
  done < <(emit_metric_plan)
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  exit 0
fi
if [[ "${mode}" == "dry-run" ]]; then
  emit_metric_plan
  exit 0
fi
if [[ -z "${region}" ]]; then
  echo "CAPACITY_CLOUDWATCH_REGION or AWS_REGION is required" >&2
  exit 1
fi
if [[ -z "${ec2_instance_id}" && -z "${rds_identifier}" && -z "${ebs_volume_id}" ]]; then
  echo "at least one CloudWatch dimension target is required" >&2
  exit 1
fi
if ! command -v aws >/dev/null 2>&1; then
  echo "aws CLI is required" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

write_metric_rows
echo "${snapshot_tsv}"
