#!/usr/bin/env bash
set -euo pipefail

runner="tools/test/run-transaction-read-429-source-gate.sh"

echo "[transaction-read-429-source] shell syntax"
bash -n "${runner}"

temp_dir="$(mktemp -d)"
trap 'rm -rf "${temp_dir}"' EXIT

summary_json="${temp_dir}/summary.json"
fail_json="${temp_dir}/fail-summary.json"
output_dir="${temp_dir}/output"

cat >"${summary_json}" <<'JSON'
{
  "metrics": {
    "http_reqs": {"values": {"count": 1000}},
    "aquila_transaction_429_rate": {"values": {"rate": 0.08}},
    "aquila_transaction_edge_429_rate": {"values": {"rate": 0.03}},
    "aquila_transaction_edge_429_count": {"values": {"count": 30}},
    "aquila_transaction_backend_429_rate": {"values": {"rate": 0.05}},
    "aquila_transaction_backend_429_count": {"values": {"count": 50}},
    "aquila_transaction_unknown_429_rate": {"values": {"rate": 0}},
    "aquila_transaction_unknown_429_count": {"values": {"count": 0}},
    "aquila_transaction_502_rate": {"values": {"rate": 0}},
    "aquila_transaction_502_count": {"values": {"count": 0}},
    "aquila_transaction_accepted_200_rate": {"values": {"rate": 0.92}},
    "aquila_transaction_accepted_200_count": {"values": {"count": 920}}
  }
}
JSON

cat >"${fail_json}" <<'JSON'
{
  "metrics": {
    "http_reqs": {"values": {"count": 1000}},
    "aquila_transaction_429_rate": {"values": {"rate": 0.12}},
    "aquila_transaction_edge_429_rate": {"values": {"rate": 0.11}},
    "aquila_transaction_edge_429_count": {"values": {"count": 110}},
    "aquila_transaction_backend_429_rate": {"values": {"rate": 0.01}},
    "aquila_transaction_backend_429_count": {"values": {"count": 10}},
    "aquila_transaction_unknown_429_rate": {"values": {"rate": 0}},
    "aquila_transaction_unknown_429_count": {"values": {"count": 0}},
    "aquila_transaction_502_rate": {"values": {"rate": 0.001}},
    "aquila_transaction_502_count": {"values": {"count": 1}},
    "aquila_transaction_accepted_200_rate": {"values": {"rate": 0.879}},
    "aquila_transaction_accepted_200_count": {"values": {"count": 879}}
  }
}
JSON

echo "[transaction-read-429-source] print plan"
plan="$(
  SOURCE_429_GATE_NAME=source-check \
  SOURCE_429_SUMMARY_JSON="${summary_json}" \
  SOURCE_429_OUTPUT_DIR="${output_dir}" \
  SOURCE_429_RUN_ID=public-arrival-8 \
    "${runner}" --print-plan
)"
grep -F "gate=source-check" <<<"${plan}" >/dev/null
grep -F "summary_json=${summary_json}" <<<"${plan}" >/dev/null
grep -F "run_id=public-arrival-8" <<<"${plan}" >/dev/null
grep -F "output_dir=${output_dir}" <<<"${plan}" >/dev/null

echo "[transaction-read-429-source] pass report"
output="$(
  SOURCE_429_GATE_NAME=source-check \
  SOURCE_429_SUMMARY_JSON="${summary_json}" \
  SOURCE_429_OUTPUT_DIR="${output_dir}" \
  SOURCE_429_RUN_ID=public-arrival-8 \
    "${runner}"
)"
report_md="$(tail -1 <<<"${output}")"
summary_tsv="${output_dir}/source-check-429-source.tsv"
test "${report_md}" = "${output_dir}/source-check-429-source.md"
grep -F $'source\tstatus\trate\tcount\tfail_threshold' "${summary_tsv}" >/dev/null
grep -F $'edge\tpass\t0.03\t30\t0.10' "${summary_tsv}" >/dev/null
grep -F $'backend\tpass\t0.05\t50\t0.10' "${summary_tsv}" >/dev/null
grep -F $'unknown\tpass\t0\t0\t0' "${summary_tsv}" >/dev/null
grep -F $'502\tpass\t0\t0\t0' "${summary_tsv}" >/dev/null
grep -F $'accepted_200\tobserve\t0.92\t920\tn/a' "${summary_tsv}" >/dev/null
grep -F "gate_status=pass" "${report_md}" >/dev/null
grep -F "run_id=public-arrival-8" "${report_md}" >/dev/null
grep -F "| edge 429 | pass | 0.03 | 30 |" "${report_md}" >/dev/null
grep -F "| backend admission 429 | pass | 0.05 | 50 |" "${report_md}" >/dev/null
grep -F "| accepted 200 | observe | 0.92 | 920 |" "${report_md}" >/dev/null

echo "[transaction-read-429-source] fail report"
if SOURCE_429_GATE_NAME=source-fail \
  SOURCE_429_SUMMARY_JSON="${fail_json}" \
  SOURCE_429_OUTPUT_DIR="${output_dir}" \
    "${runner}" >/dev/null 2>&1; then
  echo "source gate unexpectedly passed edge 429/502 failure" >&2
  exit 1
fi

echo "[transaction-read-429-source] runner contract"
grep -F "aquila_transaction_edge_429_rate" "${runner}" >/dev/null
grep -F "aquila_transaction_backend_429_rate" "${runner}" >/dev/null
grep -F "aquila_transaction_unknown_429_rate" "${runner}" >/dev/null
grep -F "aquila_transaction_502_count" "${runner}" >/dev/null
grep -F "aquila_transaction_accepted_200_count" "${runner}" >/dev/null

echo "[transaction-read-429-source] invalid input fails"
if SOURCE_429_FAIL_RATE=2 "${runner}" --print-plan >/dev/null 2>&1; then
  echo "invalid SOURCE_429_FAIL_RATE unexpectedly succeeded" >&2
  exit 1
fi
