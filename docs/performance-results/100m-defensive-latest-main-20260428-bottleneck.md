# 100m defensive latest-main 병목 재검증 20260428

## 기준

- 실행일: 2026-04-28 KST
- 기준 브랜치: `main`
- 기준 SHA: `7a9805c`
- 작업 브랜치: `perf/100m-defensive-latest-main-20260428-retest`
- 연결 이슈: `#481`
- 목표: 로컬 Docker t3.micro 제약에서 1억 row 거래 조회와 대용량 트래픽 방어 병목 재확인
- 제외: 이미 main에 반영된 `#478` fixture DB gate index-only/crash-safe recovery, `#480` off-host capacity preflight/report split, `#476` post-overload-profile 재검증, `#474` zero-503 admission profile, `#472` aggregate full inputs, `#470` off-host runner/burst calibration

## 환경

- Docker compose: `compose.yml`, `compose.t3micro.yml`, `compose.loadtest.yml`
- PostgreSQL 컨테이너: `aquila-bank-postgres-loadtest`, memory limit `384MiB`
- Backend 컨테이너: `aquila-bank-backend-loadtest`, memory limit `1GiB`
- Observability: Prometheus, Grafana, Alertmanager, postgres-exporter
- Dataset account: hot `910000001`, cold `910000002`
- Dataset estimate: total `99,999,884`, hot `49,999,856`, archive `50,000,028`
- Window probe: `index-only-bounded`, limit `51`
- k6 공통 limit: `50`

## 실행 결과

| 항목 | 결과 | 핵심 수치 | 판단 |
| --- | --- | --- | --- |
| stack health | 통과 | backend health 200, Prometheus ready 200, Grafana health 200 | 기본 실행 상태 정상 |
| fixture dataset probe | 통과 | total estimate `99,999,884`, hot/cold window count `51/51`, PostgreSQL OOM `false` | 이전 DB gate 병목은 최신 main에서 해소 |
| VU3 baseline | 통과 | failed `0`, 429 `0`, 503 `0`, p95 약 `1.74~1.80ms` | read API smoke 양호 |
| VU16 overload | 통과 | failed `0.0052672096`, 429 `0.005702338`, 503 `0`, p95 약 `2.15~2.39ms` | admission guard가 503 없이 방어 |
| burst 256/s | 통과 | failed `0.0021078782`, 429 `0.0027500859`, 503 `0`, hot first p95 `2.19317705ms` | 순간 부하 smoke 양호 |
| Prometheus mode | 통과 | failed `0`, 429 `0`, 503 `0`, p95 약 `1.98~2.07ms` | remote-write 관측 경로 동작 |
| capacity prerequisite | 실패 | `CAPACITY_K6_DOCKER_CONTEXT is required` | off-host capacity 실행 환경 미구성 |
| aggregate required capacity | 실패 의도 확인 | `required aggregate input is missing: capacity` | capacity 누락을 성공으로 오판하지 않음 |
| aggregate k6+memory | 통과 | total memory `644.45MiB`, budget `900MiB` | 이번 smoke 집계 기준 통과 |
| aggregate auto input dry-run | 위험 확인 | 2026-04-27 capacity/SSE/admission artifact를 자동 선택 | run-id/date가 다른 stale artifact 혼입 가능 |

## 리소스 스냅샷

| 컨테이너 | CPU | Memory |
| --- | ---: | ---: |
| backend | `0.45%` | `394.00MiB / 1GiB` |
| PostgreSQL | `0.02%` | `75.79MiB / 384MiB` |
| Prometheus | `1.16%` | `74.45MiB / 256MiB` |
| Grafana | `0.05%` | `86.62MiB / 256MiB` |
| postgres-exporter | `0.00%` | `13.59MiB / 128MiB` |

## 병목 판단

최신 main 기준으로 read API와 fixture DB gate는 통과했다. VU3, VU16 overload, burst 256/s, Prometheus remote-write smoke 모두 503 없이 끝났고, 429는 overload/burst threshold 안에서 방어 신호로만 기록됐다. PostgreSQL은 실행 후 `healthy`, `pg_is_in_recovery()=false`, OOM `false` 상태다.

남은 1순위 병목은 실제 capacity 실행 환경이다. 로컬 Docker context는 `default`, `desktop-linux`뿐이라 off-host k6 generator를 실행할 수 없고, `run-transaction-100m-capacity-gates.sh --print-plan`도 `CAPACITY_K6_DOCKER_CONTEXT` 누락에서 fail-fast된다. 따라서 이번 결과는 로컬 smoke 통과이지 실제 off-host capacity baseline 확정은 아니다.

2순위 병목은 aggregate auto input의 run scope다. `T3MICRO_AGGREGATE_AUTO_INPUTS=true` dry-run이 2026-04-27 capacity/SSE/admission artifact와 2026-04-28 k6/memory artifact를 섞어 잡았다. required capacity gate는 실패하지만, required gate를 걸지 않은 리포트에서는 stale artifact 혼입 위험이 남는다.

3순위 병목은 실행 계약 재현성이다. fixture probe는 기존 `dataset.env` 파일이 있어도 자동 source하지 않아 env를 직접 주지 않으면 `hot_account_id is missing`으로 중단된다. 실제 DB 병목은 아니지만 반복 측정 자동화에서는 불필요한 실패 지점이다.

## 다음 병목 후보 PR/이슈

모두 issue 제목 = PR 제목, issue 1개 = PR 1개 기준이다. 위 제외 목록의 완료 작업은 중복 등록하지 않았다.

1. `[Perf] off-host 100m capacity 실행 환경 프로비저닝 추가` / 브랜치 `perf/offhost-100m-capacity-environment`
   - runner 코드는 있지만 현재 로컬에는 `CAPACITY_K6_DOCKER_CONTEXT`, remote backend URL, remote Prometheus write URL이 없어 실제 capacity baseline을 못 닫는다.
2. `[Perf] t3micro aggregate auto input을 run-id scope로 제한` / 브랜치 `perf/t3micro-aggregate-run-id-scoped-inputs`
   - auto input이 2026-04-27 artifact와 2026-04-28 artifact를 섞어 잡는다. 같은 run id 또는 같은 aggregate name prefix만 자동 선택해야 한다.
3. `[Perf] capacity prerequisite failure artifact 문서화` / 브랜치 `perf/capacity-prerequisite-failure-artifact`
   - capacity env 누락은 현재 stderr로만 끝난다. 실패 artifact를 남겨 aggregate/report에서 원인을 추적 가능하게 한다.
4. `[Perf] transaction 100m capacity long-soak baseline 확정` / 브랜치 `perf/transaction-100m-capacity-long-soak-baseline`
   - smoke는 통과했지만 실제 목표에는 off-host generator 기반 장시간 soak baseline이 필요하다.
5. `[Perf] fixture dataset env 자동 로드 추가` / 브랜치 `perf/fixture-dataset-env-auto-load`
   - `build/fixtures/transaction-100m-fixture.dump.dataset.env`가 있어도 명시 env 없이는 probe가 중단된다. 반복 테스트 자동화의 재현성을 높인다.
6. `[Perf] k6 multi-scenario runner 재기동 비용 제거` / 브랜치 `perf/k6-multi-scenario-service-reuse`
   - VU3/VU16/burst/prometheus마다 backend를 재기동해 recovery/noise window 비용이 누적된다. 같은 stack에서 연속 시나리오를 실행하는 runner가 필요하다.
7. `[Perf] transaction read p99.9 spike profile artifact 추가` / 브랜치 `perf/transaction-read-p999-spike-profile`
   - p95는 낮지만 p99.9/max는 30~80ms까지 튄다. DB/GC/connection pool/HTTP thread 중 어느 경계인지 artifact로 분리해야 한다.
8. `[Perf] burst 256/s 429 budget 회귀 gate 강화` / 브랜치 `perf/burst-256-429-budget-regression-gate`
   - burst 429는 threshold 안이지만 0에서 0.275%로 관측됐다. generator/서버 변동성을 고려해 회귀선을 별도 고정한다.
9. `[Perf] Prometheus remote-write 부하와 API latency 상관 리포트 추가` / 브랜치 `perf/prometheus-remote-write-latency-correlation`
   - Prometheus mode p95가 baseline보다 약간 높다. 관측 비용이 API latency에 미치는 영향을 리포트로 분리한다.
10. `[Perf] t3micro capacity와 smoke 결과 등급 분리 gate 추가` / 브랜치 `perf/t3micro-capacity-smoke-grade-separation`
    - 로컬 smoke 통과가 off-host capacity 통과처럼 읽히지 않도록 `smoke`, `capacity`, `soak` 등급을 aggregate status에 명시한다.

## 생성 아티팩트

- `docs/performance-results/k6-smoke/transaction-100m-latest-main-20260428-vu3-summary.md`
- `docs/performance-results/k6-smoke/transaction-100m-latest-main-20260428-vu16-overload-summary.md`
- `docs/performance-results/k6-smoke/transaction-100m-latest-main-20260428-burst-summary.md`
- `docs/performance-results/k6-smoke/transaction-100m-latest-main-20260428-prom-vu3-summary.md`
- `docs/performance-results/t3micro-defensive-latest-main-20260428.md`
