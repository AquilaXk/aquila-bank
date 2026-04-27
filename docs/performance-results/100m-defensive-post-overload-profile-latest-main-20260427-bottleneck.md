# 100m defensive post-overload-profile latest-main 병목 재검증

## 기준

- 실행일: 2026-04-27
- 기준 브랜치: `main`
- 기준 SHA: `686d178`
- 작업 브랜치: `perf/100m-defensive-post-overload-profile-retest`
- 연결 이슈: `#475`
- 목표: 로컬 Docker t3.micro 제약에서 1억 row 거래 조회와 대용량 트래픽 방어 병목 재확인
- 제외: 이미 main에 반영된 `#466`, `#470`, `#472`, `#474` 범위의 gate 안정화, overload 429/503 분리, burst hot-first spike gate, zero-503 admission profile

## 환경

- Docker compose: `compose.yml`, `compose.t3micro.yml`, `compose.loadtest.yml`
- PostgreSQL 컨테이너: `aquila-bank-postgres-loadtest`, memory limit `384MiB`
- Backend 컨테이너: `aquila-bank-backend-loadtest`, memory limit `1GiB`
- Observability: Prometheus, Grafana, Alertmanager, postgres-exporter
- Dataset account: hot `910000001`, cold `910000002`
- Dataset estimate: fixture probe 기준 `99,999,884` rows, tolerance `1,000` rows로 1억 row 조건 수용
- k6 공통 limit: `50`

## 실행 결과

| 항목 | 결과 | 핵심 수치 | 판단 |
| --- | --- | --- | --- |
| fixture dataset probe | 실패 | estimate는 통과, hot/cold window count 중 statement timeout 및 PostgreSQL backend `signal 9` 종료 | DB gate 쿼리가 t3.micro에서 아직 위험 |
| VU3 baseline | 통과 | failed `0`, 429 `0`, 503 `0`, hot/cold p95 약 `1.18~1.24ms` | read API smoke 기준 양호 |
| VU16 overload | 통과 | failed `0.0035115521`, 429 `0.0039591095`, 503 `0`, p95 약 `1.16~1.25ms` | admission guard가 503 없이 방어 |
| burst 256/s | 통과 | failed `0`, 429 `0`, 503 `0`, hot first p95 `0.919959ms` | 짧은 burst smoke 기준 양호 |
| Prometheus mode | 통과 | failed `0`, 429 `0`, 503 `0`, p95 약 `1.15~1.28ms` | remote write 경로 동작 |
| Prometheus/Grafana health | 통과 | Prometheus `/-/ready` 200, Grafana `/api/health` 200 | 관측 스택 기동 정상 |
| capacity runner | 차단 | `CAPACITY_K6_DOCKER_CONTEXT is required`, Docker context는 `default`, `desktop-linux`만 존재 | off-host k6 context 미구성 |
| aggregate required capacity | 실패 의도 확인 | capacity input missing일 때 fail-fast | capacity 누락을 성공으로 오판하지 않음 |
| aggregate k6+memory | 통과 | total memory `743.13MiB`, budget `900MiB` | smoke 집계는 통과 |

## 리소스 스냅샷

| 컨테이너 | CPU | Memory |
| --- | ---: | ---: |
| backend | `0.38%` | `394.7MiB / 1GiB` |
| PostgreSQL | `0.87%` | `184.1MiB / 384MiB` |
| Prometheus | `0.38%` | `63.65MiB / 256MiB` |
| Grafana | `0.05%` | `86.09MiB / 256MiB` |
| postgres-exporter | `0.00%` | `14.59MiB / 128MiB` |

## 병목 판단

현재 read API 자체는 최신 main 기준으로 smoke, overload, burst에서 병목이 크게 완화됐다. 특히 `#474` 이후 overload의 503은 재현되지 않았고, 429도 VU16 overload에서 `0.3959%`로 gate 안에 들어왔다.

남은 1순위 병목은 API가 아니라 1억 row fixture 검증 DB gate다. estimate는 통과하지만 window count가 t3.micro PostgreSQL `384MiB`에서 timeout과 backend kill을 유발한다. 이 상태에서는 부하 테스트 본 실행 전 검증 단계가 DB를 recovery 상태로 만들 수 있어, 실제 capacity 결과와 fixture 검증 장애가 섞인다.

2순위 병목은 capacity runner의 실행 환경이다. 로컬 Docker 컨텍스트만 있어 off-host k6 capacity runner가 실행되지 못했고, full defensive aggregate는 capacity 입력 없이 완료로 볼 수 없다.

## 다음 병목 후보 PR/이슈

모두 issue 제목 = PR 제목, issue 1개 = PR 1개 기준이다. 이미 main에 반영된 gate 안정화, overload 429/503 분리, burst hot-first spike gate, zero-503 admission profile 작업은 제외했다.

1. `[Perf] fixture DB window count를 index-only bounded probe로 전환` / 브랜치 `perf/fixture-db-window-index-only-probe`
   - 현재 window count가 timeout과 PostgreSQL backend kill을 유발한다. full count 성격의 검증을 index-only sample/range probe로 바꿔야 한다.
2. `[Perf] fixture DB gate crash-safe timeout/connection recovery 처리 추가` / 브랜치 `perf/fixture-db-gate-crash-safe-recovery`
   - 검증 쿼리 실패 시 DB recovery와 후속 k6 결과가 섞이지 않도록 fail-fast, recovery wait, artifact 분리를 추가한다.
3. `[Perf] transaction 100m fixture manifest/db-gate artifact 복구` / 브랜치 `perf/transaction-100m-fixture-manifest-gate-artifact`
   - 준비된 fixture manifest와 db-gate env를 우선 사용해 런타임 DB count 의존도를 낮춘다.
4. `[Chore] off-host k6 docker context capacity runner 구성` / 브랜치 `chore/offhost-k6-capacity-runner`
   - `CAPACITY_K6_DOCKER_CONTEXT`가 없어 실제 capacity runner가 차단된다.
5. `[Perf] capacity runner remote URL/prometheus preflight 실환경 연결` / 브랜치 `perf/capacity-runner-remote-preflight-connectivity`
   - remote base URL과 Prometheus remote write URL을 preflight로 검증해 capacity 실행 전 환경 오류를 닫는다.
6. `[Perf] capacity gate를 실제 off-host 1억 row 결과로 baseline 확정` / 브랜치 `perf/offhost-capacity-baseline-100m`
   - 로컬 smoke 통과와 별개로 off-host k6 capacity 수치를 baseline으로 고정해야 한다.
7. `[Chore] defensive aggregate capacity input 자동 연결` / 브랜치 `chore/defensive-aggregate-capacity-input`
   - capacity 결과가 생성되면 aggregate가 자동으로 최신 capacity input을 연결하고 누락 시 실패하도록 고정한다.
8. `[Perf] PostgreSQL t3.micro 검증 쿼리 memory/IO SLO 추가` / 브랜치 `perf/postgres-verification-query-slo`
   - fixture 검증 쿼리가 앱 부하보다 DB를 먼저 죽이는 문제를 막기 위해 검증 쿼리 자체의 memory/IO budget을 둔다.
9. `[Perf] transaction read local smoke와 capacity 결과 분리 리포트 추가` / 브랜치 `perf/transaction-read-smoke-capacity-report-split`
   - smoke 결과를 capacity 결과로 오판하지 않도록 리포트와 gate status를 분리한다.
10. `[Perf] post-crash recovery noise를 loadtest 결과에서 자동 격리` / 브랜치 `perf/loadtest-post-crash-noise-isolation`
    - DB crash/recovery 뒤 exporter/log noise가 후속 측정에 섞이지 않도록 run id와 recovery window 기준으로 격리한다.

## 생성 아티팩트

- `docs/performance-results/transaction-100m-post-overload-profile-vu3-20260427-summary.md`
- `docs/performance-results/transaction-100m-post-overload-profile-vu16-overload-20260427-summary.md`
- `docs/performance-results/transaction-100m-post-overload-profile-burst-20260427-summary.md`
- `docs/performance-results/transaction-100m-post-overload-profile-prom-vu3-20260427-summary.md`
- `docs/performance-results/t3micro-defensive-gates-post-overload-profile-20260427.md`
