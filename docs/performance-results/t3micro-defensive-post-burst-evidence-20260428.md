# t3micro-defensive-post-burst-evidence-20260428

## Inputs

- capacityResult: missing
- capacitySummary: missing
- capacityRunContext: missing
- capacityPrerequisite: build/reports/k6/transaction-100m-post-burst-evidence-20260428-capacity/capacity-prerequisite-failure.env
- sseResult: missing
- admissionSummary: missing
- outboxSummary: missing
- k6Summary: docs/performance-results/k6-smoke/transaction-100m-post-burst-evidence-20260428-vu3-summary.md
- memorySummary: build/reports/t3micro/post-burst-evidence-20260428-memory-summary.tsv

## Gate Summary

| Gate | Status | Peak CPU % | Peak Memory MiB | Backlog / Reject Signal | Source |
| --- | --- | ---: | ---: | --- | --- |
| capacity smoke | missing | missing | missing | repeat=missing | missing |
| capacity prerequisite | failed | n/a | n/a | reason=missing-required-env missing=CAPACITY_K6_DOCKER_CONTEXT,CAPACITY_K6_REMOTE_BASE_URL,CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL | build/reports/k6/transaction-100m-post-burst-evidence-20260428-capacity/capacity-prerequisite-failure.env |
| capacity | missing | missing | missing | repeat=missing | missing |
| capacity soak | missing | n/a | n/a | 429Rate=n/a hikariPending=n/a profiles=missing | missing |
| sse reconnect | missing | missing | missing | clients=missing rounds=missing | missing |
| http admission | n/a | n/a | n/a | rejected=missing failed_rate=missing | missing |
| outbox backlog | n/a | n/a | n/a | lag=missing failed=missing dlq=missing | missing |
| k6 transaction 100m | n/a | n/a | n/a | hotFirstP95=2.0797479 hotCursorP95=2.13706425 coldFirstP95=2.077472299999999 coldCursorP95=2.1074309499999995 429Rate=0 | docs/performance-results/k6-smoke/transaction-100m-post-burst-evidence-20260428-vu3-summary.md |
| total memory | pass | n/a | 663.61 | budget=900 source=build/reports/t3micro/post-burst-evidence-20260428-memory-summary.tsv | build/reports/t3micro/post-burst-evidence-20260428-memory-summary.tsv |

## Notes

- missing 값은 해당 gate가 아직 같은 aggregate run에 연결되지 않았음을 뜻합니다.
- token, 운영 URL, raw Authorization header는 기록하지 않습니다.
