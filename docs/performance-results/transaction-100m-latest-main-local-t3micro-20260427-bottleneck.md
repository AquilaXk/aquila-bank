# Transaction 100m latest-main local t3.micro bottleneck 20260427

## Scope

- date: 2026-04-27 KST
- issue: #423
- branch: `perf/transaction-100m-latest-main-loadtest-report`
- base main sha: `5304ff48759af4404635d1039405d90bb347c2b1`
- source runners:
  - `tools/test/run-k6-transaction-100m-loadtest.sh`
  - `tools/test/run-transaction-100m-read-write-interference-gate.sh`
- raw artifacts:
  - `build/reports/k6/transaction-100m-latest-main-vu16-overload-20260427-summary.{md,json}`
  - `build/reports/k6/transaction-100m-latest-main-read-write-kafka768-20260427/**`
- archived summary:
  - `docs/performance-results/transaction-100m-latest-main-vu16-overload-20260427-summary.md`

## Latest main baseline

Latest completed work already merged into this baseline:

| PR | scope | status in this candidate list |
| --- | --- | --- |
| #414 | Kafka loadtest image tag and interference fixture bootstrap/read overload script 보정 | exclude |
| #416 | adaptive strict runner fail-fast guard | exclude |
| #418 | transaction read p99/max SLO gate and page limit sensitivity benchmark | exclude |
| #421 | adaptive/remote/interference results report | exclude |
| #422 | read/write interference fixture cleanup 보정 | exclude |

This report does not reopen those completed scopes. New candidates below are limited to bottlenecks still visible after `5304ff4`.

## Environment

- host Docker contexts: `default`, `desktop-linux`
- remote k6 env: no `K6_DOCKER_CONTEXT`, `K6_REMOTE_BASE_URL`, `K6_REMOTE_PROMETHEUS_RW_SERVER_URL`, `K6_REMOTE_WORKDIR`
- PostgreSQL: `PostgreSQL 18.3`
- fixture estimate:
  - `transaction_read_model`: `50,000,000`
  - `transaction_read_model_archive`: `50,000,028`
- required cursor indexes present:
  - `idx_transaction_read_model_account_cursor`
  - `idx_transaction_read_model_archive_account_cursor`
- read-only observed resource snapshot:
  - backend: `80.74% CPU`, `388MiB / 640MiB`
  - PostgreSQL: `50.88% CPU`, `90.9MiB / 384MiB`
  - Prometheus: `0.23% CPU`, `94.45MiB`

## Preflight observations

- `GET /actuator/health` returned `503` before read-only k6 because `outbox` health reported `DOWN` with `java.lang.IllegalArgumentException: 'value' must not be null`.
- The read-only runner was executed with `--no-up --no-deps`, so the overall health `DOWN` did not block transaction read measurement.
- The interference runner checks `readinessState=UP`, not overall health, so Kafka/outbox warm-up is separated from readiness where possible.

## Read-only 100m query load

Command:

```bash
K6_HOT_ACCOUNT_ID=910000001 \
K6_HOT_FROM=2026-04-01T00:00:00Z \
K6_HOT_TO=2026-04-30T00:00:00Z \
K6_COLD_ACCOUNT_ID=910000002 \
K6_COLD_FROM=2026-01-01T00:00:00Z \
K6_COLD_TO=2026-01-31T00:00:00Z \
K6_REPORT_NAME=transaction-100m-latest-main-vu16-overload-20260427 \
K6_VUS=16 \
K6_DURATION=1m \
K6_OVERLOAD_MODE=true \
K6_OVERLOAD_429_RATE_THRESHOLD=0.20 \
tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps
```

Result:

| signal | value |
| --- | ---: |
| runner status | 0 |
| completed iterations | 37,638 |
| http failed / transaction 429 rate | 0.005232686850526288 |
| checks rate | 1 |
| hot first p95 | 1.49ms |
| hot cursor p95 | 1.38ms |
| cold first p95 | 1.33ms |
| cold cursor p95 | 1.35ms |
| hot first max | 227.85ms |
| hot cursor max | 195.10ms |
| cold first max | 84.64ms |
| cold cursor max | 86.86ms |

Interpretation:

- Latest main read-only hot/cold 1억 건 조회 path is not the current bottleneck under this local VU16 overload run.
- backend and PostgreSQL both consumed meaningful CPU, but latency stayed far below the configured p95 and max thresholds.
- The remaining performance risk is no longer basic cursor/index fit. It is mixed write/async/infra budget and tail behavior.

## Read/write interference with default Kafka budget

Command:

```bash
K6_HOT_ACCOUNT_ID=910000001 \
K6_HOT_FROM=2026-04-01T00:00:00Z \
K6_HOT_TO=2026-04-30T00:00:00Z \
K6_COLD_ACCOUNT_ID=910000002 \
K6_COLD_FROM=2026-01-01T00:00:00Z \
K6_COLD_TO=2026-01-31T00:00:00Z \
INTERFERENCE_NAME=transaction-100m-latest-main-read-write-default-kafka-20260427 \
INTERFERENCE_DURATION=1m \
INTERFERENCE_READ_VUS=8 \
INTERFERENCE_WRITE_VUS=2 \
INTERFERENCE_READ_OVERLOAD_MODE=true \
INTERFERENCE_READ_429_RATE_THRESHOLD=0.20 \
tools/test/run-transaction-100m-read-write-interference-gate.sh
```

Result:

| signal | value |
| --- | --- |
| runner status | 1 |
| failure point | before k6 |
| error | `backend readiness timeout: http://localhost:8080/actuator/health` |
| Kafka budget | `448m`, heap `-Xms128m -Xmx256m` |
| Kafka state after failure | `Up Less than a second (health: starting)` |
| Kafka restart count after failure | `7` |
| backend log symptom | repeated Kafka AdminClient `fetchMetadata` timeout |
| fixture cleanup | 0 rows, completed |

Interpretation:

- The default local Kafka memory budget still cannot make the mixed read/write path measurable.
- This is not the fixed Kafka image tag issue from #414. The image starts, but broker stability under the current budget blocks backend readiness.

## Read/write interference with Kafka 768MiB

Command:

```bash
T3MICRO_KAFKA_MEMORY=768m \
T3MICRO_KAFKA_MEMORY_SWAP=768m \
T3MICRO_KAFKA_HEAP_OPTS='-Xms128m -Xmx256m' \
K6_HOT_ACCOUNT_ID=910000001 \
K6_HOT_FROM=2026-04-01T00:00:00Z \
K6_HOT_TO=2026-04-30T00:00:00Z \
K6_COLD_ACCOUNT_ID=910000002 \
K6_COLD_FROM=2026-01-01T00:00:00Z \
K6_COLD_TO=2026-01-31T00:00:00Z \
INTERFERENCE_NAME=transaction-100m-latest-main-read-write-kafka768-20260427 \
INTERFERENCE_DURATION=1m \
INTERFERENCE_READ_VUS=8 \
INTERFERENCE_WRITE_VUS=2 \
INTERFERENCE_READ_OVERLOAD_MODE=true \
INTERFERENCE_READ_429_RATE_THRESHOLD=0.20 \
INTERFERENCE_READINESS_TIMEOUT_SECONDS=360 \
tools/test/run-transaction-100m-read-write-interference-gate.sh
```

Summary was written before cleanup failure:

| signal | value |
| --- | ---: |
| read status | 0 |
| write status | 0 |
| read http reqs | 40,264 |
| read 429 rate | 0.007326644148619114 |
| read hot first p95 | 8.96ms |
| read hot cursor p95 | 7.87ms |
| read cold first p95 | 7.43ms |
| read cold cursor p95 | 8.48ms |
| write http reqs | 8,119 |
| write 429 rate | 0.04434043601428747 |
| write success rate | 0.9556595639857125 |
| write duration p95 | 72.40ms |
| runner final status | 1 |
| final failure point | post-run fixture cleanup |

Failure evidence:

- cleanup query connection closed with `server closed the connection unexpectedly`.
- PostgreSQL log: `client backend (PID 163) was terminated by signal 9: Killed` while running the interference fixture cleanup transaction.
- PostgreSQL then entered automatic recovery and the startup process was also killed by signal 9.
- PostgreSQL inspect after failure: `RestartCount=3`, `OOMKilled=false`, `ExitCode=0`.
- Services were stopped with `docker compose ... --profile loadtest down` after evidence capture; volumes were not deleted.

Interpretation:

- Kafka 768MiB makes the actual 1-minute mixed k6 workload pass at the read/write metric level.
- The runner is still not reliable because cleanup can terminate PostgreSQL under the `384MiB` Postgres budget and force recovery.
- This is a distinct bottleneck from #422. #422 prevents stale fixture rows after normal cleanup, but it does not make large cleanup transactions recovery-safe under t3.micro memory pressure.

## Current bottleneck order

1. Kafka 448MiB budget blocks measurable mixed read/write runs before k6.
2. Kafka 768MiB allows k6 to pass, but PostgreSQL cleanup/recovery is not stable under the 384MiB budget.
3. Outbox/async backlog is still unresolved from the latest successful 5-minute run in #421: 294s max-over-time and 344s current lag.
4. Remote k6 is still not configured, so local client CPU and server CPU are not fully separated.
5. Adaptive capacity still lacks admission-limit time-series evidence for policy tuning.
6. Previous max-tail and backend CPU-budget failures remain open because #418/#421 measured them but did not change runtime policy.

## Next Issue/PR Candidates

All items are intended as `issue title = PR title`, one issue per PR. Completed scopes from #414, #416, #418, #421, and #422 are intentionally excluded.

| order | issue/PR title | template | branch | reason |
| ---: | --- | --- | --- | --- |
| 1 | `[Perf] Kafka t3.micro mixed workload memory budget 확정` | `performance_request.yml` | `perf/kafka-t3micro-mixed-memory-budget` | Default `448m` repeatedly blocks backend readiness; `768m` works for k6 but no longer fits a strict single t3.micro interpretation. |
| 2 | `[Perf] transaction read/write Kafka off-host profile 추가` | `performance_request.yml` | `perf/transaction-read-write-kafka-offhost-profile` | If Kafka needs 768MiB, mixed workload should be measured with Kafka separated from backend/PostgreSQL before claiming t3.micro app-node capacity. |
| 3 | `[Perf] interference fixture cleanup chunking 및 timeout guard 추가` | `performance_request.yml` | `perf/interference-fixture-cleanup-chunking` | Latest run killed PostgreSQL during one large cleanup transaction; cleanup must be bounded, chunked, and fail-fast. |
| 4 | `[Perf] PostgreSQL t3.micro recovery/WAL budget gate 추가` | `performance_request.yml` | `perf/postgres-t3micro-recovery-wal-gate` | Cleanup failure triggered recovery and repeated signal 9; loadtest gate should detect WAL/recovery risk instead of corrupting the next run. |
| 5 | `[Perf] outbox dispatch lag SLO 및 drain 튜닝` | `performance_request.yml` | `perf/outbox-dispatch-lag-slo-drain` | #421 showed 294s/344s dispatch lag with DLQ 0, so async backlog is the next real user-impact risk after read path latency. |
| 6 | `[Perf] outbox dispatcher batch/concurrency matrix benchmark 추가` | `performance_request.yml` | `perf/outbox-dispatch-batch-concurrency-matrix` | Need measured batch size/concurrency tradeoff before changing dispatcher defaults on t3.micro. |
| 7 | `[Perf] transaction remote k6 runner 환경 연결` | `performance_request.yml` | `perf/transaction-remote-k6-runner-env` | Remote generator code exists, but this host has no remote Docker context/env, so client CPU and server CPU remain coupled. |
| 8 | `[Perf] transaction adaptive admission limit time-series export 추가` | `performance_request.yml` | `perf/transaction-adaptive-limit-timeseries` | Current reports only include end summaries; policy tuning needs admission limit changes over time. |
| 9 | `[Perf] transaction high-traffic max latency spike 프로파일링` | `performance_request.yml` | `perf/transaction-high-traffic-max-latency-profile` | #421 `single-host-high-traffic` passed p95 but failed max latency at 4507ms; tail spike root cause is still unknown. |
| 10 | `[Perf] transaction backend CPU budget profile 재정의` | `performance_request.yml` | `perf/transaction-backend-cpu-budget-profile` | #421 showed `cpu-backend040-postgres060` fails while PostgreSQL CPU stays low; backend CPU profile needs an explicit SLO/support decision. |
| 11 | `[Fix] outbox health null-safe 및 readiness 영향 분리` | `bug_report.yml` | `fix/outbox-health-null-readiness` | `GET /actuator/health` returned 503 due null outbox health value before read-only load; ops health should not hide healthy read path readiness. |
| 12 | `[Perf] mixed workload post-run metrics snapshot 자동 아카이브` | `performance_request.yml` | `perf/mixed-workload-metrics-snapshot-archive` | The runner wrote k6 TSV but failed before lag/cleanup metrics could be captured; post-run snapshots should be persisted before destructive cleanup. |

## Verification

- `git pull --ff-only origin main`
- `docker context ls`
- `docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml ps`
- `docker exec aquila-bank-postgres psql -U postgres -d aquila_bank -v ON_ERROR_STOP=1 -c "select current_database(), version();"`
- `docker exec aquila-bank-postgres psql -U postgres -d aquila_bank -v ON_ERROR_STOP=1 -c "select relname, reltuples::bigint as estimated_rows from pg_class where relname in ('transaction_read_model','transaction_read_model_archive') order by relname;"`
- `K6_HOT_ACCOUNT_ID=910000001 K6_HOT_FROM=2026-04-01T00:00:00Z K6_HOT_TO=2026-04-30T00:00:00Z K6_COLD_ACCOUNT_ID=910000002 K6_COLD_FROM=2026-01-01T00:00:00Z K6_COLD_TO=2026-01-31T00:00:00Z K6_REPORT_NAME=transaction-100m-latest-main-vu16-overload-20260427 K6_VUS=16 K6_DURATION=1m K6_OVERLOAD_MODE=true K6_OVERLOAD_429_RATE_THRESHOLD=0.20 tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps`
- `K6_HOT_ACCOUNT_ID=910000001 K6_HOT_FROM=2026-04-01T00:00:00Z K6_HOT_TO=2026-04-30T00:00:00Z K6_COLD_ACCOUNT_ID=910000002 K6_COLD_FROM=2026-01-01T00:00:00Z K6_COLD_TO=2026-01-31T00:00:00Z INTERFERENCE_NAME=transaction-100m-latest-main-read-write-default-kafka-20260427 INTERFERENCE_DURATION=1m INTERFERENCE_READ_VUS=8 INTERFERENCE_WRITE_VUS=2 INTERFERENCE_READ_OVERLOAD_MODE=true INTERFERENCE_READ_429_RATE_THRESHOLD=0.20 tools/test/run-transaction-100m-read-write-interference-gate.sh`
- `T3MICRO_KAFKA_MEMORY=768m T3MICRO_KAFKA_MEMORY_SWAP=768m T3MICRO_KAFKA_HEAP_OPTS='-Xms128m -Xmx256m' K6_HOT_ACCOUNT_ID=910000001 K6_HOT_FROM=2026-04-01T00:00:00Z K6_HOT_TO=2026-04-30T00:00:00Z K6_COLD_ACCOUNT_ID=910000002 K6_COLD_FROM=2026-01-01T00:00:00Z K6_COLD_TO=2026-01-31T00:00:00Z INTERFERENCE_NAME=transaction-100m-latest-main-read-write-kafka768-20260427 INTERFERENCE_DURATION=1m INTERFERENCE_READ_VUS=8 INTERFERENCE_WRITE_VUS=2 INTERFERENCE_READ_OVERLOAD_MODE=true INTERFERENCE_READ_429_RATE_THRESHOLD=0.20 INTERFERENCE_READINESS_TIMEOUT_SECONDS=360 tools/test/run-transaction-100m-read-write-interference-gate.sh`
- `docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml --profile loadtest down`
