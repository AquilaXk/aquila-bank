#!/usr/bin/env bash
set -euo pipefail

echo "[redis-login-throttling-runtime] compose profile: redis"
echo "[redis-login-throttling-runtime] expectation: Redis-backed login throttling shares counters and expires windows"

docker compose --profile redis up -d redis

container_id="$(docker compose ps -q redis)"
if [[ -z "${container_id}" ]]; then
  echo "[redis-login-throttling-runtime] redis container was not created" >&2
  exit 1
fi

for attempt in {1..30}; do
  if docker compose exec -T redis redis-cli ping | grep -q PONG; then
    break
  fi
  if [[ "${attempt}" == "30" ]]; then
    echo "[redis-login-throttling-runtime] redis did not become ready" >&2
    exit 1
  fi
  sleep 1
done

REDIS_LOGIN_THROTTLING_RUNTIME_SMOKE=true \
REDIS_HOST="${REDIS_HOST:-localhost}" \
REDIS_PORT="${REDIS_PORT:-6379}" \
SECURITY_LOGIN_THROTTLING_REDIS_KEY_PREFIX="${SECURITY_LOGIN_THROTTLING_REDIS_KEY_PREFIX:-auth:login:runtime-smoke:}" \
  ./back/gradlew -p back test --tests '*RedisLoginThrottleRuntimeSmokeTest'
