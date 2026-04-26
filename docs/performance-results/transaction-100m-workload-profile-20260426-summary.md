# Transaction 100m workload/profile benchmark 20260426 summary

## Scope

- date: 2026-04-26 KST
- issue: #392
- branch: `perf/transaction-100m-workload-profile-results`
- base main sha: `1bd2e18`
- source runners:
  - `tools/test/run-k6-transaction-100m-weighted-loadtest.sh`
  - `tools/test/run-transaction-read-compression-benchmark.sh`
  - `tools/test/run-transaction-read-jdbc-mapper-allocation.sh`
- archived TSV: `docs/performance-results/transaction-100m-workload-profile-20260426-compression-summary.tsv`

## Environment

- dataset: 기존 1억 row transaction read fixture
- baseUrl: `http://aquila-bank-backend:8080`
- hot account: `910000001`
- cold account: `910000002`
- k6 VU: `8`
- page limit: `50`
- weighted duration: `1m`
- compression profile duration: `30s`
- JDBC mapper JFR duration: `45s`

## Weighted Workload

Command:

```bash
K6_REPORT_NAME=transaction-100m-weighted-20260426-actual \
K6_VUS=8 \
K6_DURATION=1m \
tools/test/run-k6-transaction-100m-weighted-loadtest.sh --no-up --no-deps
```

Weights:

- hot first: `45`
- hot cursor: `25`
- cold first: `15`
- cold cursor: `10`
- detail: `0`

Result:

| metric | value |
| --- | ---: |
| http requests | 97,060 |
| request rate | 1,616.78/s |
| http failed rate | 0 |
| weighted 429 rate | 0 |
| hot first p95 | 34.87ms |
| hot cursor p95 | 6.43ms |
| cold first p95 | 28.15ms |
| cold cursor p95 | 21.73ms |
| detail p95 | n/a |

Interpretation:

- weighted workload는 429 없이 통과했고 모든 p95가 기존 hot/cold SLO 안에 있다.
- hot first와 cold first가 cursor보다 비싸지만 35ms 아래라 SQL/index 병목 증거는 약하다.
- detail weight가 `0`이라 detail endpoint 병목 판단은 이 실행에서 제외한다.

## Compression Benchmark

Command:

```bash
COMPRESSION_NAME=transaction-compression-20260426-actual \
tools/test/run-transaction-read-compression-benchmark.sh
```

Profile results:

| profile | enabled | min response size | http reqs | req/s | 429 rate | worst p95 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| off | false | 0 | 50,636 | 1,687.38/s | 0 | 21.63ms |
| on-2kb | true | 2,048 | 53,656 | 1,788.03/s | 0 | 10.48ms |
| on-8kb | true | 8,192 | 51,044 | 1,700.72/s | 0 | 17.82ms |

Interpretation:

- 세 profile 모두 30초 smoke에서 429 없이 SLO 안에 있다.
- 이 실행만 보면 `on-2kb`가 p95와 req/s 모두 가장 좋지만, 30초 로컬 smoke라 CPU 비용 결론을 확정하기에는 부족하다.
- 운영 기본값 보정은 별도 PR에서 backend CPU sample과 response byte size를 함께 문서화한 뒤 결정한다.

## JDBC Mapper Allocation

Command:

```bash
PROFILE_NAME=transaction-jdbc-mapper-20260426-actual \
tools/test/run-transaction-read-jdbc-mapper-allocation.sh
```

Artifacts:

- summary: `build/reports/profiling/transaction-jdbc-mapper-20260426-actual/transaction-jdbc-mapper-20260426-actual-summary.md`
- allocation by class: `build/reports/profiling/transaction-jdbc-mapper-20260426-actual/transaction-jdbc-mapper-20260426-actual-jfr-allocation-by-class.txt`
- allocation by site: `build/reports/profiling/transaction-jdbc-mapper-20260426-actual/transaction-jdbc-mapper-20260426-actual-jfr-allocation-by-site.txt`
- k6 summary: `build/reports/k6/transaction-jdbc-mapper-20260426-actual-k6-summary.md`

Result:

| signal | value |
| --- | ---: |
| k6 status | 0 |
| http failed rate | 0 |
| transaction 429 rate | 0 |
| hot first p95 | 12.58ms |
| hot cursor p95 | 9.17ms |
| cold first p95 | 10.04ms |
| cold cursor p95 | 10.31ms |
| top allocation class | `byte[]` 28.97% |
| domain row object allocation | `TransactionSummary` 1.35% |
| response item allocation | `TransactionItemResponse` 1.26% |

Observed hot spots:

- allocation by site: `org.postgresql.core.PGStream.receiveTupleV3()` 8.46%
- allocation by site: `java.util.Arrays.copyOfRangeByte(byte[], int, int)` 5.42%
- allocation by site: `org.postgresql.core.Encoding.decode(byte[], int, int)` 3.04%
- allocation by site: `TransactionItemResponse.from(TransactionSummary)` 1.26%
- execution samples: `HashMap.getNode`, Jackson property serialization, PostgreSQL tuple read, timestamp parsing이 상위권이다.
- `CPUTimeSample` event는 현재 JFR summary에서 제공되지 않아 CPU-time hot method 파일은 분석 근거로 쓰지 않는다.

Interpretation:

- row mapper 자체의 `TransactionSummary` allocation은 1.35%로 작고, response DTO allocation도 1.26% 수준이다.
- 가장 큰 allocation은 PostgreSQL driver tuple/byte decode와 JSON serialization 주변이다.
- low-allocation mapper 전환은 바로 진행하지 않는다. enum/time conversion이나 row object 생성이 지배적 hot path라는 증거가 부족하다.

## Priority Decision

1. 먼저 진행: capacity hard SLO threshold와 overload 429 ratio gate. 실제 capacity/workload 결과가 SLO 안에 있으므로 runner가 회귀를 즉시 실패시켜야 한다.
2. 조건부 진행: compression 운영 기본값 보정. `on-2kb` 결과가 좋아 보이지만 30초 smoke라 CPU sample을 더 붙여 결정한다.
3. 보류: JDBC mapper low-allocation 최적화. 현재 JFR에서는 mapper object보다 driver byte decode와 serialization 비중이 크다.
4. 별도 필요: remote k6 generator. local single-host 결과는 k6 client CPU 공유 영향을 분리하지 못한다.

## Raw Data

- compression raw TSV: `docs/performance-results/transaction-100m-workload-profile-20260426-compression-summary.tsv`
- 원본 JFR와 JSON artifact는 ignored 경로인 `build/reports/**` 아래에만 둔다.

## Verification

- `test -f docs/performance-results/transaction-100m-workload-profile-20260426-summary.md`
- `test -f docs/performance-results/transaction-100m-workload-profile-20260426-compression-summary.tsv`
- `git diff --cached --check`
