# Transaction 100m local t3.micro post-improvement current-main bottleneck

## Scope

- date: 2026-04-26 KST
- issue: #410
- branch: `perf/transaction-100m-post-improvement-loadtest-report`
- base main sha: `62186c1`
- goal: #391~#409 성능 개선이 merge된 최신 `main` 기준으로 1억 row transaction read와 대용량 트래픽 병목을 다시 측정하고, 이미 구현된 작업을 제외한 다음 issue/PR 후보를 정리

## Latest Main Check

- `git pull --ff-only origin main`: already up to date
- recent completed PRs now in main:
  - #391 `[Perf] transaction 100m capacity gate 결과 리포트 추가`
  - #393 `[Perf] transaction 100m workload/profile benchmark 결과 리포트 추가`
  - #395 `[Perf] transaction 100m capacity gate hard SLO threshold 추가`
  - #397 `[Perf] transaction read adaptive admission control 적용`
  - #399 `[Perf] transaction read overload 429 ratio gate 추가`
  - #401 `[Perf] transaction read remote k6 load generator 분리`
  - #403 `[Perf] transaction 100m cold-start cache-warm SLO gate 추가`
  - #405 `[Perf] transaction read compression 운영 기본값 보정`
  - #407 `[Perf] transaction read JDBC mapper low-allocation 최적화`
  - #409 `[Perf] transaction 100m HTTP read/write interference gate 추가`
- backend build: `tools/test/with-resource-lock.sh back-gradle-post-improvement-loadtest-bootjar ./back/gradlew -p back bootJar`
- build result: `BUILD SUCCESSFUL`

## Environment

- compose: `compose.yml`, `compose.t3micro.yml`, `compose.loadtest.yml`
- PostgreSQL: `PostgreSQL 18.3`
- fixture migration state: Flyway success `52`
- dataset estimate: hot `transaction_read_model` 50,000,000 rows, archive `transaction_read_model_archive` 50,000,028 rows
- hot account: `910000001`, window `2026-04-01T00:00:00Z..2026-04-30T00:00:00Z`
- cold account: `910000002`, window `2026-01-01T00:00:00Z..2026-01-31T00:00:00Z`
- PostgreSQL budget: memory `384MiB`, CPU `0.6`, health healthy, OOM false
- backend loadtest budget: memory `1GiB`, CPU `2`
- default transaction read admission: initial `3`, adaptive min `3`, adaptive max `8`

Constraint:

- read-only k6는 local generator라 backend/PostgreSQL/k6가 같은 host CPU를 공유한다. #401의 remote generator mode는 구현됐지만 이 실행에서는 별도 Docker context가 없어 사용하지 않았다.

## Runs

### Run A: default adaptive admission, VU=8, strict mode

Command:

```bash
K6_REPORT_NAME=transaction-100m-postimprove-adaptive-vu8-20260426 \
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

- report: `docs/performance-results/transaction-100m-postimprove-adaptive-vu8-20260426-summary.md`
- result: failed threshold, exit `99`
- overload mode: `false`
- `http_req_failed`: `0.7183763982652388`
- `checks`: `0.4394794249318837`
- transaction 429 rate: `0.7183763982652388`
- p95: hot first 2.39ms, hot cursor 3.18ms, cold first 3.34ms, cold cursor 3.46ms

Observed sample:

- backend CPU 204.68%, memory 373.9MiB / 1GiB
- PostgreSQL CPU 27.56%, memory 49.61MiB / 384MiB

Conclusion:

- strict mode의 첫 병목은 DB가 아니라 admission 429와 k6 stacktrace flood이다.
- adaptive admission은 429를 받으면 limit을 min으로 낮추므로, client backoff 없는 strict VU=8에서는 회복보다 reject가 지배적이다.

### Run B: default adaptive admission, VU=8, overload mode

Command:

```bash
K6_REPORT_NAME=transaction-100m-postimprove-adaptive-vu8-overload-20260426 \
K6_HOT_ACCOUNT_ID=910000001 \
K6_HOT_FROM=2026-04-01T00:00:00Z \
K6_HOT_TO=2026-04-30T00:00:00Z \
K6_COLD_ACCOUNT_ID=910000002 \
K6_COLD_FROM=2026-01-01T00:00:00Z \
K6_COLD_TO=2026-01-31T00:00:00Z \
K6_VUS=8 \
K6_DURATION=1m \
K6_OVERLOAD_MODE=true \
K6_OVERLOAD_429_RATE_THRESHOLD=0.05 \
tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps
```

Summary:

- report: `docs/performance-results/transaction-100m-postimprove-adaptive-vu8-overload-20260426-summary.md`
- result: passed
- overload mode: `true`
- `http_req_failed`: `0.00003603837366027346`
- `checks`: `1`
- transaction 429 rate: `0.00003603837366027346`
- p95: hot first 4.15ms, hot cursor 4.01ms, cold first 4.05ms, cold cursor 4.04ms
- completed iterations: 34,689/min

Observed sample:

- backend CPU 118.72%, memory 399.8MiB / 1GiB
- PostgreSQL CPU 60.53%, memory 56.18MiB / 384MiB

Conclusion:

- overload mode와 `Retry-After` backoff가 있으면 VU=8은 429 ratio gate 5%를 충분히 통과한다.
- query p95는 5ms 미만이며 1억 row read SQL/index 병목은 보이지 않는다.

### Run C: default adaptive admission, VU=16, overload mode

Command:

```bash
K6_REPORT_NAME=transaction-100m-postimprove-adaptive-vu16-overload-20260426 \
K6_HOT_ACCOUNT_ID=910000001 \
K6_HOT_FROM=2026-04-01T00:00:00Z \
K6_HOT_TO=2026-04-30T00:00:00Z \
K6_COLD_ACCOUNT_ID=910000002 \
K6_COLD_FROM=2026-01-01T00:00:00Z \
K6_COLD_TO=2026-01-31T00:00:00Z \
K6_VUS=16 \
K6_DURATION=1m \
K6_OVERLOAD_MODE=true \
K6_OVERLOAD_429_RATE_THRESHOLD=0.05 \
tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps
```

Summary:

- report: `docs/performance-results/transaction-100m-postimprove-adaptive-vu16-overload-20260426-summary.md`
- result: passed
- overload mode: `true`
- `http_req_failed`: `0.004112947876295183`
- `checks`: `1`
- transaction 429 rate: `0.004112947876295183`
- p95: hot first 1.27ms, hot cursor 1.18ms, cold first 1.13ms, cold cursor 1.16ms
- completed iterations: 47,823/min

Observed sample:

- backend CPU 97.25%, memory 403.3MiB / 1GiB
- PostgreSQL CPU 61.82%, memory 56.89MiB / 384MiB

Conclusion:

- VU=16도 429 ratio 0.42%로 5% gate를 통과했다.
- accepted request latency는 매우 낮고, 남은 read-only 병목은 DB 구조가 아니라 traffic mode 계약, client isolation, CPU budget 판정이다.

### Run D: read/write interference gate attempt

Preparation:

- 현재 1억 read fixture에는 `bank_account`, `ledger_entry`, `account_balance_snapshot` row가 없었다.
- gate가 `INTERFERENCE_WRITE_SOURCE_ACCOUNT_ID`, `INTERFERENCE_WRITE_TARGET_ACCOUNT_ID`를 요구하므로 source/target 계좌 2개를 임시 생성했다.
- 1억 read fixture와 충돌하지 않게 `ledger_entry_id_seq`, `transaction_read_model_monthly_id_seq`를 50,000,000 이후로 보정했다.
- gate 실행 실패 후 임시 계좌, ledger, read model, snapshot row는 삭제해 `bank_account=0`, `ledger_entry=0`, `account_id in (1,2)` read model row `0`으로 복구했다.

Command:

```bash
INTERFERENCE_NAME=transaction-100m-postimprove-read-write-vu8w2-20260426 \
INTERFERENCE_WRITE_SOURCE_ACCOUNT_ID=1 \
INTERFERENCE_WRITE_TARGET_ACCOUNT_ID=2 \
K6_HOT_ACCOUNT_ID=910000001 \
K6_HOT_FROM=2026-04-01T00:00:00Z \
K6_HOT_TO=2026-04-30T00:00:00Z \
K6_COLD_ACCOUNT_ID=910000002 \
K6_COLD_FROM=2026-01-01T00:00:00Z \
K6_COLD_TO=2026-01-31T00:00:00Z \
tools/test/run-transaction-100m-read-write-interference-gate.sh
```

Result:

- result: failed before read/write load started
- failure:

```text
Image bitnami/kafka:4.2 Error failed to resolve reference "docker.io/bitnami/kafka:4.2": docker.io/bitnami/kafka:4.2: not found
```

Conclusion:

- mixed read/write 대용량 트래픽 gate의 첫 병목은 application 성능이 아니라 Kafka Docker image reproducibility이다.
- #409의 runner는 main에 있지만 현재 compose image tag 때문에 로컬에서 실제 mixed workload를 시작하지 못한다.

## Bottleneck Order

1. Read-only 1억 row SQL/index path: VU=8/VU=16 overload 기준 p95는 5ms 미만, PostgreSQL memory는 60MiB 미만이어서 현재 병목이 아니다.
2. Strict non-overload runner: adaptive admission + no client backoff 조합에서 429 약 71.8%와 stacktrace flood가 발생한다.
3. Accepted overload path: VU=16에서도 429 약 0.42%로 통과하므로 read-only path의 다음 판정은 local generator가 아닌 remote generator 결과가 필요하다.
4. Mixed read/write path: Kafka image tag `bitnami/kafka:4.2` resolve 실패로 실제 write interference를 시작하지 못했다.
5. Fixture readiness: 1억 read fixture는 read 전용이라 write interference용 계좌 bootstrap fixture가 없다.

## Excluded Completed Work

- #391 `[Perf] transaction 100m capacity gate 결과 리포트 추가`
- #393 `[Perf] transaction 100m workload/profile benchmark 결과 리포트 추가`
- #395 `[Perf] transaction 100m capacity gate hard SLO threshold 추가`
- #397 `[Perf] transaction read adaptive admission control 적용`
- #399 `[Perf] transaction read overload 429 ratio gate 추가`
- #401 `[Perf] transaction read remote k6 load generator 분리`
- #403 `[Perf] transaction 100m cold-start cache-warm SLO gate 추가`
- #405 `[Perf] transaction read compression 운영 기본값 보정`
- #407 `[Perf] transaction read JDBC mapper low-allocation 최적화`
- #409 `[Perf] transaction 100m HTTP read/write interference gate 추가`

## Candidate Issue/PR List

1. `[Build] Kafka loadtest Docker image tag 보정` / template: `task_request.yml` / branch: `build/kafka-loadtest-image-pin`
   - `bitnami/kafka:4.2`가 resolve되지 않아 mixed read/write gate가 시작 전 실패한다. 지원되는 고정 tag 또는 mirror env override로 compose 재현성을 먼저 복구한다.

2. `[Perf] transaction read/write interference account fixture bootstrap 추가` / template: `performance_request.yml` / branch: `perf/transaction-read-write-interference-fixture`
   - 현재 1억 read fixture에는 write용 `bank_account`/snapshot/ledger가 없다. gate가 직접 bounded source/target 계좌를 bootstrap하고 실행 후 cleanup하도록 만들어 수동 SQL 없이 재현되게 한다.

3. `[Perf] transaction read/write interference gate read overload mode 지원` / template: `performance_request.yml` / branch: `perf/transaction-read-write-interference-overload-mode`
   - #409 runner의 read side는 `K6_OVERLOAD_MODE=false`, read 429 threshold `0`으로 고정되어 있다. adaptive admission 운영값에서는 보호 429를 별도 ratio로 판정해야 하므로 read overload/backoff mode를 옵션화한다.

4. `[Perf] transaction 100m adaptive strict runner fail-fast guard 추가` / template: `performance_request.yml` / branch: `perf/transaction-100m-adaptive-strict-runner-guard`
   - adaptive admission이 켜진 상태에서 VU가 초기 limit보다 크고 overload mode가 꺼져 있으면 429 stacktrace flood가 재발한다. runner가 실행 전 조합을 감지해 overload mode 사용 또는 VU 축소를 안내하도록 한다.

5. `[Perf] transaction adaptive admission ramp-up/down 정책 재측정` / template: `performance_request.yml` / branch: `perf/transaction-adaptive-admission-ramp-policy`
   - no-backoff strict traffic에서는 첫 429 이후 min limit으로 떨어져 reject가 지배적이다. `increase-every-successes`, `decrease-on-rejections`, 초기 limit을 overload/backoff traffic 기준으로 재측정한다.

6. `[Perf] transaction 100m remote k6 결과 리포트 추가` / template: `performance_request.yml` / branch: `perf/transaction-100m-remote-k6-results`
   - #401은 remote generator mode를 추가했지만 이번 실행은 local generator였다. Docker context 원격 k6에서 VU=8/VU=16을 재실행해 backend/PostgreSQL CPU와 k6 client CPU를 분리한다.

7. `[Perf] transaction 100m read/write interference 실제 결과 리포트 추가` / template: `performance_request.yml` / branch: `perf/transaction-100m-read-write-interference-results`
   - Kafka image와 account fixture 문제가 해결된 뒤, read VU=8/write VU=2 이상에서 1억 row read p95, read 429, transfer write success/429, outbox/Kafka 지연을 문서화한다.

8. `[Perf] transaction 100m adaptive capacity gate 재실행 리포트 추가` / template: `performance_request.yml` / branch: `perf/transaction-100m-adaptive-capacity-results`
   - #391 capacity 결과는 adaptive admission 적용 전 기준이다. 최신 adaptive admission으로 single-host/cpu-split/long-soak 결과를 다시 고정한다.

9. `[Perf] transaction read p99/max latency SLO gate 추가` / template: `performance_request.yml` / branch: `perf/transaction-read-p99-max-slo-gate`
   - 현재 판정은 p95 중심이다. t3.micro 운영에서는 짧은 CPU stall과 GC가 체감 장애가 될 수 있으므로 p99/max latency와 429 ratio를 함께 gate한다.

10. `[Perf] transaction read page limit sensitivity benchmark 추가` / template: `performance_request.yml` / branch: `perf/transaction-read-page-limit-sensitivity`
    - 현재 k6 limit은 50으로 고정이다. limit 20/50/100/200에서 SQL, JDBC decode, JSON serialization, compression 비용을 비교해 API limit 기본값과 상한을 수치로 확정한다.

## Double Check

- `git log --merges -20`와 `rg`로 #391~#409의 completed work를 확인했다.
- 이번 후보에는 이미 구현된 capacity result, workload result, hard SLO threshold, adaptive admission, overload 429 ratio, remote generator mode, cold-start gate, compression tune, low-allocation mapper, read/write interference runner 자체를 다시 넣지 않았다.
- 후보는 이번 재측정에서 새로 확인된 실행 차단점, strict/adaptive traffic 계약, remote 실행 결과, tail latency/limit 감도처럼 아직 main에 없는 작업만 남겼다.

## Final State

- PostgreSQL remained healthy, OOM false
- temporary write interference account fixture was cleaned up
- no application code, DB migration, or loadtest script changed
