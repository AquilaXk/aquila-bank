# Defensive runtime 100m latest-main bottleneck 20260427

## Scope

- date: 2026-04-27 KST
- issue: #429
- branch: `perf/defensive-runtime-100m-loadtest-report`
- base main sha: `24a28b32b1ee15be27af121f82359f5656b46562`
- runtime goal after latest main:
  - EC2 `t3.micro` app node
  - RDS `db.t4g.small` + gp3 database
  - bounded transaction query: `accountId + 기간 + keyset pagination`
  - overload defense by admission, small DB pool, small server thread pool, SSE cap, small worker batch
  - Kafka/Prometheus/Grafana are opt-in or loadtest assets, not same-host required runtime

## Latest main baseline

Latest completed work already merged into this baseline:

| PR | scope | status in this candidate list |
| --- | --- | --- |
| #424 | latest-main local 100m loadtest bottleneck report | exclude |
| #426 | t3.micro defensive runtime baseline | exclude |
| #428 | backend README defensive runtime sync | exclude |

This report does not reopen those completed scopes. New candidates below are limited to bottlenecks still visible after `24a28b3`.

## 100m fixture preflight

Commands:

```bash
docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml up -d postgres
FIXTURE_MODE=verify tools/test/run-transaction-100m-fixture-restore.sh
docker inspect aquila-bank-postgres --format '{{.State.Status}} {{.State.Restarting}} {{.State.OOMKilled}} {{.State.ExitCode}} {{.RestartCount}}'
docker stats --no-stream aquila-bank-postgres
docker logs --tail 160 aquila-bank-postgres
```

Observed:

| signal | value |
| --- | --- |
| fixture dump file | `build/fixtures/transaction-100m-fixture.dump` absent |
| postgres startup | existing database directory reused |
| fixture verify status | failed before query |
| verify error | `FATAL: the database system is not yet accepting connections` |
| recovery detail | `Consistent recovery state has not been yet reached` |
| restart count after wait | `3` |
| OOMKilled flag | `false` |
| recovery memory snapshot | `321.1MiB / 384MiB`, then `238.7MiB / 384MiB` |
| log evidence | startup process terminated by signal 9 during automatic recovery |

Interpretation:

- The previous real 100m PostgreSQL volume is not currently reusable under the local `384MiB` t3.micro Postgres budget.
- The bottleneck is before HTTP/k6: crash recovery repeatedly fails under the constrained Postgres container.
- Because no fixture dump artifact exists in `build/fixtures`, this run could not restore a clean 100m dataset without regenerating it.
- Running k6 against this state would only measure an unavailable database, not transaction query latency.

Cleanup:

```bash
docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml down
```

Volumes were not deleted.

## Defensive runtime capacity smoke

Command:

```bash
SOAK_REPEAT=1 tools/test/run-docker-t3micro-capacity-smoke.sh
```

Runtime budget:

| signal | value |
| --- | --- |
| Docker image | `eclipse-temurin:21-jdk` |
| Docker CPUs | `2` |
| Docker memory | `1024m` |
| Docker memory-swap | `1024m` |
| pids limit | `384` |
| DB pool cap | `4` |
| server threads cap | `16` |
| SSE total sessions cap | `64` |
| notification stream admission cap | `4` |
| saturation guard | enabled |

Workload:

- `TransactionQueryConcurrencySloIntegrationTest`
- `TransferCommandApiIntegrationTest`
- `NotificationSseBrokerTest`
- `NotificationSseIntegrationTest`

Result:

| signal | value |
| --- | --- |
| host `testClasses` | passed |
| Docker cgroup smoke | passed |
| Gradle test result | `BUILD SUCCESSFUL` |
| test task runtime | `8s` |

Interpretation:

- The small-runtime defensive Java/Spring path passes in a 1GiB Docker cgroup.
- This smoke does not replace real 100m HTTP/k6 measurement because it uses selected integration tests, not the 100m PostgreSQL volume.
- The result supports the new #426/#428 direction: bounded work and fail-fast guards are currently more reliable than trying to co-host Kafka/observability/100m recovery on a single local t3.micro-like container set.

## Production defensive config gate

Command:

```bash
tools/test/run-production-high-traffic-config-gate.sh
```

Result:

| guard | result |
| --- | --- |
| missing env fails | passed |
| valid defensive baseline passes | passed |
| transaction read admission above cap fails | passed |
| DB pool above cap fails | passed |
| SSE cap above cap fails | passed |
| worker batch above cap fails | passed |
| Redis throttling explicit config passes | passed |
| read replica enabled without env fails | passed |
| Kafka enabled without env fails | passed |
| Kafka explicit baseline passes | passed |
| unsafe Kafka replication fails | passed |
| consumer concurrency above DB pool fails | passed |

Interpretation:

- The production config gate now enforces the defensive target shape.
- Kafka/read replica are correctly optional: they fail only when enabled without safe configuration.
- The remaining gap is runtime evidence under real HTTP/DB load and recovery-safe 100m fixture handling.

## Current bottleneck order

1. 100m PostgreSQL fixture volume recovery is not stable under the local 384MiB Postgres budget.
2. No durable local 100m fixture dump/artifact exists, so a corrupted/crash volume blocks repeatable k6 runs.
3. The 100m read load runner lacks a fail-fast recovery-state gate before HTTP/k6.
4. Defensive runtime smoke passes, but it is not a real HTTP/k6 + 100m DB test.
5. The current local loadtest model still couples 100m fixture state to one mutable Docker volume.
6. AWS-like final target is EC2 `t3.micro` + RDS `db.t4g.small` + gp3, but local Docker only approximates CPU/memory and does not reproduce RDS recovery, gp3 latency, or CPU credit behavior.

## Next Issue/PR Candidates

All items are intended as `issue title = PR title`, one issue per PR. Completed scopes from #424, #426, and #428 are intentionally excluded.

| order | issue/PR title | template | branch | reason |
| ---: | --- | --- | --- | --- |
| 1 | `[Perf] 100m fixture restore artifact와 recovery-safe 재실행 경로 추가` | `performance_request.yml` | `perf/100m-fixture-restore-artifact` | `build/fixtures`에 dump가 없어 crash volume 후 100m 재실행이 막힌다. |
| 2 | `[Perf] PostgreSQL 100m crash recovery fail-fast gate 추가` | `performance_request.yml` | `perf/postgres-100m-recovery-failfast-gate` | 현재 runner는 DB recovery loop를 k6 전 명확히 닫지 못한다. |
| 3 | `[Perf] 100m read loadtest fresh-volume restore runner 추가` | `performance_request.yml` | `perf/100m-read-fresh-volume-runner` | mutable volume 재사용 대신 clean restore -> preflight -> k6 흐름이 필요하다. |
| 4 | `[Perf] 100m fixture cleanup chunking과 WAL budget 검증 추가` | `performance_request.yml` | `perf/100m-fixture-cleanup-wal-budget` | 이전 mixed run cleanup이 PostgreSQL signal 9/recovery를 유발했다. |
| 5 | `[Perf] transaction 100m k6 no-observability mode 추가` | `performance_request.yml` | `perf/transaction-100m-k6-no-observability` | 방어형 목표에서는 Prometheus/Grafana same-host 상시 운영이 제외되므로 summary-only 경량 k6 경로가 필요하다. |
| 6 | `[Perf] defensive runtime HTTP admission smoke 추가` | `performance_request.yml` | `perf/defensive-runtime-http-admission-smoke` | 현재 capacity smoke는 integration test 중심이라 실제 HTTP admission/backpressure를 재현하지 않는다. |
| 7 | `[Perf] t3.micro capacity smoke Markdown 결과 아카이브 추가` | `performance_request.yml` | `perf/t3micro-capacity-smoke-result-archive` | capacity smoke는 통과해도 `docs/performance-results`에 자동 결과가 남지 않는다. |
| 8 | `[Perf] 100m read staging RDS gp3 smoke 추가` | `performance_request.yml` | `perf/100m-read-staging-rds-gp3-smoke` | 최종 목표는 RDS `db.t4g.small` + gp3이며 Docker는 RDS recovery/IO/CPU credit을 재현하지 못한다. |
| 9 | `[Perf] SSE reconnect storm t3.micro cgroup gate 추가` | `performance_request.yml` | `perf/sse-reconnect-storm-t3micro-gate` | 작은 infra 방어 목표에서 SSE cap이 reconnect storm에도 유지되는지 cgroup 기반 확인이 필요하다. |
| 10 | `[Perf] outbox/provider worker small-batch HTTP backlog gate 추가` | `performance_request.yml` | `perf/outbox-provider-small-batch-backlog-gate` | Kafka 비필수 기준에서는 DB worker backlog와 작은 batch drain이 핵심 병목이다. |
| 11 | `[Perf] admission guard telemetry snapshot 자동 아카이브` | `performance_request.yml` | `perf/admission-guard-telemetry-archive` | 방어형 목표는 429/fail-fast가 정상 동작인지 수치로 남겨야 한다. |
| 12 | `[Perf] transaction read volume corruption runbook 추가` | `performance_request.yml` | `perf/transaction-read-volume-corruption-runbook` | 100m fixture volume이 recovery loop에 빠졌을 때 복구/폐기/재생성 기준이 없다. |

## Verification

- `git pull --ff-only origin main`
- `docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml ps`
- `docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml up -d postgres`
- `FIXTURE_MODE=verify tools/test/run-transaction-100m-fixture-restore.sh`
- `docker inspect aquila-bank-postgres --format '{{.State.Status}} {{.State.Restarting}} {{.State.OOMKilled}} {{.State.ExitCode}} {{.RestartCount}}'`
- `docker stats --no-stream aquila-bank-postgres`
- `docker logs --tail 160 aquila-bank-postgres`
- `docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml down`
- `SOAK_REPEAT=1 tools/test/run-docker-t3micro-capacity-smoke.sh`
- `tools/test/run-production-high-traffic-config-gate.sh`
