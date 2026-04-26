# Transaction 100m adaptive/remote/interference results 20260427

## Scope

- date: 2026-04-27 KST
- issue: #419
- branch: `perf/transaction-100m-results-report`
- base main sha: `368d8b51f096890d7394084f47371a7fb1562e90`
- source runners:
  - `tools/test/run-transaction-100m-read-write-interference-gate.sh`
  - `tools/test/run-transaction-100m-capacity-gates.sh`
  - `tools/test/run-k6-transaction-100m-loadtest.sh`
- raw artifacts: `build/reports/k6/**`

## Environment

- dataset: restored 100m transaction read fixture
- hot account: `910000001`, `2026-04-01T00:00:00Z` to `2026-04-30T00:00:00Z`
- cold account: `910000002`, `2026-01-01T00:00:00Z` to `2026-01-31T00:00:00Z`
- estimated rows after restore: hot `50,000,000`, archive `50,000,028`
- Docker contexts: `default`, `desktop-linux`
- remote k6 context/env: not configured

## Remote k6

Remote k6 was not rerun because the host has no remote Docker context and no remote runner env.

```bash
docker context ls
printenv | rg '^(K6_DOCKER_CONTEXT|K6_REMOTE_BASE_URL|K6_REMOTE_PROMETHEUS_RW_SERVER_URL|K6_REMOTE_WORKDIR)='
K6_GENERATOR_MODE=docker-context tools/test/run-k6-transaction-100m-loadtest.sh --print-plan
```

Observed:

- `docker context ls`: only `default` and `desktop-linux`
- remote env query: no values
- runner fail-fast: `K6_DOCKER_CONTEXT is required`

Decision:

- VU=8/VU=16 remote k6 numbers are not recorded in this report.
- backend/PostgreSQL CPU and k6 client CPU separation still needs a real remote Docker context plus reachable backend and Prometheus remote-write URLs.

## Read/write interference

### Default Kafka budget failure

Command:

```bash
K6_HOT_ACCOUNT_ID=910000001 \
K6_HOT_FROM=2026-04-01T00:00:00Z \
K6_HOT_TO=2026-04-30T00:00:00Z \
K6_COLD_ACCOUNT_ID=910000002 \
K6_COLD_FROM=2026-01-01T00:00:00Z \
K6_COLD_TO=2026-01-31T00:00:00Z \
INTERFERENCE_WRITE_SOURCE_ACCOUNT_ID=920000001 \
INTERFERENCE_WRITE_TARGET_ACCOUNT_ID=920000002 \
INTERFERENCE_NAME=transaction-100m-read-write-interference-20260427-actual \
INTERFERENCE_DURATION=5m \
INTERFERENCE_READ_VUS=8 \
INTERFERENCE_WRITE_VUS=2 \
INTERFERENCE_READ_OVERLOAD_MODE=true \
INTERFERENCE_READ_429_RATE_THRESHOLD=0.20 \
tools/test/run-transaction-100m-read-write-interference-gate.sh
```

Result:

- status: failed before k6
- failure: `backend readiness timeout: http://localhost:8080/actuator/health`
- Kafka default budget: `448m`, heap `-Xms128m -Xmx256m`
- Kafka symptom: repeated restarts and healthcheck timeout
- confirmed Kafka OOM when heap was lowered to `-Xmx128m`: `java.lang.OutOfMemoryError: Java heap space` in `LogCleaner`

Interpretation:

- `bitnamilegacy/kafka:4.0.0-debian-12-r10` resolves, so the image tag issue is fixed.
- Kafka 4.0 does not fit this interference profile under the current `448m` local loadtest memory budget.

### Kafka 768MiB rerun

Command:

```bash
T3MICRO_KAFKA_MEMORY=768m \
T3MICRO_KAFKA_MEMORY_SWAP=768m \
T3MICRO_KAFKA_HEAP_OPTS='-Xms128m -Xmx256m' \
K6_HOT_ACCOUNT_ID=910000001 \
K6_HOT_FROM=2026-04-01T00:00:00Z \
K6_HOT_TO=2026-04-30T00:00:00Z \
K6_COLD_ACCOUNT_ID=910000002 \
K6_COLD_FROM=2026-01-01T00:00:00Z \
K6_COLD_TO=2026-01-31T00:00:00Z \
INTERFERENCE_WRITE_SOURCE_ACCOUNT_ID=920000001 \
INTERFERENCE_WRITE_TARGET_ACCOUNT_ID=920000002 \
INTERFERENCE_NAME=transaction-100m-read-write-interference-20260427-kafka768 \
INTERFERENCE_DURATION=5m \
INTERFERENCE_READ_VUS=8 \
INTERFERENCE_WRITE_VUS=2 \
INTERFERENCE_READ_OVERLOAD_MODE=true \
INTERFERENCE_READ_429_RATE_THRESHOLD=0.20 \
INTERFERENCE_READINESS_TIMEOUT_SECONDS=360 \
tools/test/run-transaction-100m-read-write-interference-gate.sh
```

Artifacts:

- summary TSV: `build/reports/k6/transaction-100m-read-write-interference-20260427-kafka768/read-write-interference-summary.tsv`
- read JSON: `build/reports/k6/transaction-100m-read-write-interference-20260427-kafka768-read-summary.json`
- write JSON: `build/reports/k6/transaction-100m-read-write-interference-20260427-kafka768-write-summary.json`
- read log: `build/reports/k6/transaction-100m-read-write-interference-20260427-kafka768/transaction-100m-read-write-interference-20260427-kafka768-read.log`
- write log: `build/reports/k6/transaction-100m-read-write-interference-20260427-kafka768/transaction-100m-read-write-interference-20260427-kafka768-write.log`

Result:

| signal | value |
| --- | ---: |
| runner status | 0 |
| read status | 0 |
| write status | 0 |
| read http reqs | 567,963 |
| read 429 rate | 0.000519 |
| read hot first p95 | 4.66ms |
| read hot cursor p95 | 4.73ms |
| read cold first p95 | 4.69ms |
| read cold cursor p95 | 4.84ms |
| write http reqs | 8,457 |
| write 429 rate | 0.042568 |
| write success rate | 0.957432 |
| write duration p95 | 71.41ms |
| outbox dispatch lag observed after run | 294s max-over-time, 344s current before manual cleanup |
| current outbox failed count after run | 0 |
| max notification consumer lag over run window | 5 |
| notification DLQ count | 0 |

Interpretation:

- With Kafka memory raised to `768m`, the mixed read/write gate passed.
- Read latency stayed far below the p95 thresholds while protected read 429 remained below the `0.20` overload threshold.
- Transfer write stayed inside the `429 < 0.05` and `success >= 0.95` thresholds.
- Outbox dispatch lag reached 294s even with failed count 0. This should be treated as a separate async backlog signal, not a read path SQL regression.
- Runner cleanup did not remove fixture rows after success. Follow-up issue: #420.

Manual cleanup after measuring:

- deleted notification inbox rows: `10,592`
- deleted outbox rows: `8,097`
- deleted command idempotency rows: `8,097`
- deleted transaction read model rows: `16,196`
- deleted ledger rows: `16,196`
- deleted fixture bank accounts: `2`
- verified remaining fixture accounts/ledger/read model/outbox rows: `0`

## Adaptive capacity

Command:

```bash
K6_HOT_ACCOUNT_ID=910000001 \
K6_HOT_FROM=2026-04-01T00:00:00Z \
K6_HOT_TO=2026-04-30T00:00:00Z \
K6_COLD_ACCOUNT_ID=910000002 \
K6_COLD_FROM=2026-01-01T00:00:00Z \
K6_COLD_TO=2026-01-31T00:00:00Z \
CAPACITY_NAME=transaction-100m-adaptive-capacity-20260427-actual \
CAPACITY_READINESS_TIMEOUT_SECONDS=300 \
tools/test/run-transaction-100m-capacity-gates.sh
```

Artifacts:

- summary TSV: `build/reports/k6/transaction-100m-adaptive-capacity-20260427-actual/capacity-summary.tsv`
- JSON pattern: `build/reports/k6/transaction-100m-adaptive-capacity-20260427-actual-*-summary.json`
- logs: `build/reports/k6/transaction-100m-adaptive-capacity-20260427-actual/*.log`

Summary:

| phase | profile | status | admission | overload | duration | 429 rate | http reqs | worst p95 | worst max | backend CPU | PostgreSQL CPU |
| --- | --- | ---: | ---: | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| single-host | single-host-default | 99 | 3 | true | 1m | 0.159621 | 1,585 | 494.80ms | 1,825.67ms | 44.09% | 10.64% |
| single-host | single-host-high-traffic | 99 | 8 | false | 1m | 0 | 9,428 | 184.98ms | 4,507.24ms | 97.64% | 60.34% |
| cpu-split | cpu-backend040-postgres060 | 99 | 8 | false | 1m | 0 | 1,372 | 801.24ms | 2,529.09ms | 42.45% | 6.38% |
| cpu-split | cpu-backend100-postgres060 | 0 | 8 | false | 1m | 0 | 43,916 | 78.88ms | 595.73ms | 101.66% | 59.67% |
| long-soak | long-soak-high-traffic | 0 | 8 | false | 30m | 0 | 2,805,203 | 52.03ms | 848.82ms | 85.24% | 61.69% |

`worst p95` is the maximum p95 across hot first, hot cursor, cold first, and cold cursor. `worst max` is recorded when it explained k6 status `99`.

Interpretation:

- Overall runner status was 1 because three profiles crossed hard thresholds.
- `single-host-default` protected the system with 15.96% 429 but also crossed hot p95 thresholds. This is not a good operating profile for strict user-visible traffic at VU=8.
- `single-host-high-traffic` had good p95 and no 429, but k6 failed because hot cursor max reached 4,507ms, above the 3,000ms max threshold.
- `cpu-backend040-postgres060` confirmed backend CPU budget is the bottleneck. PostgreSQL CPU stayed low while p95 exceeded SLO.
- `cpu-backend100-postgres060` passed and shows that more backend CPU removes the 0.40 CPU bottleneck.
- `long-soak-high-traffic` passed for 30m with `admission=8`, `VU=8`, no 429, worst p95 52.03ms, and max below hard thresholds.

## Adaptive policy decision

- Keep the fail-fast guard that blocks adaptive strict runs where `VU > initial admission` and overload mode is off.
- For VU=8 strict traffic, `admission=8` is the only measured stable setting in this run.
- `admission=3` should remain a conservative overload/protection default, not a strict SLO profile for VU=8.
- The run does not justify changing `increase-every-successes` or `decrease-on-rejections` yet. The next policy measurement should use real overload/backoff traffic and record admission limit time series, not only k6 end summaries.

## Follow-ups

- #420: interference fixture cleanup must be fixed before relying on repeated mixed workload runs.
- Remote k6 rerun still needs an actual remote Docker context and remote URLs.
- Kafka loadtest memory budget needs a separate decision: either keep Kafka out of strict t3.micro CPU/memory interpretation or raise the local loadtest Kafka memory budget for reproducible interference runs.

## Verification

- `tools/test/run-transaction-100m-fixture-restore.sh`
- `tools/test/run-transaction-100m-read-write-interference-gate.sh`
- `tools/test/run-transaction-100m-capacity-gates.sh`
- `docker context ls`
- `K6_GENERATOR_MODE=docker-context tools/test/run-k6-transaction-100m-loadtest.sh --print-plan`
- `git diff --check`
