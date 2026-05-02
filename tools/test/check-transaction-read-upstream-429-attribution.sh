#!/usr/bin/env bash
set -euo pipefail

handler="back/src/main/java/com/aquilabank/global/web/ApiExceptionHandler.java"
metrics="back/src/main/java/com/aquilabank/global/web/transaction/TransactionReadUpstream429Metrics.java"
fairness_exception="back/src/main/java/com/aquilabank/global/web/transaction/TransactionReadAccountFairnessRejectedException.java"
api_overload_test="back/src/test/java/com/aquilabank/global/web/ApiExceptionHandlerApiOverloadTest.java"
security_test="back/src/test/java/com/aquilabank/global/web/ApiExceptionHandlerSecurityRateLimitTest.java"
fairness_test="back/src/test/java/com/aquilabank/global/web/transaction/TransactionReadAccountFairnessRejectedExceptionHandlerTest.java"

echo "[transaction-read-upstream-429] source header contract"
grep -F 'X-Aquila-429-Source' "${handler}" >/dev/null
grep -F 'X-Aquila-Reject-Source' "${handler}" >/dev/null
grep -F 'X-Aquila-Reject-Reason' "${handler}" >/dev/null
grep -F 'X-RateLimit-Scope' "${handler}" >/dev/null

echo "[transaction-read-upstream-429] source values"
grep -F 'backend-admission' "${handler}" >/dev/null
grep -F 'security-filter' "${handler}" >/dev/null
grep -F 'saturation-guard' "${handler}" >/dev/null
grep -F 'fairness-limiter' "${fairness_exception}" >/dev/null
grep -F 'transaction-read-account' "${fairness_exception}" >/dev/null

echo "[transaction-read-upstream-429] metrics contract"
grep -F 'aquila.transaction.read.upstream.429' "${metrics}" >/dev/null
grep -F '"source", source, "endpoint", endpoint' "${metrics}" >/dev/null
grep -F '"/api/v1/transactions/archive"' "${metrics}" >/dev/null
grep -F '"/api/v1/transactions"' "${metrics}" >/dev/null
grep -F 'transaction read upstream 429 attributed' "${handler}" >/dev/null

echo "[transaction-read-upstream-429] test coverage"
grep -F 'tag("source", "backend-admission")' "${api_overload_test}" >/dev/null
grep -F 'tag("source", "security-filter")' "${security_test}" >/dev/null
grep -F 'tag("source", "fairness-limiter")' "${fairness_test}" >/dev/null
grep -F 'X-Aquila-429-Source' "${api_overload_test}" >/dev/null
grep -F 'X-Aquila-429-Source' "${security_test}" >/dev/null
grep -F 'X-Aquila-429-Source' "${fairness_test}" >/dev/null
grep -F 'X-Aquila-Reject-Source' "${api_overload_test}" >/dev/null
grep -F 'X-Aquila-Reject-Source' "${security_test}" >/dev/null
grep -F 'X-Aquila-Reject-Source' "${fairness_test}" >/dev/null
