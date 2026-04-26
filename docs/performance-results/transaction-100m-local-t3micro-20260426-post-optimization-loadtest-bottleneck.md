# Transaction 100m local t3.micro post-optimization loadtest bottleneck

## Scope

- date: 2026-04-26 KST
- issue: #378
- branch: `perf/transaction-100m-post-optimization-loadtest-report`
- base main sha: `2c39290`
- goal: 최신 `main` 기준 1억 row 거래 조회와 대용량 HTTP 부하를 로컬 Docker t3.micro 예산에서 재측정하고, 아직 남은 병목 후보를 issue/PR 단위로 정리

## Environment

- compose: `compose.yml`, `compose.t3micro.yml`, `compose.loadtest.yml`
- PostgreSQL: `PostgreSQL 18.3`
- backend: Spring Boot app, Java 21, loadtest profile container
- observability: k6, Prometheus remote write, Grafana, Alertmanager, postgres-exporter
- fixture migration state: Flyway success `52`
- dataset estimate: hot `transaction_read_model` 50,000,000 rows, archive `transaction_read_model_archive` 50,000,028 rows
- hot account: `910000001`, window `2026-04-01T00:00:00Z..2026-04-30T00:00:00Z`
- cold account: `910000002`, window `2026-01-01T00:00:00Z..2026-01-31T00:00:00Z`
- PostgreSQL budget: memory `384MiB`, CPU `0.6`, OOM false, health healthy
- backend loadtest budget: memory `1GiB`, CPU `2`, DB pool `4`, Tomcat max threads `16`

Constraint:

- 현재 compose의 backend loadtest budget은 `T3MICRO_BACKEND_CPUS=2`, `T3MICRO_BACKEND_MEMORY=1024m` 기본값이다. PostgreSQL은 t3.micro 근사 예산이지만, backend와 DB를 합친 단일 t3.micro 전체 예산 검증은 아직 아니다.

## Runs

### Run A: default guard, VU=8

Command:

```bash
K6_REPORT_NAME=transaction-100m-postopt-default-vu8-20260426 \
K6_HOT_ACCOUNT_ID=910000001 \
K6_HOT_FROM=2026-04-01T00:00:00Z \
K6_HOT_TO=2026-04-30T00:00:00Z \
K6_COLD_ACCOUNT_ID=910000002 \
K6_COLD_FROM=2026-01-01T00:00:00Z \
K6_COLD_TO=2026-01-31T00:00:00Z \
K6_VUS=8 \
K6_DURATION=1m \
tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps
```

Summary:

- report: `docs/performance-results/transaction-100m-postopt-default-vu8-20260426-summary.md`
- result: failed threshold
- admission: `OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=3`
- `http_req_failed`: `0.7301305125257249`
- `checks`: `0.42503499790523486`
- p95: hot first 2.17ms, hot cursor 2.91ms, cold first 3.14ms, cold cursor 3.14ms

Conclusion:

- 최신 `main`에서도 default t3.micro guard의 첫 병목은 DB가 아니라 admission guard이다.
- p95가 낮고 실패율만 높은 형태라 1억 row index 조회 자체는 정상이고, 대부분 요청이 429로 fail-fast 처리됐다.

### Run B: raised guard, VU=8

Setup:

```bash
OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=8 \
docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml \
  --profile loadtest up -d --force-recreate aquila-bank-backend
```

Command:

```bash
K6_REPORT_NAME=transaction-100m-postopt-admission8-vu8-20260426 \
K6_HOT_ACCOUNT_ID=910000001 \
K6_HOT_FROM=2026-04-01T00:00:00Z \
K6_HOT_TO=2026-04-30T00:00:00Z \
K6_COLD_ACCOUNT_ID=910000002 \
K6_COLD_FROM=2026-01-01T00:00:00Z \
K6_COLD_TO=2026-01-31T00:00:00Z \
K6_VUS=8 \
K6_DURATION=1m \
tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps
```

Summary:

- report: `docs/performance-results/transaction-100m-postopt-admission8-vu8-20260426-summary.md`
- result: passed
- admission: `OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=8`
- `http_req_failed`: `0`
- `checks`: `1`
- p95: hot first 6.67ms, hot cursor 6.36ms, cold first 6.62ms, cold cursor 6.46ms
- completed iterations: 29,379/min

Observed sample:

- k6 CPU 62.67% -> 98.11% -> 92.13%, memory 35.92MiB -> 79.34MiB
- backend CPU 202.87% -> 121.57% -> 116.03%, memory 380.8MiB -> 401.3MiB / 1GiB
- PostgreSQL CPU 38.77% -> 62.37% -> 61.46%, memory 56.62MiB -> 57.37MiB / 384MiB

Conclusion:

- guard를 8로 올려도 1억 row hot/archive 조회 p95는 SLO보다 충분히 낮다.
- PostgreSQL memory/OOM/plan이 아니라 backend CPU와 request/serialization/JDBC 처리 비용, 그리고 k6 client CPU가 다음 병목 후보이다.

### Run C: raised guard, VU=16 overload

Command:

```bash
K6_REPORT_NAME=transaction-100m-postopt-admission8-vu16-20260426 \
K6_HOT_ACCOUNT_ID=910000001 \
K6_HOT_FROM=2026-04-01T00:00:00Z \
K6_HOT_TO=2026-04-30T00:00:00Z \
K6_COLD_ACCOUNT_ID=910000002 \
K6_COLD_FROM=2026-01-01T00:00:00Z \
K6_COLD_TO=2026-01-31T00:00:00Z \
K6_VUS=16 \
K6_DURATION=1m \
tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps
```

Summary:

- report: `docs/performance-results/transaction-100m-postopt-admission8-vu16-20260426-summary.md`
- result: failed threshold
- admission: `OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=8`
- `http_req_failed`: `0.7494471433676665`
- `checks`: `0.40070734364168803`
- p95: hot first 3.21ms, hot cursor 5.73ms, cold first 5.96ms, cold cursor 5.98ms
- completed iterations: 435,050/min

Observed sample:

- k6 CPU 218.37%, memory 99.5MiB
- backend CPU 210.76%, memory 413.8MiB / 1GiB
- PostgreSQL CPU 65.55%, memory 62.08MiB / 384MiB
- k6 stacktrace output: 약 171,210 lines

Conclusion:

- VU=16은 DB 조회 병목이 아니라 guard 초과 트래픽을 의도대로 429 차단하는 케이스이다.
- 현재 k6 script는 예상 가능한 429도 `fail()`로 stacktrace를 대량 출력하므로 overload 실험에서 client/log overhead가 측정 신뢰도를 떨어뜨린다.

## Bottleneck Order

1. Default t3.micro read traffic: transaction read admission guard `max=3`이 첫 병목이자 보호 장치이다.
2. Raised guard VU=8: 1억 row hot/archive query p95는 6.7ms 이하로 정상이며, DB memory/OOM 병목은 재현되지 않았다.
3. Raised guard VU=8 resource split: PostgreSQL은 약 60% CPU와 60MiB memory 수준이고, backend/k6 CPU가 더 큰 신호이다.
4. Raised guard VU=16 overload: 실패율은 guard 429가 대부분이며, k6 stacktrace flood가 부하 생성기 병목을 섞는다.
5. Test environment constraint: backend가 2 CPU/1GiB라 단일 t3.micro 전체 예산 검증은 아직 별도 PR로 닫아야 한다.

## Excluded Completed Work

- #366 `[Fix] transaction 100m k6-only preflight false-negative 수정`
- #367 `[Perf] transaction read backend CPU hot path profiling gate 추가`
- #368 `[Perf] transaction read response mapping/serialization CPU 절감`
- #369 `[Perf] transaction read admission/Hikari/DB pool matrix benchmark 추가`
- #370 `[Perf] transaction read admission budget 정책을 측정값 기반으로 보정`
- #374 `[Docs] 로컬 성능 절차 문서 Git 추적 제거`

## Candidate Issue/PR List

1. `[Perf] transaction 100m single-host t3.micro budget profile 추가` / template: `performance_request.yml` / branch: `perf/transaction-100m-single-host-t3micro-budget`
   - 현재 backend 2 CPU/1GiB와 PostgreSQL 0.6 CPU/384MiB가 분리되어 있어 단일 t3.micro 전체 예산 검증이 아니다. backend+PostgreSQL 합산 CPU/memory budget profile과 VU=3/VU=8 gate를 추가한다.

2. `[Perf] transaction k6 overload mode Retry-After backoff 추가` / template: `performance_request.yml` / branch: `perf/transaction-k6-overload-backoff`
   - VU=16에서 429가 정상 보호 동작인데 k6가 stacktrace를 17만 line 이상 출력한다. 429를 별도 metric으로 집계하고 `Retry-After` 기반 backoff/summary mode를 둬 overload 측정 노이즈를 줄인다.

3. `[Perf] transaction read CPU budget split matrix 추가` / template: `performance_request.yml` / branch: `perf/transaction-read-cpu-budget-split`
   - admission/Hikari matrix는 완료됐지만 backend/PostgreSQL CPU split 자체는 아직 축으로 검증하지 않았다. 같은 1억 fixture에서 backend CPU, PostgreSQL CPU, memory split을 바꿔 p95/429/CPU를 비교한다.

4. `[Perf] transaction read high-traffic long soak gate 추가` / template: `performance_request.yml` / branch: `perf/transaction-read-high-traffic-long-soak`
   - 현재 post-optimization 결과는 1분 부하 기준이다. VU=8 admission=8을 30~60분 유지해 GC, connection churn, Prometheus scrape, PostgreSQL cache 변화에 따른 p95 degradation을 검증한다.

5. `[Perf] transaction read compression threshold benchmark 추가` / template: `performance_request.yml` / branch: `perf/transaction-read-compression-benchmark`
   - 서버 compression이 켜져 있으나 50-row JSON 응답에서는 네트워크 절감보다 CPU 비용이 클 수 있다. compression on/off와 threshold별 CPU/p95를 비교해 t3.micro 기본값을 확정한다.

6. `[Perf] transaction read JDBC row mapping allocation benchmark 추가` / template: `performance_request.yml` / branch: `perf/transaction-read-jdbc-mapper-allocation`
   - response mapping 최적화는 완료됐지만 JDBC row mapper의 enum 변환, `OffsetDateTime` 변환, domain list 생성 비용은 별도 축이다. JFR 기준으로 mapper 비용을 측정하고 필요한 경우 안전한 low-allocation mapper로 줄인다.

7. `[Perf] transaction 100m realistic weighted workload 추가` / template: `performance_request.yml` / branch: `perf/transaction-100m-realistic-weighted-workload`
   - 현재 k6는 hot first/cursor와 archive first/cursor를 동일 비율로 호출한다. 실제 운영 비율에 가까운 hot/archive/filter/detail weighted scenario를 추가해 용량 판단을 현실화한다.

8. `[Perf] transaction read accepted/rejected SLO 분리 dashboard 추가` / template: `performance_request.yml` / branch: `perf/transaction-read-accepted-rejected-slo`
   - 429 보호 동작과 accepted request latency를 한 threshold failure로 보면 원인 분리가 느리다. accepted p95, rejected ratio, inflight, Retry-After를 분리한 Prometheus/Grafana view와 k6 summary를 추가한다.

9. `[Perf] transaction read replica 100m offload scenario 검증` / template: `performance_request.yml` / branch: `perf/transaction-read-replica-100m-offload`
   - read replica routing과 lag guard는 구현됐지만 이번 1억 row 로컬 실험은 단일 PostgreSQL 기준이다. primary write budget을 보호하는 read replica offload scenario를 1억 fixture로 재검증한다.

10. `[Perf] transaction 100m prebuilt fixture restore workflow 추가` / template: `performance_request.yml` / branch: `perf/transaction-100m-prebuilt-fixture-restore`
    - 1억 row 생성과 조회 분리는 완료됐지만 반복 실험은 여전히 fixture 준비 비용 영향을 받는다. prebuilt dump/volume restore 경로로 테스트 시작 시간을 줄이고, 병목 재현의 안정성을 높인다.

## Final State

- backend container restored to default `OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=3`
- PostgreSQL remained healthy, OOM false
- no application code or migration changed
