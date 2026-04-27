# t3micro-defensive-gates-aggregate-latest-main-gate-retest-20260427

## Inputs

- capacityResult: docs/performance-results/docker-t3micro-capacity-latest-main-gate-retest-20260427.md
- sseResult: docs/performance-results/sse-reconnect-latest-main-gate-retest-20260427-clients-3.md
- admissionSummary: build/reports/admission/admission-latest-main-gate-retest-20260427-ipv4-escalated/http-admission-summary.tsv
- outboxSummary: missing

## Gate Summary

| Gate | Status | Peak CPU % | Peak Memory MiB | Backlog / Reject Signal | Source |
| --- | --- | ---: | ---: | --- | --- |
| capacity | 0 | missing | missing | repeat=1 | docs/performance-results/docker-t3micro-capacity-latest-main-gate-retest-20260427.md |
| sse reconnect | 0 | missing | missing | clients=3 rounds=2 | docs/performance-results/sse-reconnect-latest-main-gate-retest-20260427-clients-3.md |
| http admission | n/a | n/a | n/a | rejected=9 failed_rate=0.000000 | build/reports/admission/admission-latest-main-gate-retest-20260427-ipv4-escalated/http-admission-summary.tsv |
| outbox backlog | n/a | n/a | n/a | lag=missing failed=missing dlq=missing | missing |

## Notes

- missing 값은 해당 gate가 아직 같은 aggregate run에 연결되지 않았음을 뜻합니다.
- token, 운영 URL, raw Authorization header는 기록하지 않습니다.
