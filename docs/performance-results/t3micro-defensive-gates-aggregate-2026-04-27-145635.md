# t3micro-defensive-gates-aggregate-2026-04-27-145635

## Inputs

- capacityResult: missing
- sseResult: missing
- admissionSummary: missing
- outboxSummary: missing
- k6Summary: docs/performance-results/transaction-100m-post-capacity-retest-vu16-overload-20260427-summary.md

## Gate Summary

| Gate | Status | Peak CPU % | Peak Memory MiB | Backlog / Reject Signal | Source |
| --- | --- | ---: | ---: | --- | --- |
| capacity | missing | missing | missing | repeat=missing | missing |
| sse reconnect | missing | missing | missing | clients=missing rounds=missing | missing |
| http admission | n/a | n/a | n/a | rejected=missing failed_rate=missing | missing |
| outbox backlog | n/a | n/a | n/a | lag=missing failed=missing dlq=missing | missing |
| k6 transaction 100m | n/a | n/a | n/a | hotFirstP95=2.308443749999999 hotCursorP95=2.217042 coldFirstP95=2.1418578 coldCursorP95=2.159458 429Rate=0.005822025169062654 | docs/performance-results/transaction-100m-post-capacity-retest-vu16-overload-20260427-summary.md |

## Notes

- missing 값은 해당 gate가 아직 같은 aggregate run에 연결되지 않았음을 뜻합니다.
- token, 운영 URL, raw Authorization header는 기록하지 않습니다.
