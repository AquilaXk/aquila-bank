#!/usr/bin/env bash
set -euo pipefail

resource_script="tools/test/run-ec2-local-db-resource-snapshot.sh"
compare_script="tools/test/compare-ec2-direct-vs-nginx-latency.sh"

echo "[ec2-resource-comparison] shell syntax"
bash -n "${resource_script}" "${compare_script}" tools/test/run-capacity-cloudwatch-credit-gp3-snapshot.sh

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

echo "[ec2-resource-comparison] resource plan"
resource_plan="$(
  EC2_CAPACITY_RUN_ID=ec2-resource-check \
  EC2_RESOURCE_OUTPUT_DIR="${temp_dir}/resource" \
  EC2_RESOURCE_DOCKER_STATS=true \
  EC2_RESOURCE_CLOUDWATCH_ENABLED=true \
  EC2_CAPACITY_AWS_REGION=ap-northeast-2 \
  EC2_CAPACITY_EC2_INSTANCE_ID=i-0123456789abcdef0 \
  EC2_CAPACITY_EBS_VOLUME_ID=vol-0123456789abcdef0 \
  EC2_CAPACITY_CLOUDWATCH_START_TIME=2026-04-28T00:00:00Z \
  EC2_CAPACITY_CLOUDWATCH_END_TIME=2026-04-28T00:30:00Z \
    "${resource_script}" --print-plan
)"
grep -F "run_id=ec2-resource-check" <<<"${resource_plan}" >/dev/null
grep -F "docker_stats=true" <<<"${resource_plan}" >/dev/null
grep -F "docker_discovery=true" <<<"${resource_plan}" >/dev/null
grep -F "docker_labels=com.aquilabank.service=nginx,com.aquilabank.service=backend,com.aquilabank.service=postgres" <<<"${resource_plan}" >/dev/null
grep -F "docker_name_regex=^(aquila-bank-nginx|aquila-bank-backend-[ab]|aquila-postgres)$" <<<"${resource_plan}" >/dev/null
grep -F "cloudwatch_enabled=true" <<<"${resource_plan}" >/dev/null
grep -F "ec2_instance_id=i-0123456789abcdef0" <<<"${resource_plan}" >/dev/null
grep -F "ebs_volume_id=vol-0123456789abcdef0" <<<"${resource_plan}" >/dev/null

echo "[ec2-resource-comparison] resource dry-run"
EC2_CAPACITY_RUN_ID=ec2-resource-check \
EC2_RESOURCE_OUTPUT_DIR="${temp_dir}/resource" \
EC2_RESOURCE_DOCKER_STATS=true \
EC2_RESOURCE_CLOUDWATCH_ENABLED=false \
  "${resource_script}" --dry-run >/dev/null

echo "[ec2-resource-comparison] docker discovery snapshot"
fake_bin="${temp_dir}/bin"
mkdir -p "${fake_bin}"
cat >"${fake_bin}/docker" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

if [[ "$1" == "ps" ]]; then
  case "$*" in
    *"label=com.aquilabank.service=nginx"*) echo "aquila-bank-nginx"; exit 0 ;;
    *"label=com.aquilabank.service=backend"*) echo "aquila-bank-backend-b"; exit 0 ;;
    *"label=com.aquilabank.service=postgres"*) echo "aquila-postgres"; exit 0 ;;
  esac
  printf 'aquila-bank-nginx\naquila-bank-backend-b\naquila-postgres\n'
  exit 0
fi

if [[ "$1" == "stats" ]]; then
  container="${@: -1}"
  case "${container}" in
    aquila-bank-nginx) printf '1.00%%\t5MiB / 1GiB\t0.49%%\t4\n' ;;
    aquila-bank-backend-b) printf '36.00%%\t375MiB / 1GiB\t36.62%%\t42\n' ;;
    aquila-postgres) printf '18.00%%\t2048MiB / 16GiB\t12.50%%\t75\n' ;;
    *) exit 1 ;;
  esac
  exit 0
fi

echo "unexpected docker command: $*" >&2
exit 1
SH
chmod +x "${fake_bin}/docker"

resource_output="$(
  PATH="${fake_bin}:${PATH}" \
  EC2_CAPACITY_RUN_ID=ec2-resource-discovery-check \
  EC2_RESOURCE_OUTPUT_DIR="${temp_dir}/resource-discovery" \
  EC2_RESOURCE_DOCKER_STATS=true \
  EC2_RESOURCE_CLOUDWATCH_ENABLED=false \
    "${resource_script}"
)"
manifest_env="$(tail -1 <<<"${resource_output}")"
docker_stats_tsv="${temp_dir}/resource-discovery/ec2-resource-discovery-check-resource-docker-stats.tsv"
test "${manifest_env}" = "${temp_dir}/resource-discovery/ec2-resource-discovery-check-resource-manifest.env"
grep -F $'aquila-bank-nginx\t1.00%' "${docker_stats_tsv}" >/dev/null
grep -F $'aquila-bank-backend-b\t36.00%' "${docker_stats_tsv}" >/dev/null
grep -F $'aquila-postgres\t18.00%' "${docker_stats_tsv}" >/dev/null
if grep -F "aquila-bank-backend-a" "${docker_stats_tsv}" >/dev/null; then
  echo "inactive backend-a should not be sampled when discovery finds backend-b" >&2
  exit 1
fi

echo "[ec2-resource-comparison] comparison report"
direct_md="${temp_dir}/direct.md"
nginx_md="${temp_dir}/nginx.md"
cat >"${direct_md}" <<'MD'
# direct
- transaction 429 rate: 0
- transaction 503 rate: 0
- transaction 503 count: 0
- hot first p95 ms: 100
- hot first p99 ms: 120
- hot cursor p95 ms: 80
- hot cursor p99 ms: 90
- cold first p95 ms: 200
- cold first p99 ms: 240
- cold cursor p95 ms: 180
- cold cursor p99 ms: 210
MD
cat >"${nginx_md}" <<'MD'
# nginx
- transaction 429 rate: 0.01
- transaction 503 rate: 0
- transaction 503 count: 0
- hot first p95 ms: 110
- hot first p99 ms: 130
- hot cursor p95 ms: 84
- hot cursor p99 ms: 95
- cold first p95 ms: 212
- cold first p99 ms: 255
- cold cursor p95 ms: 190
- cold cursor p99 ms: 225
MD
output_md="${temp_dir}/comparison.md"
EC2_DIRECT_K6_SUMMARY_MD="${direct_md}" \
EC2_NGINX_K6_SUMMARY_MD="${nginx_md}" \
EC2_COMPARISON_OUTPUT_MD="${output_md}" \
  "${compare_script}" >/dev/null
grep -F "| hot first p95 ms | 100 | 110 | 10.000 |" "${output_md}" >/dev/null
grep -F "| transaction 429 rate | 0 | 0.01 | 0.010 |" "${output_md}" >/dev/null

echo "[ec2-resource-comparison] invalid comparison fails"
if EC2_DIRECT_K6_SUMMARY_MD="${temp_dir}/missing.md" EC2_NGINX_K6_SUMMARY_MD="${nginx_md}" "${compare_script}" >/dev/null 2>&1; then
  echo "missing direct summary unexpectedly passed" >&2
  exit 1
fi

echo "[ec2-resource-comparison] contract check passed"
