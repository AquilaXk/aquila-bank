# 100m defensive latest-main gate retest bottleneck - 2026-04-27

## Scope

- Issue: #443
- Branch: `perf/100m-defensive-latest-main-gate-retest`
- Base: `6b99855 Merge pull request #442 from AquilaXk/perf/100m-defensive-gates-artifact-readiness`
- Goal: 로컬 Docker t3.micro 기준 1억 건 조회와 대용량 트래픽 방어 gate를 최신 `main`에서 재검증한다.

## Completed Scope Excluded

- #440 `[Perf] 100m defensive latest-main post-recovery local loadtest 병목 리포트`
- #442 `[Perf] 100m defensive gates artifact readiness`

위 범위는 이미 완료된 작업으로 보고, 이번 후보 목록에서 제외한다.

## Environment

- Date: 2026-04-27
- PostgreSQL container: `postgres:18`, `T3MICRO_POSTGRES_MEMORY=384m`
- Backend container: `eclipse-temurin:21-jre`, `T3MICRO_BACKEND_MEMORY=1024m`
- Existing local read-model estimate:

```text
transaction_read_model_estimate         50000000
transaction_read_model_archive_estimate 50000028
```

주의: 로컬 volume에는 1억 건 규모 read model이 남아 있었지만, `build/fixtures/transaction-100m-fixture.dump` artifact는 없었다. 따라서 fresh-volume artifact-ready 검증은 실패하고, existing-volume k6만 실행했다.

## Commands And Results

| Area | Command | Result |
| --- | --- | --- |
| main sync | `git pull --ff-only origin main` | up to date |
| fixture artifact gate | `FIXTURE_ARTIFACT_MIN_ROWS=100000000 tools/test/validate-transaction-100m-fixture-artifact.sh --verify` | failed, dump 없음 |
| artifact-ready k6 | `tools/test/run-transaction-100m-artifact-ready-k6.sh` | failed-fast, dump 없음 |
| capacity smoke | `DOCKER_T3MICRO_RESULT_NAME=docker-t3micro-capacity-latest-main-gate-retest-20260427 SOAK_REPEAT=1 tools/test/run-docker-t3micro-capacity-smoke.sh` | pass |
| SSE matrix | `SSE_RECONNECT_CLIENT_MATRIX=1,3 SSE_RECONNECT_ROUNDS=2 ... run-sse-reconnect-concurrent-user-matrix.sh` | pass |
| production config gate | `tools/test/run-production-high-traffic-config-gate.sh` | pass |
| admission smoke | `ADMISSION_BASE_URL=http://127.0.0.1:8080 ... run-defensive-runtime-http-admission-smoke.sh` | pass with 9/16 rejected |
| existing-volume k6 VU3 | `K6_VUS=3 K6_DURATION=30s ... run-k6-transaction-100m-loadtest.sh --no-up --no-deps` | pass |
| existing-volume k6 VU8 overload | `K6_VUS=8 K6_OVERLOAD_MODE=true K6_DURATION=30s ... run-k6-transaction-100m-loadtest.sh --no-up --no-deps` | pass |
| outbox backlog gate | `run-outbox-provider-backlog-local-gate.sh` and direct gate | blocked by readiness, then 401 |

## Result Artifacts

- [transaction-100m-latest-main-gate-retest-vu3-20260427-summary.md](transaction-100m-latest-main-gate-retest-vu3-20260427-summary.md)
- [transaction-100m-latest-main-gate-retest-vu8-overload-20260427-summary.md](transaction-100m-latest-main-gate-retest-vu8-overload-20260427-summary.md)
- [docker-t3micro-capacity-latest-main-gate-retest-20260427.md](docker-t3micro-capacity-latest-main-gate-retest-20260427.md)
- [sse-reconnect-latest-main-gate-retest-20260427-clients-1.md](sse-reconnect-latest-main-gate-retest-20260427-clients-1.md)
- [sse-reconnect-latest-main-gate-retest-20260427-clients-3.md](sse-reconnect-latest-main-gate-retest-20260427-clients-3.md)
- [admission-latest-main-gate-retest-20260427.md](admission-latest-main-gate-retest-20260427.md)
- [t3micro-defensive-gates-aggregate-latest-main-gate-retest-20260427.md](t3micro-defensive-gates-aggregate-latest-main-gate-retest-20260427.md)

## Key Findings

1. 기존 로컬 volume 기준 1억 건 조회 성능은 병목이 아니다.
   - VU3/30s: `http_req_failed=0`, `checks=1`, hot/cold p95 약 2.5ms.
   - VU8 overload/30s: `http_req_failed=0`, `checks=1`, hot/cold p95 약 6ms.
   - 단, 이 결과는 재생성 가능한 fixture artifact가 아니라 기존 volume 기반이다.

2. fresh-volume 1억 건 재현성은 아직 막혀 있다.
   - `build/fixtures/transaction-100m-fixture.dump`가 없다.
   - artifact-ready gate가 dump/manifest/checksum/Flyway/row distribution 검증 전 실패한다.
   - fresh-volume k6는 안전하게 시작하지 않는 것이 맞다.

3. local loadtest compose는 개발 DB와 포트 충돌이 난다.
   - 기존 `dev-postgres-1`이 host `5432`를 점유했다.
   - `DB_PORT=55432`로 우회 가능했지만, runner 기본값은 병렬 로컬 환경에서 쉽게 실패한다.

4. 재사용 PostgreSQL volume recovery가 runner 실패를 유발한다.
   - PostgreSQL 18 container가 recovery 중 `service_healthy`를 늦게 만족했다.
   - 첫 admission compose 실행은 dependency 단계에서 조기 실패했다.

5. backend readiness gate가 운영 상태와 테스트 readiness를 혼합한다.
   - `/actuator/health/readiness`는 401로 응답했다.
   - `/actuator/health`는 DB는 UP이지만 outbox lag 때문에 503이었다.
   - `readinessState=UP`이어도 runner는 backend ready로 판단하지 못했다.

6. outbox backlog local gate는 인증 경로가 맞지 않는다.
   - runner는 service JWT를 발급해 `Authorization: Bearer`로 호출한다.
   - compose backend는 `OUTBOX_OPS_ENABLED=true`였지만 direct gate는 401을 반환했다.
   - `X-Outbox-Ops-Token` legacy token도 401이었다.

7. outbox backlog가 health와 로그를 오염시킨다.
   - 재사용 volume에서 outbox lag가 약 28,000초로 측정됐다.
   - backend log는 outbox event publish INFO가 초당 대량으로 출력됐다.
   - t3.micro 부하 테스트에서 로그 IO와 health 503이 노이즈가 된다.

8. t3.micro telemetry archive가 peak 값을 놓친다.
   - stdout에는 Docker stats sample이 찍혔지만 archive 문서는 `statsSamples=0`, `peakCpuPercent=0.00`, `peakMemoryMiB=0.00`으로 기록됐다.
   - aggregate report도 peak 값을 `missing`으로 표시했다.

## Next PR/Issue Candidates

1. `[Perf] 100m existing volume fixture dump export 추가`
   - Template: `performance_request.yml`
   - Branch: `perf/100m-existing-volume-fixture-export`
   - Existing local 100m volume을 dump/manifest/checksum으로 export해 artifact-ready k6와 fresh-volume restore를 재현 가능하게 만든다.

2. `[Fix] loadtest compose host port isolation 추가`
   - Template: `bug_report.yml`
   - Branch: `fix/loadtest-compose-port-isolation`
   - `DB_PORT`, `BACKEND_PORT`, exporter/grafana/prometheus port를 runner 이름별로 격리하고 기존 dev DB와 충돌하지 않게 한다.

3. `[Fix] actuator readiness path 보안 허용 및 runner readiness 판정 수정`
   - Template: `bug_report.yml`
   - Branch: `fix/loadtest-readiness-health-group`
   - `/actuator/health/readiness`를 permit하거나 runner가 허용된 readiness endpoint를 사용하게 하고, outbox health 503과 앱 accept 상태를 분리한다.

4. `[Perf] loadtest PostgreSQL recovery wait/backoff 개선`
   - Template: `performance_request.yml`
   - Branch: `perf/loadtest-postgres-recovery-wait`
   - recovery 중인 PostgreSQL을 dependency failure로 조기 종료하지 않고, recovery 로그/시간/최종 health를 리포트에 남긴다.

5. `[Fix] outbox backlog local gate service JWT 인증 정합성 수정`
   - Template: `bug_report.yml`
   - Branch: `fix/outbox-backlog-local-service-token`
   - `issue-internal-service-token.sh`, compose env, `InternalServiceTokenVerifier` 기준을 맞춰 local outbox gate 401을 제거한다.

6. `[Perf] loadtest outbox backlog cleanup/recovery preflight 추가`
   - Template: `performance_request.yml`
   - Branch: `perf/loadtest-outbox-backlog-preflight`
   - k6/admission 전 outbox lag를 정리하거나 별도 threshold로 기록해 health 503과 로그 폭주가 조회 부하 측정을 오염시키지 않게 한다.

7. `[Perf] outbox publish INFO log rate limit 적용`
   - Template: `performance_request.yml`
   - Branch: `perf/outbox-publish-log-rate-limit`
   - 대량 backlog 처리 시 event마다 INFO를 남기지 않고 sampled/debug/metric 중심으로 전환해 t3.micro IO/CPU 노이즈를 줄인다.

8. `[Fix] t3.micro telemetry stats archive peak parsing 수정`
   - Template: `bug_report.yml`
   - Branch: `fix/t3micro-telemetry-peak-archive`
   - Docker stats sample이 stdout에는 존재하지만 archive/aggregate에는 0 또는 missing으로 남는 문제를 수정한다.

9. `[Perf] k6 burst admission scenario 추가`
   - Template: `performance_request.yml`
   - Branch: `perf/k6-burst-admission-scenario`
   - 현재 VU8 constant-vus는 429가 0이고 direct burst는 9/16 429다. burst/constant-arrival-rate 시나리오를 추가해 admission guard를 k6에서도 재현한다.

10. `[Perf] 100m k6 existing-volume EXPLAIN snapshot 자동 첨부`
    - Template: `performance_request.yml`
    - Branch: `perf/100m-k6-explain-snapshot`
    - k6 실행 전후 hot/cold first/cursor query plan을 함께 보관해 p95가 좋아도 plan regression을 추적한다.

11. `[Perf] loadtest aggregate report에 k6/admission/outbox 상태 통합`
    - Template: `performance_request.yml`
    - Branch: `perf/loadtest-aggregate-k6-admission-outbox`
    - aggregate report가 capacity/SSE 중심이라 k6 p95, 429 rate, outbox gate 실패 원인을 한 표로 묶지 못한다.

12. `[Ops] loadtest container lifecycle safe cleanup 명령 추가`
    - Template: `task_request.yml`
    - Branch: `ops/loadtest-container-safe-cleanup`
    - 테스트 전용 container/network만 정리하고 volume 삭제는 별도 confirm을 요구하는 cleanup 경로를 제공한다.

## Current Priority

최단 시간 병목 제거 순서는 `1 -> 3 -> 5 -> 8 -> 9`다. 조회 API 자체보다 재현성, readiness, outbox ops, telemetry가 다음 검증 루프를 막고 있다.
