#!/usr/bin/env bash
set -euo pipefail

script="tools/test/run-docker-t3micro-capacity-smoke.sh"

echo "[docker-t3micro-capacity-script] syntax: ${script}"
bash -n "${script}"

echo "[docker-t3micro-capacity-script] plan includes Docker cgroup budget and production smoke source"
plan="$(
  DOCKER_T3MICRO_IMAGE=local-java21 \
  DOCKER_T3MICRO_CPUS=2 \
  DOCKER_T3MICRO_MEMORY=1024m \
  DOCKER_T3MICRO_MEMORY_SWAP=1024m \
  DOCKER_T3MICRO_PIDS_LIMIT=384 \
  SOAK_REPEAT=2 \
    "${script}" --print-plan
)"
grep -F "source=tools/test/run-production-t3micro-capacity-smoke.sh" <<<"${plan}" >/dev/null
grep -F "image=local-java21" <<<"${plan}" >/dev/null
grep -F "cpus=2 memory=1024m memory-swap=1024m pids-limit=384" <<<"${plan}" >/dev/null
grep -F "prepare-test-classes=true" <<<"${plan}" >/dev/null
grep -F "repeat=2" <<<"${plan}" >/dev/null
grep -F "DB_POOL_MAX_SIZE=4" <<<"${plan}" >/dev/null
grep -F "SERVER_THREADS_MAX=16" <<<"${plan}" >/dev/null

echo "[docker-t3micro-capacity-script] dry-run includes Docker resource flags"
dry_run="$(
  DOCKER_T3MICRO_IMAGE=local-java21 \
  DOCKER_T3MICRO_CPUS=2 \
  DOCKER_T3MICRO_MEMORY=1024m \
  DOCKER_T3MICRO_MEMORY_SWAP=1024m \
  DOCKER_T3MICRO_PIDS_LIMIT=384 \
    "${script}" --dry-run
)"
grep -F -- "--cpus 2" <<<"${dry_run}" >/dev/null
grep -F -- "--memory 1024m" <<<"${dry_run}" >/dev/null
grep -F -- "--memory-swap 1024m" <<<"${dry_run}" >/dev/null
grep -F -- "--pids-limit 384" <<<"${dry_run}" >/dev/null
grep -F "local-java21 bash -lc tools/test/run-production-t3micro-capacity-smoke.sh" <<<"${dry_run}" >/dev/null

echo "[docker-t3micro-capacity-script] compose override is valid"
docker compose -f compose.yml -f compose.t3micro.yml config >/dev/null

echo "[docker-t3micro-capacity-script] invalid Docker budget fails before run"
if DOCKER_T3MICRO_CPUS=0 "${script}" --print-plan >/dev/null 2>&1; then
  echo "DOCKER_T3MICRO_CPUS=0 unexpectedly succeeded" >&2
  exit 1
fi
if DOCKER_T3MICRO_MEMORY=0m "${script}" --print-plan >/dev/null 2>&1; then
  echo "DOCKER_T3MICRO_MEMORY=0m unexpectedly succeeded" >&2
  exit 1
fi
if DOCKER_T3MICRO_PIDS_LIMIT=abc "${script}" --print-plan >/dev/null 2>&1; then
  echo "DOCKER_T3MICRO_PIDS_LIMIT=abc unexpectedly succeeded" >&2
  exit 1
fi
if DOCKER_T3MICRO_PREPARE_TEST_CLASSES=maybe "${script}" --print-plan >/dev/null 2>&1; then
  echo "DOCKER_T3MICRO_PREPARE_TEST_CLASSES=maybe unexpectedly succeeded" >&2
  exit 1
fi
