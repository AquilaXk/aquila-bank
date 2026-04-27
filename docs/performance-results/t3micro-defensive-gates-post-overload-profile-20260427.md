# t3micro-defensive-gates-post-overload-profile-20260427

## Inputs

- capacityResult: missing
- sseResult: missing
- admissionSummary: missing
- outboxSummary: missing
- k6Summary: docs/performance-results/transaction-100m-post-overload-profile-vu16-overload-20260427-summary.md
- memorySummary: build/reports/t3micro/post-overload-profile-memory-summary.tsv

## Gate Summary

| Gate | Status | Peak CPU % | Peak Memory MiB | Backlog / Reject Signal | Source |
| --- | --- | ---: | ---: | --- | --- |
| capacity | missing | missing | missing | repeat=missing | missing |
| sse reconnect | missing | missing | missing | clients=missing rounds=missing | missing |
| http admission | n/a | n/a | n/a | rejected=missing failed_rate=missing | missing |
| outbox backlog | n/a | n/a | n/a | lag=missing failed=missing dlq=missing | missing |
| k6 transaction 100m | n/a | n/a | n/a | hotFirstP95=1.2469707999999997 hotCursorP95=1.2110400999999988 coldFirstP95=1.1627785499999999 coldCursorP95=1.169051499999999 429Rate=0.003959109504908281 | docs/performance-results/transaction-100m-post-overload-profile-vu16-overload-20260427-summary.md |
| total memory | pass | n/a | 743.13 | budget=900 source=build/reports/t3micro/post-overload-profile-memory-summary.tsv | build/reports/t3micro/post-overload-profile-memory-summary.tsv |

## Notes

- missing 값은 해당 gate가 아직 같은 aggregate run에 연결되지 않았음을 뜻합니다.
- token, 운영 URL, raw Authorization header는 기록하지 않습니다.
