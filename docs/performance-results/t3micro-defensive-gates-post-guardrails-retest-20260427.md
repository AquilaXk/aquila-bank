# t3micro-defensive-gates-post-guardrails-retest-20260427

## Inputs

- capacityResult: missing
- sseResult: missing
- admissionSummary: missing
- outboxSummary: missing
- k6Summary: docs/performance-results/transaction-100m-post-guardrails-retest-vu16-overload-20260427-summary.md

## Gate Summary

| Gate | Status | Peak CPU % | Peak Memory MiB | Backlog / Reject Signal | Source |
| --- | --- | ---: | ---: | --- | --- |
| capacity | missing | missing | missing | repeat=missing | missing |
| sse reconnect | missing | missing | missing | clients=missing rounds=missing | missing |
| http admission | n/a | n/a | n/a | rejected=missing failed_rate=missing | missing |
| outbox backlog | n/a | n/a | n/a | lag=missing failed=missing dlq=missing | missing |
| k6 transaction 100m | n/a | n/a | n/a | hotFirstP95=3.1128830999999995 hotCursorP95=3.1466266999999997 coldFirstP95=2.973924899999999 coldCursorP95=3.0614669999999986 429Rate=0.007297904191616767 | docs/performance-results/transaction-100m-post-guardrails-retest-vu16-overload-20260427-summary.md |

## Notes

- missing 값은 해당 gate가 아직 같은 aggregate run에 연결되지 않았음을 뜻합니다.
- token, 운영 URL, raw Authorization header는 기록하지 않습니다.
