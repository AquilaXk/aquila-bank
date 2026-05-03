#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE' >&2
usage: tools/test/run-transaction-read-nginx-access-aggregate-artifact.sh [--print-plan]

Environment:
  NGINX_ACCESS_AGGREGATE_NAME       default transaction-read-nginx-access-aggregate-<timestamp>
  NGINX_ACCESS_AGGREGATE_LOG        required Nginx JSON access log
  NGINX_ACCESS_AGGREGATE_RUN_ID     optional k6 run id filter
  NGINX_ACCESS_AGGREGATE_OUTPUT_DIR default build/reports/k6/<artifact>
USAGE
}

mode="run"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --print-plan)
      mode="print-plan"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
  shift
done

name="${NGINX_ACCESS_AGGREGATE_NAME:-transaction-read-nginx-access-aggregate-$(date +%Y-%m-%d-%H%M%S)}"
access_log="${NGINX_ACCESS_AGGREGATE_LOG:-}"
run_id_filter="${NGINX_ACCESS_AGGREGATE_RUN_ID:-}"
output_dir="${NGINX_ACCESS_AGGREGATE_OUTPUT_DIR:-build/reports/k6/${name}}"
summary_tsv="${output_dir}/${name}-nginx-access-aggregate.tsv"
summary_json="${output_dir}/${name}-nginx-access-aggregate.json"
report_md="${output_dir}/${name}-nginx-access-aggregate.md"

print_plan() {
  echo "[transaction-read-nginx-access-aggregate] name=${name}"
  echo "[transaction-read-nginx-access-aggregate] access_log=${access_log:-missing}"
  echo "[transaction-read-nginx-access-aggregate] run_id_filter=${run_id_filter:-none}"
  echo "[transaction-read-nginx-access-aggregate] output_dir=${output_dir}"
  echo "[transaction-read-nginx-access-aggregate] aggregate_key=k6_run_id,status,limit_req_status,upstream_status,reject_source,upstream_reject_source,upstream_reject_reason"
  echo "[transaction-read-nginx-access-aggregate] summary_tsv=${summary_tsv}"
  echo "[transaction-read-nginx-access-aggregate] summary_json=${summary_json}"
  echo "[transaction-read-nginx-access-aggregate] report_md=${report_md}"
}

print_plan
if [[ "${mode}" == "print-plan" ]]; then
  if [[ -z "${access_log}" || ! -s "${access_log}" ]]; then
    echo "NGINX_ACCESS_AGGREGATE_LOG is required" >&2
    exit 1
  fi
  exit 0
fi

if [[ -z "${access_log}" || ! -s "${access_log}" ]]; then
  echo "NGINX_ACCESS_AGGREGATE_LOG is required" >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 1
fi

mkdir -p "${output_dir}"
{
  printf "k6_run_id\tstatus\tlimit_req_status\tupstream_status\treject_source\treject_reason\tupstream_reject_source\tupstream_reject_reason\tcount\tdelayed_count\trejected_count\trequest_p95_ms\tupstream_p95_ms\n"
  jq -r -s --arg run_id_filter "${run_id_filter}" '
    def clean($value; $fallback):
      (($value // $fallback) | tostring) as $text
      | if $text == "" then $fallback else $text end;
    def run_id: clean(.k6_run_id; "unknown");
    def status_text: clean(.status; "0");
    def limit_text: clean(.limit_req_status; "none");
    def upstream_text: clean(.upstream_status; "none");
    def reject_source_text: clean(.reject_source; "none");
    def reject_reason_text: clean(.reject_reason; "none");
    def upstream_reject_source_text: clean(.upstream_reject_source; "none");
    def upstream_reject_reason_text: clean(.upstream_reject_reason; "none");
    def request_ms: ((.request_time | tonumber? // 0) * 1000);
    def upstream_ms: ((.upstream_response_time | tonumber? // 0) * 1000);
    def p95:
      sort as $values
      | if ($values | length) == 0 then 0
        else $values[((($values | length) - 1) * 0.95 | floor)]
        end;
    map(select((.request // "") | contains("/api/v1/transactions")))
    | map(select(($run_id_filter == "") or (run_id == $run_id_filter)))
    | sort_by(
        run_id,
        status_text,
        limit_text,
        upstream_text,
        reject_source_text,
        reject_reason_text,
        upstream_reject_source_text,
        upstream_reject_reason_text
      )
    | group_by([
        run_id,
        status_text,
        limit_text,
        upstream_text,
        reject_source_text,
        reject_reason_text,
        upstream_reject_source_text,
        upstream_reject_reason_text
      ])
    | .[]
    | {
        run: (.[0] | run_id),
        status: (.[0] | status_text),
        limit: (.[0] | limit_text),
        upstream: (.[0] | upstream_text),
        reject_source: (.[0] | reject_source_text),
        reject_reason: (.[0] | reject_reason_text),
        upstream_reject_source: (.[0] | upstream_reject_source_text),
        upstream_reject_reason: (.[0] | upstream_reject_reason_text),
        count: length,
        delayed: ([.[] | select(limit_text == "DELAYED")] | length),
        rejected: ([.[] | select((status_text == "429") or (limit_text == "REJECTED"))] | length),
        request_p95: ([.[] | request_ms] | p95),
        upstream_p95: ([.[] | upstream_ms] | p95)
      }
    | [
        .run,
        .status,
        .limit,
        .upstream,
        .reject_source,
        .reject_reason,
        .upstream_reject_source,
        .upstream_reject_reason,
        .count,
        .delayed,
        .rejected,
        .request_p95,
        .upstream_p95
      ]
    | @tsv
  ' "${access_log}" | awk -F '\t' 'BEGIN { OFS = FS } { printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%.3f\t%.3f\n", $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13 }'
} >"${summary_tsv}"

jq -Rn '
  def number_or_string:
    if test("^-?[0-9]+([.][0-9]+)?$") then tonumber else . end;
  (input | split("\t")) as $headers
  | [
      inputs
      | split("\t") as $row
      | reduce range(0; $headers | length) as $i (
          {};
          .[$headers[$i]] = (($row[$i] // "") | number_or_string)
        )
    ]
' <"${summary_tsv}" >"${summary_json}"

transaction_read_rows="$(awk -F '\t' 'NR > 1 { count += $9 } END { print count + 0 }' "${summary_tsv}")"
delayed_count="$(awk -F '\t' 'NR > 1 { count += $10 } END { print count + 0 }' "${summary_tsv}")"
rejected_count="$(awk -F '\t' 'NR > 1 { count += $11 } END { print count + 0 }' "${summary_tsv}")"
status_499_count="$(awk -F '\t' 'NR > 1 && $2 == "499" { count += $9 } END { print count + 0 }' "${summary_tsv}")"
status_5xx_count="$(awk -F '\t' 'NR > 1 && $2 ~ /^5/ { count += $9 } END { print count + 0 }' "${summary_tsv}")"
delayed_ratio="$(awk -v delayed="${delayed_count}" -v total="${transaction_read_rows}" 'BEGIN {
  if (total == 0) printf "0.000000"; else printf "%.6f", delayed / total
}')"

if [[ "${transaction_read_rows}" == "0" ]]; then
  echo "transaction read rows are required in Nginx access log" >&2
  exit 1
fi

aggregate_table="$(awk -F '\t' '
  BEGIN {
    print "| k6 run id | Status | limit_req_status | upstream_status | reject_source | reject_reason | upstream_reject_source | upstream_reject_reason | Count | Delayed | Rejected | Request p95 ms | Upstream p95 ms |"
    print "| --- | --- | --- | --- | --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: |"
  }
  NR > 1 {
    printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13
  }
' "${summary_tsv}")"

cat >"${report_md}" <<REPORT
# Transaction Read Nginx Access Aggregate Artifact

## Summary

- aggregate key: k6_run_id/status/limit_req_status/upstream_status/reject_source/upstream_reject_source/upstream_reject_reason
- run_id_filter=${run_id_filter:-none}
- transaction_read_rows=${transaction_read_rows}
- delayed count: ${delayed_count}
- delayed ratio: ${delayed_ratio}
- rejected count: ${rejected_count}
- 499 count: ${status_499_count}
- 5xx count: ${status_5xx_count}

## Aggregate

${aggregate_table}

## Contract Notes

- Nginx JSON access log는 k6 run id, HTTP status, limit_req_status, upstream_status, upstream reject source/reason을 같은 key로 집계한다.
- 이 artifact는 429/499/5xx를 실패로 직접 판정하지 않고, edge delay/reject와 upstream 결과를 빠르게 분리하는 표준 산출물이다.
- budget 판정은 이 TSV를 OCI evidence gate 또는 PR 본문에서 참조해 수행한다.

## Artifacts

- summary TSV: ${summary_tsv}
- summary JSON: ${summary_json}
- access log: ${access_log}
REPORT

echo "${report_md}"
