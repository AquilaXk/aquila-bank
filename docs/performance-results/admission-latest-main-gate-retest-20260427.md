# admission-latest-main-gate-retest-20260427

## Admission Guard Telemetry

- archivedAt: 2026-04-27T01:17:57Z
- sourceSummary: build/reports/admission/admission-latest-main-gate-retest-20260427-ipv4-escalated/http-admission-summary.tsv
- sourceRaw: build/reports/admission/admission-latest-main-gate-retest-20260427-ipv4-escalated/http-admission-raw.tsv
- secretPolicy: token, Authorization header, 운영 URL은 기록하지 않음

## Summary

```tsv
requests	success_count	rejected_count	failed_count	failed_rate	retry_after_count	raw_path
16	7	9	0	0.000000	9	build/reports/admission/admission-latest-main-gate-retest-20260427-ipv4-escalated/http-admission-raw.tsv
```

## Raw Sample

```tsv
request_id	status	duration_seconds	retry_after
1	429	0.158339	1
2	200	0.159824
3	429	0.157598	1
4	429	0.158753	1
5	200	0.157690
6	200	0.159308
7	429	0.160654	1
8	429	0.159706	1
9	429	0.013049	1
10	200	0.015246
11	200	0.017027
12	200	0.015886
13	429	0.013844	1
14	429	0.013992	1
15	429	0.012333	1
16	200	0.025564
```
