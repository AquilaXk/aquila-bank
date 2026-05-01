#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-k6-transaction-100m-multisource.sh"

echo "[k6-transaction-100m-multisource] shell syntax"
bash -n "${runner}"

echo "[k6-transaction-100m-multisource] print plan"
plan="$(
  K6_MULTI_SOURCE_NAME=transaction-read-multisource-check \
  K6_MULTI_SOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  K6_MULTI_SOURCE_BASE_URLS=http://10.0.1.10:8080,http://10.0.2.10:8080 \
  K6_MULTI_SOURCE_PROMETHEUS_RW_SERVER_URLS=http://10.0.1.20:9090/api/v1/write,http://10.0.2.20:9090/api/v1/write \
  K6_MULTI_SOURCE_REMOTE_WORKDIRS=/srv/aquila-bank-a,/srv/aquila-bank-b \
  K6_MULTI_SOURCE_RUN_ID_PREFIX=weighted-10m \
  K6_WORKLOAD_SHAPE=weighted-random \
  K6_DURATION=10m \
    "${runner}" --print-plan
)"
grep -F "name=transaction-read-multisource-check" <<<"${plan}" >/dev/null
grep -F "source boundary=multi-source-real-ip" <<<"${plan}" >/dev/null
grep -F "shard_count=2" <<<"${plan}" >/dev/null
grep -F "child runner=tools/test/run-k6-transaction-100m-loadtest.sh" <<<"${plan}" >/dev/null
grep -F "child generator mode=docker-context" <<<"${plan}" >/dev/null
grep -F "shard=1 context=oci-k6-a base_url=http://10.0.1.10:8080 prometheus_rw=http://10.0.1.20:9090/api/v1/write workdir=/srv/aquila-bank-a run_id=weighted-10m-shard-1 report_name=weighted-10m-shard-1" <<<"${plan}" >/dev/null
grep -F "shard=2 context=oci-k6-b base_url=http://10.0.2.10:8080 prometheus_rw=http://10.0.2.20:9090/api/v1/write workdir=/srv/aquila-bank-b run_id=weighted-10m-shard-2 report_name=weighted-10m-shard-2" <<<"${plan}" >/dev/null
grep -F "shared_env K6_WORKLOAD_SHAPE=weighted-random K6_DURATION=10m" <<<"${plan}" >/dev/null

echo "[k6-transaction-100m-multisource] shared base URL"
shared_plan="$(
  K6_MULTI_SOURCE_NAME=transaction-read-multisource-shared-base-check \
  K6_MULTI_SOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  K6_MULTI_SOURCE_BASE_URLS=http://10.0.1.10:8080 \
  K6_MULTI_SOURCE_RUN_ID_PREFIX=shared-base \
    "${runner}" --print-plan
)"
grep -F "shard_count=2" <<<"${shared_plan}" >/dev/null
grep -F "shard=1 context=oci-k6-a base_url=http://10.0.1.10:8080 prometheus_rw=disabled" <<<"${shared_plan}" >/dev/null
grep -F "shard=2 context=oci-k6-b base_url=http://10.0.1.10:8080 prometheus_rw=disabled" <<<"${shared_plan}" >/dev/null

echo "[k6-transaction-100m-multisource] invalid context"
if K6_MULTI_SOURCE_NAME=transaction-read-multisource-invalid \
  K6_MULTI_SOURCE_CONTEXTS=oci-k6-a,oci-k6-b \
  K6_MULTI_SOURCE_BASE_URLS=http://10.0.1.10:8080,http://10.0.2.10:8080,http://10.0.3.10:8080 \
    "${runner}" --print-plan >/dev/null 2>&1; then
  echo "multi-source runner unexpectedly accepted mismatched base URLs" >&2
  exit 1
fi
