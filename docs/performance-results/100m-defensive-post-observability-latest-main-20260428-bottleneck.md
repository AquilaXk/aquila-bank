# 100m defensive post-observability latest-main 병목 재검증

## 기준

- 실행일: 2026-04-28 KST
- 기준 브랜치: `main`
- 기준 SHA: `0aee83a`
- 작업 브랜치: `perf/100m-defensive-post-observability-retest`
- 연결 이슈: `#487`
- 목표: 로컬 Docker t3.micro 제약에서 1억 row 거래 조회와 대용량 트래픽 방어 병목 재확인
- 제외: 이미 main에 반영된 `#484` off-host capacity 핵심 실행 경로, fixture env 자동 로드, aggregate run scope/grade 분리, `#486` p99.9 spike artifact, burst 429 gate, Prometheus latency correlation

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
| fixture dataset probe | 통과 | total estimate `99,999,884`, hot/cold window count `51/51` | env 자동 로드와 DB gate 정상 |
| VU3 baseline | 통과 | failed `0`, 429 `0`, 503 `0`, p95 약 `1.83~1.92ms` | read API smoke 양호 |
| VU16 overload | 통과 | failed `0.0039936511`, 429 `0.0042366897`, 503 `0`, p95 약 `1.28~1.40ms` | admission guard가 503 없이 방어 |
| burst 256/s, VU64 | 실패 | 503 `0`, 429 `0.0099947118`, dropped `279`, insufficient VUs | 기본 burst headroom 부족 |
| burst 256/s, VU128 | 실패 | 503 `0`, 429 `0.0176892187`, dropped `10`, insufficient VUs | headroom 128도 불충분 |
| burst 256/s, VU256 | 통과 | failed `0.0036452461`, 429 `0.0047889410`, 503 `0`, dropped `0` | app보다 generator/VU sizing 병목 |
| Prometheus mode | 통과 | failed `0`, 429 `0`, 503 `0`, p95 약 `2.00~2.11ms` | remote-write 관측 경로 동작 |
| capacity prerequisite | 실패 예상 | missing `CAPACITY_K6_DOCKER_CONTEXT,CAPACITY_K6_REMOTE_BASE_URL,CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL` | artifact는 남지만 실제 off-host 환경 없음 |
| aggregate required capacity | 실패 의도 확인 | `required aggregate input is missing: capacity` | capacity 누락을 성공으로 오판하지 않음 |
| aggregate k6+memory | 통과 | total memory `645.49MiB`, budget `900MiB` | smoke 집계 기준 통과 |
| aggregate auto input dry-run | 부분 위험 | run-id scope는 stale artifact를 막지만 k6는 failed `burst-vu128`, memory는 missing 선택 | 대표 profile 선택 규칙 미흡 |

## 리소스 스냅샷

| 컨테이너 | CPU | Memory |
| --- | ---: | ---: |
| backend | `3.51%` | `394.90MiB / 1GiB` |
| PostgreSQL | `4.84%` | `75.60MiB / 384MiB` |
| Prometheus | `0.60%` | `75.48MiB / 256MiB` |
| Grafana | `0.02%` | `85.82MiB / 256MiB` |
| postgres-exporter | `1.94%` | `13.69MiB / 128MiB` |

## 병목 판단

최신 main 기준 read API, fixture DB gate, Prometheus 관측 경로는 정상이다. VU3/VU16/Prometheus 모두 503 없이 통과했고 PostgreSQL은 실행 후 `healthy`, `pg_is_in_recovery()=false`, OOM `false` 상태다.

이번 1순위 병목은 burst 256/s generator profile이다. VU64와 VU128은 `Insufficient VUs`와 `dropped_iterations`로 hard gate 실패했지만, VU256에서는 같은 256/s burst가 통과했다. 503은 모든 burst 실험에서 0이므로 앱 장애라기보다 burst profile의 VU headroom과 초반 spike 처리 계약이 병목이다.

2순위 병목은 aggregate auto input의 대표 profile 선택이다. run-id scope는 stale artifact 혼입을 줄였지만, 같은 run-id 안에서 failed `burst-vu128` summary를 대표 k6 input으로 잡고 memory summary를 찾지 못했다. aggregate가 profile grade와 pass/fail 상태를 기준으로 대표 결과를 고르도록 더 좁혀야 한다.

3순위 병목은 실제 off-host capacity 환경이다. runner와 prerequisite artifact는 준비됐지만 현재 로컬 Docker context는 `default`, `desktop-linux`뿐이고, remote backend/Prometheus URL도 없어 capacity/soak baseline은 아직 실행되지 않았다.

## 다음 병목 후보 PR/이슈

모두 issue 제목 = PR 제목, issue 1개 = PR 1개 기준이다. 위 제외 목록의 완료 작업은 중복 등록하지 않았다.

1. `[Perf] burst 256/s 기본 VU headroom 256으로 재보정` / 브랜치 `perf/burst-256-vu-headroom-recalibration`
   - VU64/VU128은 dropped iterations로 실패하고 VU256은 통과했다. 현재 기본 burst profile의 preAllocated/max VUs가 256/s 목표에 부족하다.
2. `[Perf] burst generator headroom preflight 추가` / 브랜치 `perf/burst-generator-headroom-preflight`
   - 실행 후 `Insufficient VUs`로 실패하기 전에 rate/duration/profile 기준 최소 VU headroom을 계산해 fail-fast한다.
3. `[Perf] k6 실패 summary 자동 archive 보강` / 브랜치 `perf/k6-failure-summary-archive`
   - hard gate 실패 시 summary가 자동 archive되지 않아 실패 근거 보존이 수동으로 필요했다.
4. `[Perf] t3micro aggregate k6 대표 profile selector 추가` / 브랜치 `perf/t3micro-aggregate-k6-profile-selector`
   - 같은 run-id 안에서 failed burst summary가 대표 k6 input으로 선택됐다. profile grade, pass/fail, 목적을 기준으로 선택해야 한다.
5. `[Perf] t3micro aggregate memory run-id auto input 보강` / 브랜치 `perf/t3micro-aggregate-memory-run-id-input`
   - run-id scope auto input에서 memory summary가 missing으로 남았다. memory artifact 이름과 auto discovery 계약을 맞춘다.
6. `[Perf] capacity prerequisite failure를 aggregate status로 표시` / 브랜치 `perf/capacity-prerequisite-aggregate-status`
   - prerequisite failure artifact는 생성되지만 aggregate에는 capacity missing만 보인다. missing vars와 failure reason을 gate row에 노출한다.
7. `[Chore] off-host capacity env doctor와 로컬 템플릿 추가` / 브랜치 `chore/offhost-capacity-env-doctor`
   - 실제 capacity 실행에 필요한 Docker context, remote backend, remote Prometheus URL을 로컬에서 한 번에 검증하는 doctor가 필요하다.
8. `[Perf] transaction 100m off-host capacity baseline 실행 리포트 추가` / 브랜치 `perf/transaction-100m-offhost-capacity-baseline-run`
   - 로컬 smoke는 통과했지만 목표 달성 판단에는 off-host generator 기반 capacity/soak baseline 수치가 필요하다.
9. `[Perf] burst first-second spike correlation artifact 추가` / 브랜치 `perf/burst-first-second-spike-correlation`
   - VU64/VU128 실패가 burst 시작 직후 VU 포화와 p99.9 spike로 나타났다. 첫 1~3초 구간의 latency/429/dropped/VU를 별도 artifact로 분리한다.
10. `[Perf] k6 multi-scenario backend reuse runner 추가` / 브랜치 `perf/k6-multi-scenario-backend-reuse`
    - 각 시나리오마다 backend 재기동과 recovery noise window가 반복된다. 같은 stack에서 연속 시나리오를 실행해 측정 시간과 변동성을 줄인다.

## 생성 아티팩트

- `docs/performance-results/k6-smoke/transaction-100m-post-observability-20260428-vu3-summary.md`
- `docs/performance-results/k6-smoke/transaction-100m-post-observability-20260428-vu16-overload-summary.md`
- `docs/performance-results/k6-smoke/transaction-100m-post-observability-20260428-burst-summary.md`
- `docs/performance-results/k6-smoke/transaction-100m-post-observability-20260428-burst-vu128-summary.md`
- `docs/performance-results/k6-smoke/transaction-100m-post-observability-20260428-burst-vu256-summary.md`
- `docs/performance-results/k6-smoke/transaction-100m-post-observability-20260428-prom-vu3-summary.md`
- `docs/performance-results/t3micro-defensive-post-observability-20260428.md`
