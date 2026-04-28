# t3micro-defensive-post-observability-20260428

## Inputs

- capacityResult: missing
- capacitySummary: missing
- capacityRunContext: missing
- sseResult: missing
- admissionSummary: missing
- outboxSummary: missing
- k6Summary: docs/performance-results/k6-smoke/transaction-100m-post-observability-20260428-vu16-overload-summary.md
- memorySummary: build/reports/t3micro/post-observability-20260428-memory-summary.tsv

## Gate Summary

| Gate | Status | Peak CPU % | Peak Memory MiB | Backlog / Reject Signal | Source |
| --- | --- | ---: | ---: | --- | --- |
| capacity smoke | missing | missing | missing | repeat=missing | missing |
| capacity | missing | missing | missing | repeat=missing | missing |
| capacity soak | missing | n/a | n/a | 429Rate=n/a hikariPending=n/a profiles=missing | missing |
| sse reconnect | missing | missing | missing | clients=missing rounds=missing | missing |
| http admission | n/a | n/a | n/a | rejected=missing failed_rate=missing | missing |
| outbox backlog | n/a | n/a | n/a | lag=missing failed=missing dlq=missing | missing |
| k6 transaction 100m | n/a | n/a | n/a | hotFirstP95=1.3960875999999989 hotCursorP95=1.3451997999999996 coldFirstP95=1.3145 coldCursorP95=1.2804584999999997 429Rate=0.0042366897330885465 | docs/performance-results/k6-smoke/transaction-100m-post-observability-20260428-vu16-overload-summary.md |
| total memory | pass | n/a | 645.49 | budget=900 source=build/reports/t3micro/post-observability-20260428-memory-summary.tsv | build/reports/t3micro/post-observability-20260428-memory-summary.tsv |

## Notes

- missing 값은 해당 gate가 아직 같은 aggregate run에 연결되지 않았음을 뜻합니다.
- token, 운영 URL, raw Authorization header는 기록하지 않습니다.
