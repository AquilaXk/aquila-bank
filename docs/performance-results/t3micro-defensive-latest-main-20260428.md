# t3micro-defensive-latest-main-20260428

## Inputs

- capacityResult: missing
- capacitySummary: missing
- capacityRunContext: missing
- sseResult: missing
- admissionSummary: missing
- outboxSummary: missing
- k6Summary: docs/performance-results/k6-smoke/transaction-100m-latest-main-20260428-vu16-overload-summary.md
- memorySummary: build/reports/t3micro/latest-main-20260428-memory-summary.tsv

## Gate Summary

| Gate | Status | Peak CPU % | Peak Memory MiB | Backlog / Reject Signal | Source |
| --- | --- | ---: | ---: | --- | --- |
| capacity | missing | missing | missing | repeat=missing | missing |
| sse reconnect | missing | missing | missing | clients=missing rounds=missing | missing |
| http admission | n/a | n/a | n/a | rejected=missing failed_rate=missing | missing |
| outbox backlog | n/a | n/a | n/a | lag=missing failed=missing dlq=missing | missing |
| k6 transaction 100m | n/a | n/a | n/a | hotFirstP95=2.3940358999999996 hotCursorP95=2.2502080999999996 coldFirstP95=2.1458436999999986 coldCursorP95=2.264982899999999 429Rate=0.005702337958563011 | docs/performance-results/k6-smoke/transaction-100m-latest-main-20260428-vu16-overload-summary.md |
| total memory | pass | n/a | 644.45 | budget=900 source=build/reports/t3micro/latest-main-20260428-memory-summary.tsv | build/reports/t3micro/latest-main-20260428-memory-summary.tsv |

## Notes

- missing 값은 해당 gate가 아직 같은 aggregate run에 연결되지 않았음을 뜻합니다.
- token, 운영 URL, raw Authorization header는 기록하지 않습니다.
