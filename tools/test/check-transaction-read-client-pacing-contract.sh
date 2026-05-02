#!/usr/bin/env bash
set -euo pipefail

echo "[transaction-read-client-pacing] nginx retry headers"
grep -F 'add_header Retry-After ${NGINX_EDGE_RETRY_AFTER_SECONDS} always;' ops/nginx/nginx.conf >/dev/null
grep -F 'add_header X-RateLimit-Scope nginx-edge always;' ops/nginx/nginx.conf >/dev/null
grep -F 'add_header X-RateLimit-Retry-After-Millis ${NGINX_EDGE_RETRY_AFTER_MILLIS} always;' ops/nginx/nginx.conf >/dev/null
grep -F 'add_header X-RateLimit-Retry-Jitter-Millis ${NGINX_EDGE_RETRY_JITTER_MILLIS} always;' ops/nginx/nginx.conf >/dev/null
grep -F '"retryAfterMillis":${NGINX_EDGE_RETRY_AFTER_MILLIS}' ops/nginx/nginx.conf >/dev/null

echo "[transaction-read-client-pacing] k6 client pacing"
grep -F 'K6_PREEMPTIVE_PACING enable request-before token pacing' tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F 'K6_PREEMPTIVE_PACING_RPS global target request rate split by K6_VUS' tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F 'K6_PREEMPTIVE_PACING_MAX_SLEEP_MS default 250' tools/test/run-k6-transaction-100m-loadtest.sh >/dev/null
grep -F 'const preemptivePacingEnabled = preemptivePacing && preemptivePacingIntervalMs > 0;' ops/k6/transaction-read-100m.js >/dev/null
grep -F 'aquila_transaction_preemptive_pacing_count' ops/k6/transaction-read-100m.js >/dev/null
grep -F 'aquila_transaction_preemptive_pacing_sleep_ms' ops/k6/transaction-read-100m.js >/dev/null

echo "[transaction-read-client-pacing] docs contract"
grep -F 'Retry-After' ops/nginx/README.md >/dev/null
grep -F 'X-RateLimit-Retry-After-Millis' ops/nginx/README.md >/dev/null
grep -F 'preemptive pacing' ops/nginx/README.md >/dev/null
