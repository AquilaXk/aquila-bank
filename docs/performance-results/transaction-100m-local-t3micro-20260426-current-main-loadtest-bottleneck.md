# Transaction 100m local t3.micro current-main loadtest bottleneck

## Scope

- date: 2026-04-26 KST
- issue: #388
- branch: `perf/transaction-100m-current-main-loadtest-report`
- base main sha: `3ac145e`
- goal: 최신 `main` 기준 1억 row 거래 조회와 대용량 HTTP 부하를 로컬 Docker t3.micro 예산에서 재측정하고, 이미 구현된 작업을 제외한 병목 후보를 issue/PR 단위로 정리

## Latest Main Check

- `git fetch origin main`
- `git switch main`
- `git pull --ff-only origin main`: already up to date
- `git switch perf/transaction-100m-current-main-loadtest-report`
- `git rebase main`: current branch up to date
- backend build: `tools/test/with-resource-lock.sh back-gradle-current-main-loadtest-bootjar ./back/gradlew -p back bootJar`
- build result: `BUILD SUCCESSFUL`

## Environment

- compose: `compose.yml`, `compose.t3micro.yml`, `compose.loadtest.yml`
- PostgreSQL: `PostgreSQL 18.3`
- fixture migration state: Flyway success `52`
- dataset estimate: hot `transaction_read_model` 50,000,000 rows, archive `transaction_read_model_archive` 50,000,028 rows
- hot account: `910000001`, window `2026-04-01T00:00:00Z..2026-04-30T00:00:00Z`
- cold account: `910000002`, window `2026-01-01T00:00:00Z..2026-01-31T00:00:00Z`
- PostgreSQL budget: memory `384MiB`, CPU `0.6`, health healthy, OOM false, restart `0`
- backend loadtest budget: memory `1GiB`, CPU `2`, DB pool `4`, Tomcat max threads `16`

Constraint:

- PostgreSQL은 t3.micro 근사 예산이지만, backend loadtest container는 기본 `2 CPU`와 `1GiB`이다. 단일 t3.micro 전체 예산 판정은 #382 capacity gate를 실제 실행해 별도로 닫아야 한다.

## Runs

### Run A: default guard, VU=8

Command:

```bash
K6_REPORT_NAME=transaction-100m-current-main-default-vu8-20260426 \
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

- report: `docs/performance-results/transaction-100m-current-main-default-vu8-20260426-summary.md`
- result: failed threshold
- admission: `OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=3`
- `http_req_failed`: `0.7235015341616794`
- `checks`: `0.43321394147815845`
- transaction 429 rate: `0.7235015341616794`
- p95: hot first 2.12ms, hot cursor 2.83ms, cold first 3.05ms, cold cursor 3.05ms

Observed sample:

- k6 CPU 68.73%, memory 40.62MiB
- backend CPU 201.29%, memory 377.4MiB / 1GiB
- PostgreSQL CPU 25.94%, memory 49.7MiB / 384MiB

Conclusion:

- 최신 `main`에서도 default guard의 첫 병목은 DB가 아니라 transaction read admission guard이다.
- p95가 낮고 실패율만 높은 형태라 1억 row index 조회 자체는 정상이며, 대부분 요청이 429로 fail-fast 처리됐다.

### Run B: raised guard, VU=8

Setup:

```bash
OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=8 \
docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml \
  --profile loadtest up -d --force-recreate aquila-bank-backend
```

Command:

```bash
K6_REPORT_NAME=transaction-100m-current-main-admission8-vu8-20260426 \
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

- report: `docs/performance-results/transaction-100m-current-main-admission8-vu8-20260426-summary.md`
- result: passed
- admission: `OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=8`
- `http_req_failed`: `0`
- `checks`: `1`
- transaction 429 rate: `0`
- p95: hot first 6.46ms, hot cursor 6.85ms, cold first 5.96ms, cold cursor 6.78ms
- completed iterations: 29,240/min

Observed samples:

- sample A: k6 CPU 67.20%, backend CPU 203.87%, backend memory 381.6MiB, PostgreSQL CPU 39.99%, PostgreSQL memory 55.8MiB
- sample B: k6 CPU 91.51%, backend CPU 110.43%, backend memory 391.7MiB, PostgreSQL CPU 61.41%, PostgreSQL memory 56.24MiB

Conclusion:

- guard를 8로 올려도 hot/archive 1억 row 조회 p95는 7ms 미만으로 안정적이다.
- PostgreSQL memory/OOM은 병목이 아니며, 다음 신호는 backend CPU, PostgreSQL CPU, k6 client CPU가 섞인 실행 예산이다.

### Run C: raised guard, VU=16 overload

Command:

```bash
K6_REPORT_NAME=transaction-100m-current-main-admission8-vu16-overload-20260426 \
K6_HOT_ACCOUNT_ID=910000001 \
K6_HOT_FROM=2026-04-01T00:00:00Z \
K6_HOT_TO=2026-04-30T00:00:00Z \
K6_COLD_ACCOUNT_ID=910000002 \
K6_COLD_FROM=2026-01-01T00:00:00Z \
K6_COLD_TO=2026-01-31T00:00:00Z \
K6_VUS=16 \
K6_DURATION=1m \
K6_OVERLOAD_MODE=true \
tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps
```

Summary:

- report: `docs/performance-results/transaction-100m-current-main-admission8-vu16-overload-20260426-summary.md`
- result: passed
- admission: `OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=8`
- overload mode: `true`
- `http_req_failed`: `0.003796236980093482`
- `checks`: `1`
- transaction 429 rate: `0.003796236980093482`
- p95: hot first 4.75ms, hot cursor 4.83ms, cold first 4.71ms, cold cursor 4.98ms
- completed iterations: 31,916/min

Observed samples:

- sample A: k6 CPU 107.09%, backend CPU 119.47%, backend memory 397.7MiB, PostgreSQL CPU 62.32%, PostgreSQL memory 56.61MiB
- sample B: k6 CPU 77.61%, backend CPU 113.75%, backend memory 401.5MiB, PostgreSQL CPU 61.36%, PostgreSQL memory 56.99MiB

Conclusion:

- #380의 overload mode와 `Retry-After` backoff 적용 후 VU=16은 stacktrace flood 없이 통과한다.
- 이전 post-optimization VU=16의 429 대량 실패는 DB 병목이 아니라 guard 초과 트래픽과 k6 failure 처리 노이즈였다.
- overload mode가 성공하더라도 429 비율 상한을 따로 두지 않으면 과도한 reject를 성공으로 볼 수 있으므로 별도 gate가 필요하다.

## Bottleneck Order

1. Default t3.micro read traffic: `OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=3`이 첫 병목이자 보호 장치이다.
2. Raised guard VU=8: 1억 row hot/archive query p95는 7ms 미만으로 정상이며, PostgreSQL memory/OOM 병목은 재현되지 않았다.
3. Raised guard VU=8 resource split: backend CPU와 PostgreSQL CPU가 동시에 의미 있는 수준까지 올라가며, k6 client CPU도 같은 로컬 호스트에서 측정값을 오염시킬 수 있다.
4. Raised guard VU=16 overload: #380 이후 429 처리 노이즈는 해소됐고, 남은 문제는 429 허용률을 어느 수준까지 성공으로 볼지 정하는 것이다.
5. Capacity decision: #382, #383, #384가 도구를 추가했으므로 다음 병목 제거는 새 도구 추가보다 실제 1억 fixture에서 결과를 고정하고 운영값으로 반영하는 쪽이 빠르다.

## Excluded Completed Work

- #366 `[Fix] transaction 100m k6-only preflight false-negative 수정`
- #367 `[Perf] transaction read backend CPU hot path profiling gate 추가`
- #368 `[Perf] transaction read response mapping/serialization CPU 절감`
- #369 `[Perf] transaction read admission/Hikari/DB pool matrix benchmark 추가`
- #370 `[Perf] transaction read admission budget 정책을 측정값 기반으로 보정`
- #378 `[Perf] transaction 100m post-optimization loadtest 병목 리포트`
- #380 `[Perf] transaction k6 overload mode Retry-After backoff 추가`
- #382 `[Perf] transaction 100m capacity gate 묶음 추가`
- #383 `[Perf] transaction 100m workload/profile benchmark 묶음 추가`
- #384 `[Perf] transaction read SLO dashboard 및 replica offload 검증 추가`

## Candidate Issue/PR List

1. `[Perf] transaction 100m capacity gate 결과 리포트 추가` / template: `performance_request.yml` / branch: `perf/transaction-100m-capacity-gate-results`
   - #382는 runner를 추가했다. 현재 필요한 작업은 같은 1억 fixture에서 single-host, CPU split, 30분 long soak를 실제 실행하고 `capacity-summary.tsv`와 해석 문서를 남기는 것이다.

2. `[Perf] transaction 100m workload/profile benchmark 결과 리포트 추가` / template: `performance_request.yml` / branch: `perf/transaction-100m-workload-profile-results`
   - #383은 weighted workload, compression benchmark, JDBC mapper allocation, fixture restore runner를 추가했다. 아직 실제 결과와 우선순위 결정 문서가 없으므로 실행 결과를 먼저 고정한다.

3. `[Perf] transaction 100m capacity gate hard SLO threshold 추가` / template: `performance_request.yml` / branch: `perf/transaction-100m-capacity-hard-threshold`
   - 현재 capacity runner는 p95, 429, CPU, Hikari 값을 TSV로 남기지만 runner 자체의 hard pass/fail 기준은 약하다. 목표 초과를 PR/CI에서 바로 실패시키는 SLO threshold를 추가한다.

4. `[Perf] transaction read adaptive admission control 적용` / template: `performance_request.yml` / branch: `perf/transaction-read-adaptive-admission-control`
   - default `max=3`은 안전하지만 VU=8 결과 기준으로 여유 용량을 남긴다. CPU/Hikari/429 신호가 건강할 때는 상한을 높이고 포화 시 줄이는 bounded adaptive admission으로 t3.micro 보호와 처리량을 동시에 맞춘다.

5. `[Perf] transaction read overload 429 ratio gate 추가` / template: `performance_request.yml` / branch: `perf/transaction-read-overload-429-ratio-gate`
   - overload mode는 정상 reject를 허용하지만 reject 비율 상한이 없으면 과부하를 성공으로 오판할 수 있다. VU=16 같은 보호 실험에 `transaction_429_rate` 상한을 별도 threshold로 둔다.

6. `[Perf] transaction read remote k6 load generator 분리` / template: `performance_request.yml` / branch: `perf/transaction-read-remote-k6-generator`
   - 이번 실행에서 k6 CPU가 67%에서 107%까지 올라갔다. backend/PostgreSQL과 같은 로컬 호스트에서 부하 생성기가 CPU를 공유하므로, 원격 k6 또는 별도 Docker context 실행 경로를 추가해 서버 병목만 분리한다.

7. `[Perf] transaction 100m cold-start cache-warm SLO gate 추가` / template: `performance_request.yml` / branch: `perf/transaction-100m-cold-start-cache-warm-gate`
   - 현재 결과는 이미 준비된 fixture와 warm cache 영향을 받을 수 있다. PostgreSQL/backend 재기동 직후 cold read와 warm-up 이후 read를 분리해 배포 직후 p95 악화를 검증한다.

8. `[Perf] transaction read compression 운영 기본값 보정` / template: `performance_request.yml` / branch: `perf/transaction-read-compression-config-tune`
   - compression benchmark runner는 있지만 운영 기본값 결정은 아직 남아 있다. 50-row JSON 응답에서 CPU 비용이 더 큰지 측정하고 t3.micro high-traffic profile의 compression threshold를 보정한다.

9. `[Perf] transaction read JDBC mapper low-allocation 최적화` / template: `performance_request.yml` / branch: `perf/transaction-read-jdbc-mapper-low-allocation`
   - JFR mapper allocation runner는 준비됐다. 실제 profile에서 enum/time conversion이나 row object 생성이 hot path로 확인될 때만 low-allocation mapper로 줄인다.

10. `[Perf] transaction 100m HTTP read/write interference gate 추가` / template: `performance_request.yml` / branch: `perf/transaction-100m-read-write-interference-gate`
    - 현재 k6는 transaction read 전용이다. transfer write, outbox, notification 소비가 같이 도는 조건에서 1억 row read p95와 429 비율을 검증해야 대용량 트래픽 운영 병목을 더 정확히 잡을 수 있다.

## Double Check

- `rg`로 최신 main의 `tools/test`, `docs/agent`, `docs/performance-results`, `ops/k6`, `back/src/main/resources`를 확인했다.
- 이미 구현된 `Retry-After` backoff, single-host/cpu-split/long-soak capacity runner, weighted/compression/JDBC/fixture restore runner, accepted/rejected SLO dashboard, replica offload runner는 후보에서 제외했다.
- 위 후보는 기존 도구를 실제 결과와 운영 판단으로 닫거나, 현재 실행에서 새로 드러난 reject threshold, client isolation, cold-start, read/write interference 병목을 분리하는 작업이다.

## Final State

- backend container restored to default `OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=3`
- PostgreSQL remained healthy, OOM false
- no application code or migration changed
