# 100m defensive post-capacity latest-main 20260427 bottleneck

## Scope

- base: `main` @ `b4aedd9` (`#456` merge)
- branch: `perf/100m-defensive-post-capacity-retest`
- issue: `#457`
- 목적: `#456` 이후 최신 `main`에서 실제 1억 row 기존 volume 기준 거래 조회와 대용량 트래픽 방어를 재측정하고, 이미 구현된 범위를 제외한 남은 병목 후보를 PR/이슈 단위로 정리합니다.

## Environment

- PostgreSQL container: `aquila-bank-postgres-loadtest`
- PostgreSQL budget: `0.60 CPU`, `384MiB`
- Backend container: `aquila-bank-backend-loadtest`
- Backend budget: `2 CPU`, `1GiB`
- Observability budget:
  - Prometheus: `0.25 CPU`, `256MiB`
  - Grafana: `0.20 CPU`, `256MiB`
  - Alertmanager: `0.10 CPU`, `128MiB`
  - postgres-exporter: `0.10 CPU`, `128MiB`
- dataset estimate:
  - `transaction_read_model`: `50,000,000`
  - `transaction_read_model_archive`: `50,000,028`
- hot account: `910000001`, window `2026-04-01T00:00:00Z..2026-04-30T00:00:00Z`
- cold account: `910000002`, window `2026-01-01T00:00:00Z..2026-01-31T00:00:00Z`

## Resolved From Previous Report

- loadtest PostgreSQL port override: 재생성 후 `15432` 단일 매핑 확인.
- fixture dataset probe: env/manifest 기반 probe로 `build/fixtures/transaction-100m-fixture.dump.dataset.env` 생성 확인.
- fixture docker cp container name: `postgres_container_name` 변수 사용으로 코드상 하드코딩 제거 확인.
- k6 EXPLAIN snapshot: pre/post EXPLAIN 파일 8개 생성 확인.
- k6 scenario env collision: burst run이 실제 `constant-arrival-rate`로 실행됨.
- overload 503 분리: `transaction 503 rate/count`가 summary에 산출됨.
- outbox health: `/actuator/health`에서 outbox `UP` 확인.
- p99 summary: hot/cold custom trend p99 산출 확인.
- postgres-exporter PostgreSQL 18 `stat_bgwriter` noise: 재발 없음.
- observability resource profile: compose budget 출력 및 `docker stats`에서 제한 확인.

## Executed Checks

### Runtime refresh

최신 compose 반영을 위해 loadtest 컨테이너만 재생성했습니다. named volume은 삭제하지 않았습니다.

```text
ports={"5432/tcp":[{"HostIp":"","HostPort":"15432"}]} oom=false status=running
```

### Dataset probe

```text
[transaction-100m-dataset-probe] dataset_env=build/fixtures/transaction-100m-fixture.dump.dataset.env
[transaction-100m-dataset-probe] db_fallback=false
[transaction-100m-dataset-probe] source_order=manifest,env,db-fallback
[transaction-100m-dataset-probe] dataset env written=build/fixtures/transaction-100m-fixture.dump.dataset.env
```

### Initial k6 VU3 after bootJar rebuild

- report: `docs/performance-results/transaction-100m-post-capacity-retest-vu3-20260427-summary.md`
- result: failed semantically, but script exited `0`
- completed iterations: `0`
- interrupted iterations: `3`
- checks rate: `0`
- observed backend log:

```text
NoClassDefFoundError: ch/qos/logback/classic/spi/ThrowableProxy
ClassNotFoundException: ch.qos.logback.classic.spi.ThrowableProxy
```

Root-cause inference: `run-k6-transaction-100m-loadtest.sh` builds `bootJar` while the loadtest backend container may already be running from a previous step. The running JVM reads classes lazily from the mounted jar path, so host-side jar replacement can corrupt the live classpath. After restarting only the backend container, `/actuator/health` and the transaction API returned `200`, and the error did not recur after `2026-04-27T05:47:40Z`.

### Isolated k6 VU3 baseline

- report: `docs/performance-results/transaction-100m-post-capacity-retest-vu3-isolated-20260427-summary.md`
- result: pass
- `http_req_failed`: `0`
- `transaction 429 rate`: `0`
- `transaction 503 rate`: `0`
- p95/p99:
  - hot first: `6.093ms` / `27.138ms`
  - hot cursor: `6.029ms` / `27.148ms`
  - cold first: `5.458ms` / `27.763ms`
  - cold cursor: `5.565ms` / `26.410ms`
- sampled resource:
  - k6 generator: `78.78% CPU`, `22.75MiB`
  - backend: `200.77% CPU`, `391.2MiB / 1GiB`
  - PostgreSQL: `50.08% CPU`, `65.9MiB / 384MiB`

### Isolated k6 VU16 overload

- report: `docs/performance-results/transaction-100m-post-capacity-retest-vu16-overload-20260427-summary.md`
- result: pass
- `http_req_failed`: `0.005822`
- `transaction 429 rate`: `0.005822`
- `transaction 503 rate`: `0`
- `transaction 503 count`: `0`
- p95/p99:
  - hot first: `2.308ms` / `8.818ms`
  - hot cursor: `2.217ms` / `8.815ms`
  - cold first: `2.142ms` / `8.943ms`
  - cold cursor: `2.159ms` / `9.204ms`
- sampled resource:
  - k6 generator: `96.99% CPU`
  - backend: `106.87% CPU`, `399.7MiB / 1GiB`
  - PostgreSQL: `61.71% CPU`, `72.21MiB / 384MiB`

### Burst admission

- report: `docs/performance-results/transaction-100m-post-capacity-retest-burst-20260427-summary.md`
- result: pass
- engine output:

```text
* burst_admission: 256.00 iterations/s for 20s (maxVUs: 64, gracefulStop: 30s)
warning: Insufficient VUs, reached 64 active VUs and cannot initialize more
```

- `http_req_failed`: `0.008126`
- `transaction 429 rate`: `0.008126`
- `transaction 503 rate`: `0`
- p95/p99:
  - hot first: `2.388ms` / `6.821ms`
  - hot cursor: `1.917ms` / `4.572ms`
  - cold first: `1.822ms` / `3.804ms`
  - cold cursor: `1.906ms` / `4.078ms`
- sampled resource:
  - k6 generator: `52.23% CPU`
  - backend: `54.92% CPU`
  - PostgreSQL: `28.72% CPU`

### k6 + Prometheus + Grafana

- report: `docs/performance-results/transaction-100m-post-capacity-retest-prom-vu3-20260427-summary.md`
- result: pass
- `http_req_failed`: `0`
- `transaction 429 rate`: `0`
- `transaction 503 rate`: `0`
- p95/p99:
  - hot first: `2.338ms` / `8.928ms`
  - hot cursor: `2.457ms` / `8.540ms`
  - cold first: `2.323ms` / `6.592ms`
  - cold cursor: `2.338ms` / `7.522ms`
- sampled resource:
  - k6 generator: `101.15% CPU`, `41.87MiB`
  - Grafana: `61.09MiB / 256MiB`
  - Prometheus: `54.14MiB / 256MiB`
  - backend: `112.34% CPU`, `402.2MiB / 1GiB`
  - postgres-exporter: `3.238MiB / 128MiB`
  - PostgreSQL: `61.71% CPU`, `72.2MiB / 384MiB`
- health:
  - `/actuator/health`: `200`
  - Prometheus readiness: `200`
  - Grafana health: `200`

### Aggregate

- report: `docs/performance-results/t3micro-defensive-gates-aggregate-2026-04-27-145635.md`
- current aggregate only links the k6 transaction summary; capacity/SSE/admission/outbox inputs remain `missing`.

## Remaining Bottleneck Candidates

이미 main에 merge된 `#450`, `#456` 구현 범위는 제외했습니다.

1. `[Fix] loadtest bootJar hot-swap으로 인한 backend classpath 손상 방지` / template: `bug_report.yml` / branch: `fix/loadtest-backend-jar-hotswap`
   - backend 컨테이너가 실행 중인 상태에서 host `bootJar`를 다시 만들면 Tomcat worker가 `ThrowableProxy` classpath 오류로 멈출 수 있습니다. runner는 build 전 backend stop 또는 build 후 backend force-recreate를 보장해야 합니다.

2. `[Fix] k6 zero-iteration/no-check run hard fail 추가` / template: `bug_report.yml` / branch: `fix/k6-zero-iteration-hard-fail`
   - 초기 VU3 run은 iteration `0`, checks `0`인데 script exit code가 `0`이었습니다. `iterations > 0`, `checks > 0`, `interrupted_iterations == 0` gate가 필요합니다.

3. `[Perf] burst admission dropped/insufficient VU threshold 추가` / template: `performance_request.yml` / branch: `perf/burst-insufficient-vu-threshold`
   - burst는 실제 constant-arrival-rate로 실행됐지만 `Insufficient VUs` 경고가 있어 generator sizing 병목이 숨어 있습니다. `dropped_iterations` 또는 insufficient VU 로그를 hard fail 조건으로 올려야 합니다.

4. `[Perf] transaction read backend CPU hotpath JFR profile 적용` / template: `performance_request.yml` / branch: `perf/transaction-read-backend-cpu-profile`
   - VU3에서도 backend CPU가 순간 `200.77%`까지 올라갔습니다. DB보다 backend response serialization/compression/security/filter hotpath가 다음 병목 후보입니다.

5. `[Perf] transaction read admission CPU 기반 reject calibration` / template: `performance_request.yml` / branch: `perf/transaction-read-admission-cpu-calibration`
   - VU16/burst에서 429는 약 `0.58%~0.81%`로 낮고 503은 0입니다. backend CPU 보호 목표가 더 엄격하다면 CPU/load 기반 rejection threshold를 재보정해야 합니다.

6. `[Perf] local k6 generator CPU 오염 방지 기본값 강화` / template: `performance_request.yml` / branch: `perf/k6-local-generator-guardrail`
   - local generator가 `78%~101% CPU`를 사용합니다. capacity 판단에서는 off-host/docker-context를 기본으로 강제하고 local mode는 smoke 전용으로 제한해야 합니다.

7. `[Chore] t3micro defensive aggregate에 capacity/SSE/admission/outbox 자동 연결` / template: `task_request.yml` / branch: `chore/t3micro-aggregate-auto-inputs`
   - 현재 aggregate는 k6만 연결되고 capacity/SSE/admission/outbox가 `missing`입니다. 병목 후보 우선순위를 한 파일에서 판단하려면 runner 결과 자동 탐색이 필요합니다.

8. `[Perf] 100m fixture dump artifact 생성/검증 경로 정기화` / template: `performance_request.yml` / branch: `perf/100m-fixture-artifact-scheduled-verify`
   - 이번 실행도 기존 volume에 의존했습니다. fresh volume 재현성을 보장하려면 dump artifact 생성, manifest 검증, restore smoke를 주기적으로 돌려야 합니다.

9. `[Perf] transaction read max latency spike guard 추가` / template: `performance_request.yml` / branch: `perf/transaction-read-max-spike-guard`
   - p95/p99는 낮지만 VU3 hot first max `454.588ms`, burst max `386ms` 수준의 spike가 있습니다. t3.micro 목표에서는 max 또는 p99.9 spike budget을 별도 gate로 둬야 합니다.

10. `[Fix] k6 prometheus mode summary note 정합성 수정` / template: `bug_report.yml` / branch: `fix/k6-prometheus-summary-note`
    - Prometheus mode 결과에도 summary-only 설명 문구가 남습니다. 운영 리포트 해석 오류를 막기 위해 observability mode별 note를 분리해야 합니다.

## Conclusion

- 최신 main에서 1억 row bounded 조회 hot path는 isolated/observability/VU16/burst 모두 p95 수 ms, 503 rate 0으로 통과했습니다.
- 이전 병목 대부분은 해결됐고, 현재 최우선은 SQL index보다 loadtest runner 신뢰도와 backend CPU hotpath입니다.
- 가장 짧은 개선 순서는 `loadtest jar hot-swap 방지` → `k6 zero-iteration hard fail` → `burst insufficient VU gate` → `backend CPU JFR profile`입니다.
