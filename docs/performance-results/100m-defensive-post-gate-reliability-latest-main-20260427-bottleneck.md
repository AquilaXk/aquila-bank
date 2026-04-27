# 100m defensive post-gate-reliability latest-main bottleneck

## 기준

- 날짜: 2026-04-27
- branch: `perf/100m-defensive-post-gate-reliability-retest`
- base: `main @ efccba6`
- 연결 issue: #465
- 목적: #464 병합 이후 로컬 Docker t3.micro 1억 row 조회 부하와 대용량 트래픽 방어 경로를 다시 검증하고, 이미 완료된 작업을 제외한 남은 병목 후보를 PR/issue 단위로 정리한다.
- 제외 범위: #462/#464에서 이미 닫은 k6 summary parser, backend readiness gate 추가, warmup/measured phase 추가, dropped_iterations hard fail 추가, aggregate required input 추가, postgres-exporter config mount 자체는 신규 후보에서 제외한다.

## 환경

- Docker stack: `compose.yml`, `compose.t3micro.yml`, `compose.loadtest.yml`
- PostgreSQL: `aquila-bank-postgres-loadtest`, host port `15432`
- Backend: `aquila-bank-backend-loadtest`, host port `18080`
- Observability: Prometheus `19090`, Grafana `13001`, postgres-exporter `19187`
- Fixture estimate: `transaction_read_model=50,000,000`, `transaction_read_model_archive=50,000,028`
- Dataset env: `K6_HOT_ACCOUNT_ID=910000001`, `K6_COLD_ACCOUNT_ID=910000002`
- Hot window: `2026-04-01T00:00:00Z` ~ `2026-04-30T00:00:00Z`
- Cold window: `2026-01-01T00:00:00Z` ~ `2026-01-31T00:00:00Z`

## 실행 결과

| Run | Result | 핵심 수치 | 판단 |
| --- | --- | --- | --- |
| fixture dataset probe | failed | `total_estimate=99,999,884`, `min=100,000,000` | #464 DB gate는 동작하지만 `reltuples` estimate 기준이라 116 row 차이로 실패했다. manifest 파일도 없어 fallback min 100m이 적용됐다. |
| exact count 분리 실험 | crashed DB | 100m `count(*)` 중 server connection lost, PostgreSQL backend `signal 9`, crash recovery 약 70초 | t3.micro PostgreSQL 384MiB에서 full exact count는 검증 방식 자체가 OOM/crash 병목이다. |
| VU3 baseline | passed | `http_req_failed=0`, `checks=1`, p95 약 3.1~3.3ms, max 67~234ms | readiness gate, warmup 분리, summary parser는 정상 동작했다. 조회 plan 병목은 재발하지 않았다. |
| VU16 overload | failed | `429=1.1089%`, `503 count=1`, p95 약 4.4~5.1ms, max 294~589ms | 대다수 요청은 방어됐지만 503=0 hard gate를 1건 위반했다. |
| burst 256/s 20s | failed | `429=8.7308%`, `503=0`, `dropped_iterations=2488`, `Insufficient VUs` | #464의 dropped/insufficient hard fail은 정상 동작한다. 병목은 generator capacity와 burst 목표/정책 불일치다. |
| Prometheus VU3 | passed | `http_req_failed=0`, `checks=1`, p95 약 3.3~3.4ms | Prometheus remote-write와 Grafana/Prometheus health는 정상이다. |
| capacity runner | blocked | `CAPACITY_K6_DOCKER_CONTEXT is required` | 로컬 Docker context는 `default`, `desktop-linux`뿐이라 off-host k6 capacity runner prerequisite이 아직 없다. |
| aggregate required input | passed as guard | `required aggregate input is missing: capacity`로 dry-run fail-fast | required gate 자체는 동작한다. 남은 문제는 capacity/SSE/admission/outbox/memory 입력을 실제로 채우는 실행 체계다. |

## 리소스와 로그

- post-run memory snapshot: backend `398.4MiB`, PostgreSQL `184.9MiB`, Prometheus `78.0MiB`, Grafana `79.1MiB`, postgres-exporter `20.7MiB`
- aggregate memory total: `761.08MiB / 900MiB`, status `pass`
- exact count 후 PostgreSQL 로그: `client backend ... terminated by signal 9`, `database system was interrupted`, `automatic recovery in progress`
- postgres-exporter는 config missing warn은 사라졌고, DB crash/recovery 동안 connection error만 남았다.

## 남은 병목 판단

1억 row 조회 API 자체는 월별 chunk/index/keyset 경로에서 p95 3~5ms로 안정적이다. 현재 병목은 “실제 1억 row를 안전하게 검증하는 방식”, “t3.micro에서 503=0 보장”, “burst/high-traffic generator와 capacity runner 분리”에 있다. 특히 이번 실행에서는 검증용 exact count가 PostgreSQL을 OOM/crash시켜 이후 부하 테스트에도 간섭할 수 있음을 확인했다.

## 다음 PR/issue 후보

모두 issue 제목 = PR 제목, issue 1개 = PR 1개 기준이다.

1. `[Perf] 100m fixture DB gate를 OOM-safe estimate 검증으로 전환` / 템플릿 `performance_request.yml` / 브랜치 `perf/100m-fixture-oom-safe-db-gate`
   - 현재 DB gate는 `reltuples` estimate가 100m보다 116 낮으면 실패하고, exact count로 확인하면 PostgreSQL 384MiB에서 OOM/crash가 난다. partition별 `reltuples` 허용 오차, manifest 보강, bounded sample/window count 조합으로 바꿔야 한다.

2. `[Perf] transaction read overload 503 원인 추적 및 zero-503 안정화` / 템플릿 `performance_request.yml` / 브랜치 `perf/transaction-read-overload-zero-503`
   - VU16 overload에서 503이 1건 발생해 hard gate가 실패했다. DB crash 직후 간섭 가능성을 포함해 datasource timeout, admission guard, exception mapping을 분리해 503=0 계약을 복구한다.

3. `[Perf] burst 256/s generator capacity를 off-host 기준으로 재보정` / 템플릿 `performance_request.yml` / 브랜치 `perf/burst-offhost-generator-calibration`
   - burst에서 `dropped_iterations=2488`와 `Insufficient VUs`가 발생했다. local generator 한계를 제거하고 off-host generator 기준으로 preAllocated/max VUs와 target rate를 다시 잡는다.

4. `[Chore] off-host k6 docker context capacity runner 구성` / 템플릿 `task_request.yml` / 브랜치 `chore/offhost-k6-capacity-runner`
   - capacity runner는 여전히 `CAPACITY_K6_DOCKER_CONTEXT` 없이는 시작하지 않는다. app stack과 k6 generator를 다른 Docker context로 분리해야 capacity 결과가 host 간섭을 제거한다.

5. `[Perf] capacity runner remote URL/prometheus 계약 검증 추가` / 템플릿 `performance_request.yml` / 브랜치 `perf/capacity-runner-remote-contract`
   - docker context 외에도 `CAPACITY_K6_REMOTE_BASE_URL`, `CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL`이 필요하다. off-host runner가 app/prometheus에 실제 도달 가능한지 preflight로 닫는다.

6. `[Perf] PostgreSQL crash recovery state preflight 추가` / 템플릿 `performance_request.yml` / 브랜치 `perf/postgres-recovery-state-preflight`
   - exact count OOM 이후 DB는 약 70초간 recovery를 수행했다. 부하 테스트 전 `pg_is_in_recovery=false`, health=healthy, exporter 안정화 구간을 확인해 이전 crash가 측정에 섞이지 않게 한다.

7. `[Perf] t3.micro read admission 429/latency 정책 재보정` / 템플릿 `performance_request.yml` / 브랜치 `perf/read-admission-policy-calibration`
   - VU16은 429 1.1%였고 burst는 429 8.7%였다. 503=0을 보장하면서 p99.9/max spike를 줄이려면 admission threshold, retry-after, queue 정책을 실제 SLO 기준으로 재보정해야 한다.

8. `[Perf] burst hot-first p99.9 spike profile 추가` / 템플릿 `performance_request.yml` / 브랜치 `perf/burst-hot-first-spike-profile`
   - burst hot-first p99.9가 1123ms, max가 1292ms까지 상승했다. DB index plan은 정상이라 JDBC mapping, serialization, pool wait, CPU steal을 profile로 분리한다.

9. `[Chore] defensive aggregate capacity/SSE/admission/outbox 입력 연결` / 템플릿 `task_request.yml` / 브랜치 `chore/defensive-aggregate-full-inputs`
   - required gate는 동작하지만 이번 aggregate는 k6/memory만 채웠다. capacity, SSE reconnect, HTTP admission, outbox backlog 산출물을 한 실행에서 연결해야 “대용량 트래픽 방어” 전체 판정이 가능하다.

10. `[Perf] DB 검증 쿼리 statement_timeout/memory budget guard 추가` / 템플릿 `performance_request.yml` / 브랜치 `perf/db-verification-query-resource-guard`
    - full count 같은 검증 쿼리가 운영형 t3.micro DB를 죽이면 테스트가 아니라 장애 주입이 된다. 검증 쿼리별 timeout, `work_mem`, read-only transaction, concurrency cap을 명시한다.

## 산출물

- `docs/performance-results/transaction-100m-post-gate-reliability-vu3-20260427-summary.md`
- `docs/performance-results/transaction-100m-post-gate-reliability-vu16-overload-20260427-summary.md`
- `docs/performance-results/transaction-100m-post-gate-reliability-burst-20260427-summary.md`
- `docs/performance-results/transaction-100m-post-gate-reliability-prom-vu3-20260427-summary.md`
- `docs/performance-results/t3micro-defensive-gates-post-gate-reliability-20260427.md`
- `build/reports/k6/transaction-100m-post-gate-reliability-burst-20260427-runner.log`
- `build/reports/t3micro/post-gate-reliability-memory-summary.tsv`
