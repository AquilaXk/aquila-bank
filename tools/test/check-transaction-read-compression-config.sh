#!/usr/bin/env bash
set -euo pipefail

application_config="back/src/main/resources/application.yml"
loadtest_compose="compose.loadtest.yml"

echo "[transaction-compression-config] application defaults"
grep -F 'enabled: ${SERVER_COMPRESSION_ENABLED:true}' "${application_config}" >/dev/null
grep -F 'min-response-size: ${SERVER_COMPRESSION_MIN_RESPONSE_SIZE:2KB}' "${application_config}" >/dev/null
grep -F 'mime-types: ${SERVER_COMPRESSION_MIME_TYPES:application/json,application/problem+json}' "${application_config}" >/dev/null

echo "[transaction-compression-config] loadtest compose defaults"
grep -F 'SERVER_COMPRESSION_ENABLED: ${SERVER_COMPRESSION_ENABLED:-true}' "${loadtest_compose}" >/dev/null
grep -F 'SERVER_COMPRESSION_MIN_RESPONSE_SIZE: ${SERVER_COMPRESSION_MIN_RESPONSE_SIZE:-2KB}' "${loadtest_compose}" >/dev/null
grep -F 'SERVER_COMPRESSION_MIME_TYPES: ${SERVER_COMPRESSION_MIME_TYPES:-application/json,application/problem+json}' "${loadtest_compose}" >/dev/null

echo "[transaction-compression-config] benchmark contract"
grep -F "on-2kb:true:2048" tools/test/run-transaction-read-compression-benchmark.sh >/dev/null
grep -F "server.compression.min-response-size" tools/test/run-transaction-read-compression-benchmark.sh >/dev/null
