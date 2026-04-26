# Transaction 100m local t3.micro two-phase loadtest bottleneck

## Scope

- date: 2026-04-26 KST
- issue: #364
- branch: `perf/transaction-100m-two-phase-loadtest-report`
- base main sha: `242ce2c`
- goal: 1억 row 거래 조회와 대용량 HTTP 부하를 로컬 Docker t3.micro 예산에서 분리 측정

## Environment

- compose: `compose.yml`, `compose.t3micro.yml`, `compose.loadtest.yml`
- PostgreSQL: 18.3
- backend: Spring Boot app, Java 21, loadtest profile container
- observability: k6, Prometheus remote write, Grafana, postgres-exporter
- dataset: hot `transaction_read_model` 50,000,000 rows, archive `transaction_read_model_archive` 약 50,000,028 rows
- hot account: `910000001`, window `2026-04-01T00:00:00Z..2026-04-30T00:00:00Z`
- cold account: `910000002`, window `2026-01-01T00:00:00Z..2026-01-31T00:00:00Z`

## Phase 1 Fixture Generation

Command:

```bash
tools/test/prepare-transaction-read-model-100m-fixture.sh
```

Result:

- fresh PostgreSQL volume에서 1억 row fixture 생성 완료
- fixture budget: PostgreSQL `2GiB`, `2 CPU`, observability off
- output estimate: hot 50,000,000, archive 50,000,028
- peak observed sample: PostgreSQL CPU 약 90%, memory 약 256MiB/2GiB, Block I/O write 약 172GB
- disk free after fixture sample: 약 141GiB

Conclusion:

- 1억 row 생성은 t3.micro 384MiB PostgreSQL에서 직접 수행할 작업이 아니다.
- pre-indexed insert 전략은 2GiB fixture budget에서는 성공했다.
- fixture 생성 병목은 memory보다 CPU + WAL/disk write이다.

## Phase 2 Read Loadtest

Before read test:

- 같은 Docker volume 유지
- PostgreSQL을 t3.micro budget으로 recreate: memory `384MiB`, CPU `0.6`, OOM false, healthy
- backend loadtest budget: memory `1GiB`, CPU `2`
- default transaction read admission: `OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=3`

### Run A: default guard, VU=8

Command:

```bash
K6_REPORT_NAME=transaction-100m-two-phase-t3micro-20260426 \
K6_VUS=8 K6_DURATION=1m \
tools/test/run-k6-transaction-100m-loadtest.sh --no-up
```

Summary:

- report: `docs/performance-results/transaction-100m-two-phase-t3micro-20260426-summary.md`
- result: failed threshold
- `http_req_failed`: `0.7257660989557434`
- `checks`: `0.43042945383813314`
- p95: hot first 2.16ms, hot cursor 2.79ms, cold first 3.03ms, cold cursor 3.09ms

Conclusion:

- 8 VU에서 첫 병목은 DB query plan이 아니라 backend admission guard이다.
- p95가 낮은데 실패율만 높은 형태라, 대부분 요청은 과부하 보호 정책으로 429 처리됐다.

### Run B: default guard, VU=3

Command:

```bash
K6_REPORT_NAME=transaction-100m-two-phase-t3micro-vu3-20260426 \
K6_VUS=3 K6_DURATION=1m \
tools/test/run-k6-transaction-100m-loadtest.sh --no-up
```

Summary:

- report: `docs/performance-results/transaction-100m-two-phase-t3micro-vu3-20260426-summary.md`
- result: passed
- complete iterations: 47,751/min
- `http_req_failed`: `0`
- `checks`: `1`
- p95: hot first 1.05ms, hot cursor 1.04ms, cold first 1.02ms, cold cursor 1.08ms
- observed sample: backend CPU 약 98%, PostgreSQL CPU 약 61%, PostgreSQL memory 약 62MiB/384MiB

Conclusion:

- default guard 안쪽에서는 1억 row 조회가 index 경로로 안정적으로 수행된다.
- PostgreSQL memory는 여유가 있고, 다음 한계는 backend CPU와 PostgreSQL CPU 사용률이다.

### Run C: attempted admission8 through wrapper

Command:

```bash
K6_REPORT_NAME=transaction-100m-two-phase-t3micro-vu8-admission8-20260426 \
K6_VUS=8 K6_DURATION=1m \
tools/test/run-k6-transaction-100m-loadtest.sh --no-up
```

Summary:

- report: `docs/performance-results/transaction-100m-two-phase-t3micro-vu8-admission8-20260426-summary.md`
- result: invalid for capacity decision
- reason: `docker compose run` dependency handling recreated backend with default `OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=3`
- observed backend env after run: `OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=3`

Conclusion:

- 현재 k6 wrapper는 dependency recreate가 섞이면 admission setting 검증 없이 결과를 만들 수 있다.
- report name의 `admission8`만 믿으면 안 되고, preflight에서 backend env를 검증해야 한다.

### Run D: admission8, no-deps direct k6, VU=8

Setup:

```bash
OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=8 \
docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml \
  --profile loadtest up -d --force-recreate aquila-bank-backend
```

Command:

```bash
docker compose -f compose.yml -f compose.t3micro.yml -f compose.loadtest.yml \
  --profile loadtest run --rm --no-deps \
  -e K6_REPORT_NAME=transaction-100m-two-phase-t3micro-vu8-admission8-nodeps-20260426 \
  -e K6_HOT_ACCOUNT_ID=910000001 \
  -e K6_HOT_FROM=2026-04-01T00:00:00Z \
  -e K6_HOT_TO=2026-04-30T00:00:00Z \
  -e K6_COLD_ACCOUNT_ID=910000002 \
  -e K6_COLD_FROM=2026-01-01T00:00:00Z \
  -e K6_COLD_TO=2026-01-31T00:00:00Z \
  -e K6_VUS=8 \
  -e K6_DURATION=1m \
  -e K6_LIMIT=50 \
  k6-transaction-read-100m
```

Summary:

- report: `docs/performance-results/transaction-100m-two-phase-t3micro-vu8-admission8-nodeps-20260426-summary.md`
- result: passed
- complete iterations: 31,701/min
- `http_req_failed`: `0`
- `checks`: `1`
- p95: hot first 5.09ms, hot cursor 5.27ms, cold first 5.05ms, cold cursor 5.39ms
- observed sample: backend CPU 약 115%, PostgreSQL CPU 약 61%, PostgreSQL memory 약 56MiB/384MiB

Conclusion:

- guard를 8로 올려도 1억 row 조회 p95는 SLO보다 충분히 낮다.
- 다만 VU=8 throughput은 VU=3 대비 선형 증가하지 않았다.
- next bottleneck은 query memory가 아니라 backend CPU, JDBC/request 처리 비용, PostgreSQL CPU budget 조합이다.

## Additional Harness Finding

`tools/test/run-transaction-read-model-100m-k6-local.sh --k6-only`는 같은 DB에서 수동 schema query가 `t`로 즉시 응답했는데도 `transaction_read_model schema was not created in time`으로 실패했다.

Implication:

- 1억 row 조회 자체와 별개로 loadtest wrapper 신뢰성 문제가 있다.
- 다음 PR에서 k6-only preflight를 `docker compose exec` 결과 원문 로깅, backend env 검증, `--no-deps` 실행 옵션으로 보강해야 한다.

## Bottleneck Order

1. Fixture generation: 1억 row 생성은 t3.micro DB budget에서 수행하지 말고 별도 fixture budget 또는 외부 dump/restore로 분리한다.
2. Default production-like t3.micro read traffic: `OPS_API_ADMISSION_CONTROL_TRANSACTION_READ_MAX=3`이 첫 병목이자 보호 장치이다.
3. Guard-internal read path: VU=3은 실패율 0, p95 약 1ms로 정상이다.
4. Raised guard read path: VU=8은 실패율 0, p95 약 5ms로 정상이나 throughput이 선형 확장되지 않는다.
5. Next optimization target: backend CPU/request overhead, DB CPU allocation, Hikari pool/admission limit의 조합을 튜닝한다.

## Follow-up Work

- k6 wrapper에 `--no-deps` 경로와 backend env preflight 추가
- admission limit과 Hikari pool size를 같은 실험 축으로 둔 matrix loadtest 추가
- backend/DB CPU saturation 기준 Prometheus alert와 Grafana panel 추가
- fixture 생성은 dump/restore 또는 prebuilt volume workflow로 분리
- 1억 row 조회 SLO는 default guard 기준과 raised guard 기준을 별도 문서화
