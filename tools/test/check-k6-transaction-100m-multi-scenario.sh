#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-k6-transaction-100m-multi-scenario.sh"

echo "[k6-transaction-100m-multi] shell syntax"
bash -n "${script}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

scenarios="baseline:constant-vus:3:30s:false:16:3:3,burst-256:burst:16:20s:true:256:256:256"

echo "[k6-transaction-100m-multi] plan"
plan="$(
  K6_MULTI_NAME=multi-check \
  K6_MULTI_RUN_ID=multi-run-check \
  K6_MULTI_OUTPUT_DIR="${temp_dir}/multi" \
  K6_MULTI_SCENARIOS="${scenarios}" \
    "${script}" --print-plan
)"
grep -F "name=multi-check" <<<"${plan}" >/dev/null
grep -F "run_id=multi-run-check" <<<"${plan}" >/dev/null
grep -F "output_dir=${temp_dir}/multi" <<<"${plan}" >/dev/null
grep -F "reuse_backend=true" <<<"${plan}" >/dev/null
grep -F "post_first_recovery_noise_window_seconds=0" <<<"${plan}" >/dev/null
grep -F "scenario_count=2" <<<"${plan}" >/dev/null
grep -F "execution_plan=${temp_dir}/multi/multi-check-execution-plan.tsv" <<<"${plan}" >/dev/null

echo "[k6-transaction-100m-multi] dry-run execution plan"
output="$(
  K6_MULTI_NAME=multi-check \
  K6_MULTI_RUN_ID=multi-run-check \
  K6_MULTI_OUTPUT_DIR="${temp_dir}/multi" \
  K6_MULTI_SCENARIOS="${scenarios}" \
    "${script}" --dry-run
)"
execution_plan="$(tail -1 <<<"${output}")"
test "${execution_plan}" = "${temp_dir}/multi/multi-check-execution-plan.tsv"
test -s "${execution_plan}"
grep -F $'order\tname\tmode\tvus\tduration\toverload\tburst_rate\tpre_allocated_vus\tmax_vus\treport_name\trunner_args\trecovery_noise_window_seconds' "${execution_plan}" >/dev/null
grep -F $'1\tbaseline\tconstant-vus\t3\t30s\tfalse\t16\t3\t3\tmulti-check-baseline\tdefault\t30' "${execution_plan}" >/dev/null
grep -F $'2\tburst-256\tburst\t16\t20s\ttrue\t256\t256\t256\tmulti-check-burst-256\t--no-up --no-deps\t0' "${execution_plan}" >/dev/null

echo "[k6-transaction-100m-multi] invalid input fails"
if K6_MULTI_SCENARIOS=bad "${script}" --print-plan >/dev/null 2>&1; then
  echo "bad multi scenario unexpectedly passed" >&2
  exit 1
fi
if K6_MULTI_REUSE_BACKEND=maybe "${script}" --print-plan >/dev/null 2>&1; then
  echo "bad reuse flag unexpectedly passed" >&2
  exit 1
fi
if K6_MULTI_POST_FIRST_RECOVERY_NOISE_WINDOW_SECONDS=bad "${script}" --print-plan >/dev/null 2>&1; then
  echo "bad recovery noise window unexpectedly passed" >&2
  exit 1
fi

echo "[k6-transaction-100m-multi] runner contract"
grep -F "run-k6-transaction-100m-loadtest.sh --no-up --no-deps" "${script}" >/dev/null
grep -F "K6_POSTGRES_RECOVERY_NOISE_WINDOW_SECONDS=\"\${post_first_noise_window}\"" "${script}" >/dev/null
grep -F "K6_PRE_ALLOCATED_VUS=\"\${pre_allocated}\"" "${script}" >/dev/null
grep -F "K6_MAX_VUS=\"\${max_vus}\"" "${script}" >/dev/null
