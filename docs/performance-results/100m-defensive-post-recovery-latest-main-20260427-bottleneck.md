# 100m defensive post-recovery latest-main bottleneck 20260427

## Scope

- date: 2026-04-27 KST
- issue: #439
- branch: `perf/100m-defensive-post-recovery-loadtest-report`
- base main sha: `4fd1a7d3773e65da1ec4ea659beef1095e88815e`
- runtime goal:
  - 로컬 Docker t3.micro 근사 환경에서 bounded 1억 건 조회와 대용량 트래픽 방어 상태를 재확인한다.
  - Kafka, Prometheus, Grafana는 same-host 필수 runtime이 아니라 opt-in/loadtest asset으로 본다.

## Latest main baseline

Latest completed work already merged into this baseline:

| PR | scope | status in this candidate list |
| --- | --- | --- |
| #435 | 100m fixture recovery-safe rerun, fresh-volume runner, cleanup WAL budget, corruption runbook | exclude |
| #436 | summary-only 100m k6, defensive HTTP admission smoke, admission telemetry archive | exclude |
| #437 | t3.micro capacity result archive, SSE reconnect t3.micro gate, outbox/provider backlog gate | exclude |
| #438 | staging RDS gp3 100m read smoke runner | exclude |

This report does not reopen those completed scopes. New candidates below are limited to gaps still visible after `4fd1a7d`.

## 100m fixture and fresh-volume preflight

Commands:

```bash
ls -lh build/fixtures
tools/test/run-transaction-100m-fresh-volume-restore-k6.sh --dry-run
FIXTURE_MODE=verify FIXTURE_REQUIRE_DUMP=true tools/test/run-transaction-100m-fixture-restore.sh
```

Observed:

| signal | value |
| --- | --- |
| `build/fixtures` | empty |
| expected dump | `build/fixtures/transaction-100m-fixture.dump` |
| fixture verify status | 1 |
| fixture verify error | `fixture dump not found: build/fixtures/transaction-100m-fixture.dump` |
| fresh-volume runner destructive step | `docker volume rm aquila-bank-postgres-data` appears before restore |
| actual fresh-volume run | intentionally not executed |

Interpretation:

- #435 added the recovery-safe runner path, but the required actual 100m dump artifact is not present on this machine.
- Running the fresh-volume runner with `FRESH_VOLUME_CONFIRM=erase-postgres-volume` would delete the local Postgres volume before restore, then fail because the dump is missing.
- Therefore actual 100m k6 could not be run safely in this pass. The blocker is now dataset artifact supply and destructive-runner guard ordering, not the transaction read SQL path itself.

## Defensive capacity smoke

Command:

```bash
DOCKER_T3MICRO_RESULT_NAME=docker-t3micro-capacity-latest-main-20260427 \
SOAK_REPEAT=1 \
tools/test/run-docker-t3micro-capacity-smoke.sh
```

Archived result:

- `docs/performance-results/docker-t3micro-capacity-latest-main-20260427.md`

Result:

| signal | value |
| --- | --- |
| status | 0 |
| Docker CPUs | 2 |
| Docker memory | 1024m |
| Docker memory-swap | 1024m |
| pids limit | 384 |
| repeat | 1 |
| DB pool max | 4 |
| server threads max | 16 |
| SSE max total sessions | 64 |
| notification stream max | 4 |
| Gradle test result | `BUILD SUCCESSFUL` |
| test task runtime | `7s` |

Interpretation:

- Defensive Java/Spring mixed workload still passes in a 1GiB Docker cgroup.
- This confirms the small-runtime guard direction, but it is not a substitute for actual 100m HTTP/k6 because no 100m DB fixture was available.

## SSE reconnect t3.micro gate

Command:

```bash
SSE_T3MICRO_RESULT_NAME=sse-reconnect-t3micro-latest-main-20260427 \
tools/test/run-sse-reconnect-storm-t3micro-gate.sh
```

Archived result:

- `docs/performance-results/sse-reconnect-t3micro-latest-main-20260427.md`

Result:

| signal | value |
| --- | --- |
| status | 0 |
| Docker CPUs | 2 |
| Docker memory | 1024m |
| Docker memory-swap | 1024m |
| pids limit | 384 |
| smoke | `run-sse-reconnect-storm-smoke.sh` |
| Gradle test result | `BUILD SUCCESSFUL` |

Interpretation:

- SSE reconnect storm defense passes under the local 1GiB cgroup gate.
- The remaining realtime risk is no longer basic reconnect replay, but longer soak, concurrent users, and operational telemetry depth.

## Production high-traffic config gate

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

- Production defensive configuration guard still enforces small limits.
- Kafka/read replica remain opt-in and fail only when enabled without safe settings.

## Current bottleneck order

1. Actual 100m dataset artifact is absent locally, so fresh restore cannot start.
2. `run-transaction-100m-fresh-volume-restore-k6.sh` plans to delete the Postgres volume before checking that the dump exists. This is unsafe for repeated local runs.
3. There is no standard way to generate or fetch the 100m fixture dump outside Git before the fresh-volume runner runs.
4. The passing t3.micro gates are integration/cgroup defense smoke, not actual 100m HTTP/k6 evidence.
5. Existing archived capacity/SSE results contain status and budgets, but not peak CPU/memory/GC time-series.
6. HTTP admission and outbox backlog gates exist, but actual local execution still requires a live backend/data/token setup that is not composed by a single safe runner.

## Next Issue/PR Candidates

All items are intended as `issue title = PR title`, one issue per PR. Completed scopes from #435, #436, #437, and #438 are intentionally excluded.

| order | issue/PR title | template | branch | reason |
| ---: | --- | --- | --- | --- |
| 1 | `[Fix] 100m fresh-volume runner dump preflight를 volume 삭제 전에 수행` | `bug_report.yml` | `fix/100m-fresh-volume-dump-preflight-order` | 현재 dry-run 기준 `docker volume rm`이 dump 검증보다 먼저 계획된다. |
| 2 | `[Perf] 100m fixture dump 생성/게시 workflow 추가` | `performance_request.yml` | `perf/100m-fixture-dump-publish-workflow` | 실제 100m k6는 dump artifact가 없으면 시작할 수 없다. |
| 3 | `[Perf] 100m fixture manifest/checksum/Flyway version 검증 추가` | `performance_request.yml` | `perf/100m-fixture-manifest-checksum` | dump가 생겨도 schema version, row distribution, checksum을 검증해야 반복 측정이 안전하다. |
| 4 | `[Perf] 100m fresh-volume runner seed fallback mode 추가` | `performance_request.yml` | `perf/100m-fresh-volume-seed-fallback` | dump가 없을 때 명시적으로 seed-only 또는 fail-only를 선택할 수 있어야 한다. |
| 5 | `[Perf] 100m local k6 실제 실행 gate를 artifact-ready 조건으로 분리` | `performance_request.yml` | `perf/100m-local-k6-artifact-ready-gate` | actual 100m k6 job은 fixture artifact ready 상태에서만 실행되어야 한다. |
| 6 | `[Perf] defensive HTTP admission compose launcher 추가` | `performance_request.yml` | `perf/defensive-http-admission-compose-launcher` | HTTP admission smoke는 존재하지만 live backend/data/token 준비를 한 번에 수행하지 않는다. |
| 7 | `[Perf] outbox backlog gate local bootstrap/token runner 추가` | `performance_request.yml` | `perf/outbox-backlog-local-bootstrap-token-runner` | outbox backlog gate는 internal token/live backend가 없으면 실제 로컬 실행이 막힌다. |
| 8 | `[Perf] t3.micro cgroup smoke CPU/memory/GC peak telemetry 추가` | `performance_request.yml` | `perf/t3micro-cgroup-peak-telemetry` | 현재 archive는 status/budget만 남기고 peak resource 지표가 없다. |
| 9 | `[Perf] t3.micro defensive gates aggregate report runner 추가` | `performance_request.yml` | `perf/t3micro-defensive-gates-aggregate-report` | capacity/SSE/admission/outbox 결과가 개별 파일로 흩어져 병목 우선순위 비교가 어렵다. |
| 10 | `[Perf] t3.micro capacity smoke 반복 soak matrix 추가` | `performance_request.yml` | `perf/t3micro-capacity-repeat-soak-matrix` | `SOAK_REPEAT=1`은 통과했지만 장시간/반복 안정성은 별도 확인이 필요하다. |
| 11 | `[Perf] SSE reconnect storm concurrent-user matrix 추가` | `performance_request.yml` | `perf/sse-reconnect-concurrent-user-matrix` | 단일 reconnect smoke 이후 사용자 수/세션 수 증가에 따른 방어 한계가 아직 수치화되지 않았다. |
| 12 | `[Perf] local Docker 100m vs staging RDS gp3 결과 비교 리포트 추가` | `performance_request.yml` | `perf/100m-local-vs-staging-rds-comparison` | 최종 목표는 RDS gp3이므로 Docker/RDS 결과를 같은 형식으로 비교해야 한다. |

## Verification

- `git pull --ff-only origin main`
- `ls -lh build/fixtures`
- `tools/test/run-transaction-100m-fresh-volume-restore-k6.sh --dry-run`
- `FIXTURE_MODE=verify FIXTURE_REQUIRE_DUMP=true tools/test/run-transaction-100m-fixture-restore.sh`
- `tools/test/run-defensive-runtime-http-admission-smoke.sh --dry-run`
- `DOCKER_T3MICRO_RESULT_NAME=docker-t3micro-capacity-latest-main-20260427 SOAK_REPEAT=1 tools/test/run-docker-t3micro-capacity-smoke.sh`
- `SSE_T3MICRO_RESULT_NAME=sse-reconnect-t3micro-latest-main-20260427 tools/test/run-sse-reconnect-storm-t3micro-gate.sh`
- `tools/test/run-production-high-traffic-config-gate.sh`
