# 100m defensive post-guardrails latest-main bottleneck

## 기준

- 날짜: 2026-04-27
- branch: `perf/100m-defensive-post-guardrails-retest`
- base: `main @ 1a4235f`
- 연결 issue: #461
- 목적: #460 병합 이후 최신 main에서 로컬 Docker t3.micro 1억 row 조회 부하와 대용량 트래픽 방어 경로를 다시 검증하고, 남은 병목 후보를 PR/issue 단위로 정리한다.
- 제외 범위: #458, #460에서 이미 닫은 hotswap 방지, zero-iteration hard fail, p99.9 metric export, CPU/profile runner 추가, aggregate/fixture scheduled verify 자동화 자체는 신규 후보에서 제외한다.

## 환경

- Docker stack: `compose.yml`, `compose.t3micro.yml`, `compose.loadtest.yml`
- PostgreSQL: `aquila-bank-postgres-loadtest`, host port `15432`, OOM `false`
- Backend: `aquila-bank-backend-loadtest`, host port `18080`, OOM `false`
- Observability: Prometheus `19090`, Grafana `13001`, postgres-exporter `19187`
- Fixture estimate: `transaction_read_model=50,000,000`, `transaction_read_model_archive=50,000,028`
- Dataset probe source: `build/fixtures/transaction-100m-fixture.dump.dataset.env`
- Hot: `accountId=910000001`, `2026-04-01T00:00:00Z` ~ `2026-04-30T00:00:00Z`
- Cold: `accountId=910000002`, `2026-01-01T00:00:00Z` ~ `2026-01-31T00:00:00Z`

## 조회 plan

- hot first/cursor: `transaction_read_model_y202604_account_id_booked_at_id_idx` Index Scan / Index Only Scan
- cold first/cursor: `transaction_read_model_archive_y202601` partition의 account/booked_at/id index 사용
- 이번 실행에서는 1억 row 조건에서 Seq Scan + Sort 재발은 없었다.

## 실행 결과

| Run | Result | 핵심 수치 | 판단 |
| --- | --- | --- | --- |
| backend recreate 후 즉시 VU3 | failed | `http_req_failed=0.9569`, `checks=0.0825` | backend container started 이후 Spring ready까지 약 25.5초가 걸렸고, k6가 readiness 전에 내부 DNS로 접속해 connection refused가 발생했다. |
| ready 이후 VU3 baseline | wrapper failed | `http_req_failed=0`, `checks=1`, hot/cold p95 약 6.6~7.1ms, max 160~443ms | 조회 자체는 통과했지만 wrapper summary gate가 `checks actual=0`으로 잘못 파싱해 exit 1을 냈다. |
| VU16 overload | passed | `429=0.0073`, `503=0`, hot/cold p95 약 3.0~3.1ms, max 252~309ms | admission guard는 정상 방어했고 503은 없었다. |
| burst 256/s 20s | passed with warning | `429=0.0141`, `503=0`, `dropped_iterations=19`, `Insufficient VUs` 경고 | 성능 수치는 양호하지만 generator sizing 실패가 남아 있다. summary gate를 끈 상태에서는 exit 0이므로 자동 gate 신뢰성이 부족하다. |
| Prometheus VU3 | passed | `http_req_failed=0`, `checks=1`, hot/cold p95 약 4.7~5.0ms | Prometheus remote-write와 summary archive가 동작했다. Prometheus `/-/ready`, Grafana `/api/health` 모두 200이다. |
| capacity runner | blocked | `CAPACITY_K6_DOCKER_CONTEXT is required` | 로컬 Docker context는 `default`, `desktop-linux`뿐이라 off-host k6 capacity prerequisite이 충족되지 않았다. |

## 리소스 관찰

- post-run snapshot: backend `400MiB / 1GiB`, PostgreSQL `81MiB / 384MiB`, Prometheus `74MiB / 256MiB`, Grafana `81MiB / 256MiB`, postgres-exporter `16MiB / 128MiB`
- container별 OOM은 없었다.
- postgres-exporter는 PostgreSQL 18 metric 호환 오류는 재발하지 않았고, config file missing warn만 남았다.
- backend log에는 부하 중 application exception은 없었다.

## 남은 병목 판단

조회 read model 자체는 monthly chunk/index/keyset 경로가 동작해 1억 row 조건에서도 p95는 충분히 낮다. 현재 목표 달성을 막는 병목은 DB index plan보다 테스트 방어 체계와 capacity prerequisite 쪽이다. 특히 자동화 gate가 정상 결과를 실패로 만들거나, generator 부족을 성공으로 통과시키는 문제가 있어 다음 PR은 측정 신뢰성부터 닫아야 한다.

## 다음 PR/issue 후보

모두 issue 제목 = PR 제목, issue 1개 = PR 1개 기준이다.

1. `[Perf] k6 summary gate metric parser false-negative 수정` / 템플릿 `performance_request.yml` / 브랜치 `perf/k6-summary-gate-metric-parser`
   - `checks rate=1`인데 wrapper가 `checks actual=0`으로 판정해 baseline을 실패시킨다. 이 작업이 먼저 닫혀야 자동 gate 결과를 신뢰할 수 있다.

2. `[Perf] loadtest backend actuator readiness gate 추가` / 템플릿 `performance_request.yml` / 브랜치 `perf/loadtest-backend-readiness-gate`
   - backend recreate 직후 k6가 readiness 전에 시작되어 connection refused가 대량 발생했다. compose started가 아니라 actuator readiness를 기준으로 k6 시작을 막아야 한다.

3. `[Perf] burst dropped_iterations hard fail 및 generator sizing 보정` / 템플릿 `performance_request.yml` / 브랜치 `perf/k6-burst-dropped-iterations-gate`
   - burst 256/s에서 `Insufficient VUs`와 `dropped_iterations=19`가 발생했지만 측정은 성공으로 남았다. dropped/interrupted/insufficient VUs를 독립 hard fail로 분리한다.

4. `[Chore] off-host k6 docker context capacity runner 구성` / 템플릿 `task_request.yml` / 브랜치 `chore/offhost-k6-capacity-runner`
   - capacity runner는 `CAPACITY_K6_DOCKER_CONTEXT`를 요구한다. local app stack과 generator를 분리하지 않으면 t3.micro 방어 capacity 결과가 host 간섭을 포함한다.

5. `[Perf] t3.micro total memory budget aggregate gate 추가` / 템플릿 `performance_request.yml` / 브랜치 `perf/t3micro-total-memory-budget-gate`
   - 개별 컨테이너 limit은 있지만 전체 합산 budget gate가 약하다. backend, DB, observability 합산 RSS가 t3.micro 실사용 가능 메모리를 넘지 않는지 aggregate에서 실패 처리해야 한다.

6. `[Perf] k6 warmup phase와 measured phase 분리` / 템플릿 `performance_request.yml` / 브랜치 `perf/k6-warmup-measured-phase`
   - ready 이후 baseline도 첫 구간 max가 443ms까지 튄다. DispatcherServlet/CORS/cache/pool warmup을 측정 구간과 분리해 p99.9/max SLO를 더 정확히 본다.

7. `[Perf] 100m fixture probe DB-count 검증 gate 추가` / 템플릿 `performance_request.yml` / 브랜치 `perf/100m-fixture-db-count-gate`
   - dataset probe는 manifest/env를 우선 사용한다. 실제 DB row estimate, hot/cold count, partition 존재를 함께 확인해 stale fixture로 테스트가 통과하지 않게 한다.

8. `[Chore] t3micro aggregate required input fail-fast 추가` / 템플릿 `task_request.yml` / 브랜치 `chore/t3micro-aggregate-required-inputs`
   - aggregate report는 capacity/SSE/admission/outbox 입력이 missing이어도 문서를 만든다. 방어 목표용 aggregate에서는 required gate 목록을 지정하고 누락 시 실패해야 한다.

9. `[Perf] overload admission 429 SLO threshold 재보정` / 템플릿 `performance_request.yml` / 브랜치 `perf/overload-admission-429-slo-calibration`
   - 현재 overload/burst 429는 0.7~1.4%인데 threshold는 20%로 넓다. t3.micro 방어 목표에 맞게 정상 방어 범위를 좁히고 503=0은 계속 hard fail로 유지한다.

10. `[Chore] postgres-exporter config mount 정리` / 템플릿 `task_request.yml` / 브랜치 `chore/postgres-exporter-config-mount`
    - PostgreSQL 18 metric 오류는 사라졌지만 `postgres_exporter.yml` missing warn이 남았다. 부하 테스트 로그에서 의미 있는 오류만 남도록 exporter config mount를 명시한다.

## 산출물

- `docs/performance-results/transaction-100m-post-guardrails-retest-vu16-overload-20260427-summary.md`
- `docs/performance-results/transaction-100m-post-guardrails-retest-burst-20260427-summary.md`
- `docs/performance-results/transaction-100m-post-guardrails-retest-prom-vu3-20260427-summary.md`
- `docs/performance-results/t3micro-defensive-gates-post-guardrails-retest-20260427.md`
- `build/reports/k6/transaction-100m-post-guardrails-retest-vu3-ready-20260427-summary.md`
- `build/reports/k6/transaction-100m-post-guardrails-retest-burst-20260427-summary.json`
