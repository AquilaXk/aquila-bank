#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-capacity-cloudwatch-credit-gp3-snapshot.sh"

echo "[capacity-cloudwatch] shell syntax"
bash -n "${script}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

echo "[capacity-cloudwatch] plan"
plan="$(
  CAPACITY_CLOUDWATCH_NAME=cloudwatch-check \
  CAPACITY_CLOUDWATCH_OUTPUT_DIR="${temp_dir}" \
  CAPACITY_CLOUDWATCH_REGION=ap-northeast-2 \
  CAPACITY_CLOUDWATCH_START_TIME=2026-04-28T00:00:00Z \
  CAPACITY_CLOUDWATCH_END_TIME=2026-04-28T00:30:00Z \
  CAPACITY_CLOUDWATCH_EC2_INSTANCE_ID=i-0123456789abcdef0 \
  CAPACITY_CLOUDWATCH_RDS_IDENTIFIER=aquila-staging-rds \
  CAPACITY_CLOUDWATCH_EBS_VOLUME_ID=vol-0123456789abcdef0 \
    "${script}" --print-plan
)"
grep -F "name=cloudwatch-check" <<<"${plan}" >/dev/null
grep -F "region=ap-northeast-2" <<<"${plan}" >/dev/null
grep -F "ec2_instance_id=i-0123456789abcdef0" <<<"${plan}" >/dev/null
grep -F "rds_identifier=aquila-staging-rds" <<<"${plan}" >/dev/null
grep -F "ebs_volume_id=vol-0123456789abcdef0" <<<"${plan}" >/dev/null
grep -F "snapshot_tsv=${temp_dir}/cloudwatch-check-cloudwatch-capacity-snapshot.tsv" <<<"${plan}" >/dev/null

echo "[capacity-cloudwatch] dry-run metrics"
dry_run="$(
  CAPACITY_CLOUDWATCH_NAME=cloudwatch-check \
  CAPACITY_CLOUDWATCH_OUTPUT_DIR="${temp_dir}" \
  CAPACITY_CLOUDWATCH_REGION=ap-northeast-2 \
  CAPACITY_CLOUDWATCH_START_TIME=2026-04-28T00:00:00Z \
  CAPACITY_CLOUDWATCH_END_TIME=2026-04-28T00:30:00Z \
  CAPACITY_CLOUDWATCH_EC2_INSTANCE_ID=i-0123456789abcdef0 \
  CAPACITY_CLOUDWATCH_RDS_IDENTIFIER=aquila-staging-rds \
  CAPACITY_CLOUDWATCH_EBS_VOLUME_ID=vol-0123456789abcdef0 \
    "${script}" --dry-run
)"
grep -F $'AWS/EC2\tCPUCreditBalance\tInstanceId\ti-0123456789abcdef0' <<<"${dry_run}" >/dev/null
grep -F $'AWS/RDS\tFreeableMemory\tDBInstanceIdentifier\taquila-staging-rds' <<<"${dry_run}" >/dev/null
grep -F $'AWS/EBS\tVolumeQueueLength\tVolumeId\tvol-0123456789abcdef0' <<<"${dry_run}" >/dev/null

echo "[capacity-cloudwatch] invalid input fails"
if CAPACITY_CLOUDWATCH_PERIOD_SECONDS=0 "${script}" --print-plan >/dev/null 2>&1; then
  echo "zero period unexpectedly passed" >&2
  exit 1
fi
if CAPACITY_CLOUDWATCH_START_TIME=bad CAPACITY_CLOUDWATCH_END_TIME=2026-04-28T00:30:00Z "${script}" --print-plan >/dev/null 2>&1; then
  echo "bad start time unexpectedly passed" >&2
  exit 1
fi

echo "[capacity-cloudwatch] runner contract"
grep -F "aws cloudwatch get-metric-statistics" "${script}" >/dev/null
grep -F "CPUCreditBalance" "${script}" >/dev/null
grep -F "FreeableMemory" "${script}" >/dev/null
grep -F "VolumeQueueLength" "${script}" >/dev/null
grep -F "capacity-cloudwatch" "${script}" >/dev/null
