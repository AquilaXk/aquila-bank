#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/transaction-100m-fixture-dump-publish.yml"

echo "[transaction-100m-fixture-publish] workflow contract"
test -f "${workflow}"
grep -F "workflow_dispatch:" "${workflow}" >/dev/null
grep -F "tools/test/prepare-transaction-read-model-100m-fixture.sh" "${workflow}" >/dev/null
grep -F "FIXTURE_MODE: dump" "${workflow}" >/dev/null
grep -F "FIXTURE_WRITE_MANIFEST: \"true\"" "${workflow}" >/dev/null
grep -F "tools/test/validate-transaction-100m-fixture-artifact.sh --verify" "${workflow}" >/dev/null
grep -F "build/fixtures/\${{ inputs.fixture_name }}.dump" "${workflow}" >/dev/null
grep -F "build/fixtures/\${{ inputs.fixture_name }}.dump.manifest" "${workflow}" >/dev/null
grep -F "build/fixtures/\${{ inputs.fixture_name }}.dump.sha256" "${workflow}" >/dev/null
grep -F "compression-level: 0" "${workflow}" >/dev/null

echo "[transaction-100m-fixture-publish] shell syntax"
bash -n tools/test/check-transaction-100m-fixture-dump-publish-workflow.sh
