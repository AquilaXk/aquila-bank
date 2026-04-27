# t3micro-defensive-gates-post-gate-reliability-20260427

## Inputs

- capacityResult: missing
- sseResult: missing
- admissionSummary: missing
- outboxSummary: missing
- k6Summary: docs/performance-results/transaction-100m-post-gate-reliability-vu3-20260427-summary.md
- memorySummary: build/reports/t3micro/post-gate-reliability-memory-summary.tsv

## Gate Summary

| Gate | Status | Peak CPU % | Peak Memory MiB | Backlog / Reject Signal | Source |
| --- | --- | ---: | ---: | --- | --- |
| capacity | missing | missing | missing | repeat=missing | missing |
| sse reconnect | missing | missing | missing | clients=missing rounds=missing | missing |
| http admission | n/a | n/a | n/a | rejected=missing failed_rate=missing | missing |
| outbox backlog | n/a | n/a | n/a | lag=missing failed=missing dlq=missing | missing |
| k6 transaction 100m | n/a | n/a | n/a | hotFirstP95=3.1063249999999982 hotCursorP95=3.3210085999999985 coldFirstP95=3.153784 coldCursorP95=3.310249999999999 429Rate=0 | docs/performance-results/transaction-100m-post-gate-reliability-vu3-20260427-summary.md |
| total memory | pass | n/a | 761.08 | budget=900 source=build/reports/t3micro/post-gate-reliability-memory-summary.tsv | build/reports/t3micro/post-gate-reliability-memory-summary.tsv |

## Notes

- missing 값은 해당 gate가 아직 같은 aggregate run에 연결되지 않았음을 뜻합니다.
- token, 운영 URL, raw Authorization header는 기록하지 않습니다.
