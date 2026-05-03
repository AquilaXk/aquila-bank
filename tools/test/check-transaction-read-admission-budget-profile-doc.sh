#!/usr/bin/env bash
set -euo pipefail

doc="ops/nginx/README.md"

echo "[transaction-read-admission-budget-profile-doc] doc exists"
test -f "${doc}"

echo "[transaction-read-admission-budget-profile-doc] profile contract"
grep -F "## Transaction Read Admission Budget Profile" "${doc}" >/dev/null
grep -F "normal profile" "${doc}" >/dev/null
grep -F "saturation profile" "${doc}" >/dev/null
grep -F "overload profile" "${doc}" >/dev/null
grep -F 'NGINX_TRANSACTION_READ_BUDGET_PROFILE' "${doc}" >/dev/null
grep -F 'OCI_A1_TRANSACTION_READ_BUDGET_PROFILE' "${doc}" >/dev/null
grep -F '`burst64`' "${doc}" >/dev/null
grep -F '`balanced`' "${doc}" >/dev/null
grep -F '`fail-fast`' "${doc}" >/dev/null

echo "[transaction-read-admission-budget-profile-doc] thresholds"
grep -F 'arrival-rate `16/s` strict gate' "${doc}" >/dev/null
grep -F 'edge 429 `0`' "${doc}" >/dev/null
grep -F 'burst64 edge 429 `<= 10%`' "${doc}" >/dev/null
grep -F 'backend 429 `<= 0.5%`' "${doc}" >/dev/null
grep -F '5xx `0`' "${doc}" >/dev/null
grep -F 'accepted p95 `< 100ms`' "${doc}" >/dev/null
grep -F 'Retry-After p95 `<= 250ms`' "${doc}" >/dev/null

echo "[transaction-read-admission-budget-profile-doc] promotion and rollback"
grep -F "Staging promotion 조건" "${doc}" >/dev/null
grep -F "Production promotion hold 조건" "${doc}" >/dev/null
grep -F "Nginx aggregate TSV/JSON/MD" "${doc}" >/dev/null
grep -F '`default` Docker context' "${doc}" >/dev/null
grep -F "Profile 변경 rollback" "${doc}" >/dev/null
grep -F "evidence incomplete" "${doc}" >/dev/null
