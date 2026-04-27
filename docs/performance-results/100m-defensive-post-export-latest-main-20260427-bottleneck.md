# 100m defensive post-export latest-main 20260427 bottleneck

## Scope

- base: `main` @ `9cd968c` (`#448` merge)
- branch: `perf/100m-defensive-post-export-retest`
- issue: `#449`
- 목적: 실제 1억 row fixture가 남아 있는 로컬 Docker t3.micro 제한 환경에서 거래 조회와 대용량 트래픽 방어 경로를 재검증하고, 완료된 작업을 제외한 신규 병목 후보를 PR/이슈 단위로 정리합니다.

## Environment

- PostgreSQL container: `aquila-bank-postgres-loadtest`
- PostgreSQL limit: `0.60 CPU`, `384MiB`
- Backend container: `aquila-bank-backend-loadtest`
- Backend limit: `2 CPU`, `1GiB`
- dataset estimate:
  - `transaction_read_model`: `50,000,000`
  - `transaction_read_model_archive`: `50,000,028`
- populated partitions:
  - `transaction_read_model_y202604`: `49,999,856`, `12GB`
  - `transaction_read_model_archive_y202601`: `50,000,028`, `13GB`
- hot account: `910000001`, window `2026-04-01T00:00:00Z..2026-04-30T00:00:00Z`
- cold account: `910000002`, window `2026-01-01T00:00:00Z..2026-01-31T00:00:00Z`

## Executed Checks

### Fixture/export path

- `build/fixtures` was empty before export validation.
- `run-transaction-100m-existing-volume-fixture-export.sh --dry-run` planned the expected dump path: `build/fixtures/transaction-100m-fixture.dump`.
- Minimal reproduction of the export copy step failed:

```text
docker cp aquila-bank-postgres:/tmp/transaction-100m-fixture.dump /tmp/aquila-bank-nonexistent-fixture-copy-test.dump
Error response from daemon: No such container: aquila-bank-postgres
```

Root cause: `run-transaction-100m-fixture-restore.sh` resolves `postgres_container_name` from `FIXTURE_POSTGRES_CONTAINER_NAME` / `LOADTEST_POSTGRES_CONTAINER_NAME`, but the `docker cp` calls still hardcode `aquila-bank-postgres`.

### Dataset probe

The ad-hoc range discovery query below was cancelled after about 46 seconds:

```sql
SELECT 'current' AS table_name, min(account_id), max(account_id), min(booked_at), max(booked_at)
FROM transaction_read_model
UNION ALL
SELECT 'archive', min(account_id), max(account_id), min(booked_at), max(booked_at)
FROM transaction_read_model_archive;
```

During the scan, PostgreSQL used about `60.29% CPU` and `361.9MiB / 384MiB` (`94.25%`). This is not an online query target, but it shows that test-prep discovery must also be index/metadata based on t3.micro.

### k6 EXPLAIN snapshot

Command with `K6_EXPLAIN_SNAPSHOT=true` failed before k6 execution:

```text
psql: error: build/reports/k6/transaction-100m-post-export-retest-vu3-20260427-explain/pre-hot-first.txt: No such file or directory
```

Root cause: `psql` runs inside the PostgreSQL container and tries to write to a host-relative `build/reports/...` path.

### k6 summary-only VU3 baseline

- report: `docs/performance-results/transaction-100m-post-export-retest-vu3-20260427-summary.md`
- result: pass
- `http_req_failed`: `0`
- `transaction 429 rate`: `0`
- p95:
  - hot first: `5.243ms`
  - hot cursor: `5.139ms`
  - cold first: `4.864ms`
  - cold cursor: `5.041ms`
- sampled resource:
  - k6 generator: `93.61% CPU`
  - backend: `107.58% CPU`, `394MiB / 1GiB`
  - PostgreSQL: `62.56% CPU`, `186.6MiB / 384MiB`

### k6 summary-only VU16 overload

- report: `docs/performance-results/transaction-100m-post-export-retest-vu16-overload-20260427-summary.md`
- result: pass, but with hidden risk
- `http_req_failed`: `0.005409`
- `transaction 429 rate`: `0.005396`
- p95:
  - hot first: `2.082ms`
  - hot cursor: `2.007ms`
  - cold first: `1.924ms`
  - cold cursor: `1.994ms`
- sampled resource:
  - k6 generator: `103.87% CPU`
  - backend: `107.95% CPU`, `399.5MiB / 1GiB`
  - PostgreSQL: `62.08% CPU`, `186.5MiB / 384MiB`
- observed stderr:

```text
GoError: cold_first returned HTTP 503
```

Overload mode treats only `429` as expected rejection. A `503` can still appear while the run passes because the check threshold is `rate>0.99` and `http_req_failed` is disabled in overload mode.

### k6 burst attempt

- report: `docs/performance-results/transaction-100m-post-export-retest-burst-20260427-summary.md`
- requested mode: `K6_SCENARIO_MODE=burst`, `K6_BURST_RATE=256`, `K6_PRE_ALLOCATED_VUS=64`, `K6_MAX_VUS=64`
- result: pass, but not a real burst run
- k6 engine output:

```text
scenarios: (100.00%) 1 scenario, 16 max VUs, 50s max duration (incl. graceful stop):
  * default: 16 looping VUs for 20s (gracefulStop: 30s)
```

Root cause: `K6_VUS` and `K6_DURATION` are reserved k6 runtime option environment variables. They override script-defined `options.scenarios`, so the planned `burst`/`constant-arrival-rate` scenario is not actually exercised.

### k6 + Prometheus + Grafana VU3

- report: `docs/performance-results/transaction-100m-post-export-retest-prom-vu3-20260427-summary.md`
- result: pass
- `http_req_failed`: `0`
- Prometheus readiness: `200 OK`
- Grafana health: `200 OK`
- postgres-exporter log: PostgreSQL 18 `stat_bgwriter` noise did not recur with `--no-collector.stat_bgwriter`; only missing optional config warning was logged.
- sampled resource:
  - k6 generator: `89.76% CPU`, `72.17MiB`
  - Grafana: `204.7MiB`
  - Prometheus: `95.2MiB`
  - Alertmanager: `20.22% CPU`, `38.74MiB`
  - backend: `103.36% CPU`, `407.9MiB / 1GiB`
  - PostgreSQL: `59.39% CPU`, `189.2MiB / 384MiB`

Note: observability containers are not constrained by the t3.micro compose budget, so app/PostgreSQL resource claims and host-level observability overhead must be interpreted separately.

### Backend health

`/actuator/health` returned `503` even though DB components and readiness state were `UP`:

```text
"outbox":{"details":{"error":"java.lang.IllegalArgumentException: 'value' must not be null"},"status":"DOWN"}
```

This can break deploy/load-balancer health checks independently from the transaction read hot path.

## Bottleneck Candidates

이미 완료된 `#350`, `#351`, `#446`, `#448` 범위는 제외했습니다. 아래 항목은 이번 실행에서 새로 확인된 병목 또는 검증 신뢰도 문제입니다.

1. `[Fix] 100m fixture export/restore docker cp container name 정합성 수정` / template: `bug_report.yml` / branch: `fix/100m-fixture-docker-cp-container-name`
   - `docker cp`가 `aquila-bank-postgres`를 하드코딩해 loadtest 컨테이너명에서 export/restore가 실패합니다.

2. `[Fix] k6 EXPLAIN snapshot host artifact 출력 경로 수정` / template: `bug_report.yml` / branch: `fix/k6-explain-host-artifact-output`
   - `psql --output build/reports/...`가 컨테이너 내부 경로로 해석되어 pre/post EXPLAIN 산출물이 생성되지 않습니다.

3. `[Fix] k6 scenario env 예약어 충돌 제거` / template: `bug_report.yml` / branch: `fix/k6-scenario-env-reserved-name`
   - `K6_VUS`/`K6_DURATION`이 k6 런타임 옵션으로 작동해 `burst`/`constant-arrival-rate` 시나리오가 실제로는 `constant-vus`로 실행됩니다.

4. `[Perf] overload 503/429 방어 판정 분리 및 hard fail 추가` / template: `performance_request.yml` / branch: `perf/overload-rejection-status-slo`
   - overload mode에서 `429`는 기대 rejection이지만 `503`은 별도 지표와 threshold로 분리해야 합니다. 현재는 일부 `503`이 있어도 통과할 수 있습니다.

5. `[Fix] outbox health indicator null status 복구` / template: `bug_report.yml` / branch: `fix/outbox-health-null-value`
   - 거래 조회는 정상이어도 `/actuator/health`가 outbox null-value 오류로 `503`을 반환합니다.

6. `[Build] loadtest PostgreSQL 포트 매핑 단일화` / template: `task_request.yml` / branch: `build/loadtest-postgres-port-single-binding`
   - compose 병합 결과 같은 PostgreSQL 컨테이너에 `55432`와 `15432`가 동시에 매핑되어 실행 로그/스크립트가 다른 포트를 보고합니다.

7. `[Perf] 100m fixture metadata 기반 dataset probe 추가` / template: `performance_request.yml` / branch: `perf/100m-fixture-metadata-probe`
   - 테스트 준비용 `min/max` 탐색도 1억 row 전체 스캔으로 떨어질 수 있습니다. fixture manifest 또는 partition metadata 기반 probe가 필요합니다.

8. `[Perf] k6 generator off-host/docker-context capacity gate 기본화` / template: `performance_request.yml` / branch: `perf/k6-generator-offhost-capacity-gate`
   - local k6 generator가 테스트 중 `~90-104% CPU`를 사용해 app/PostgreSQL 병목 측정을 오염시킵니다.

9. `[Chore] observability loadtest resource profile 분리` / template: `task_request.yml` / branch: `chore/observability-loadtest-resource-profile`
   - Prometheus/Grafana/Alertmanager는 현재 t3.micro 제한 밖에서 실행되어 app budget과 host observability cost가 섞입니다.

10. `[Perf] transaction k6 p99 summary 산출 정확도 보정` / template: `performance_request.yml` / branch: `perf/transaction-k6-p99-summary`
    - report의 custom trend p99가 `n/a`로 남아 SLO 판단이 p95/max 중심으로 치우칩니다.

## Conclusion

- 현재 1억 row bounded 조회 자체는 기존 volume 기준으로 VU3/VU16에서 p95 수 ms 수준으로 통과했습니다.
- 지금의 주요 병목은 SQL hot path보다 fixture 재현성, 테스트 시나리오 정확도, overload 실패 판정, 운영 health 신뢰도입니다.
- 다음 PR 우선순위는 `fixture docker cp` → `k6 EXPLAIN artifact` → `k6 scenario env 충돌` → `overload 503/429 SLO` 순서가 가장 짧은 시간에 목표 대비 신뢰도를 올립니다.
