# Transaction 100m capacity gate 20260426 summary

## Scope

- date: 2026-04-26 KST
- issue: #390
- branch: `perf/transaction-100m-capacity-gate-results`
- base main sha: `7070ad9`
- source runner: `tools/test/run-transaction-100m-capacity-gates.sh`
- source summary: `build/reports/k6/transaction-100m-capacity-20260426-actual/capacity-summary.tsv`
- archived summary: `docs/performance-results/transaction-100m-capacity-gate-20260426-capacity-summary.tsv`

## Environment

- dataset: 기존 1억 row transaction read fixture
- hot account: `910000001`, 2026-04 month
- cold account: `910000002`, 2026-01 month
- PostgreSQL budget: `0.60 CPU`, `384m`
- backend profiles: `0.40 CPU/512m`, `0.80 CPU/640m`, `1.00 CPU/640m`
- k6 VU: `8`
- high-traffic admission: `8`
- default guard admission: `3`
- long soak duration: `30m`

## Command

```bash
CAPACITY_NAME=transaction-100m-capacity-20260426-actual \
CAPACITY_LONG_SOAK_DURATION=30m \
tools/test/run-transaction-100m-capacity-gates.sh
```

## Summary

| phase | profile | status | admission | duration | 429 rate | http reqs | worst p95 ms | backend CPU | PostgreSQL CPU |
| --- | --- | ---: | ---: | --- | ---: | ---: | ---: | ---: | ---: |
| single-host | single-host-default | 0 | 3 | 1m | 0.1283 | 2,050 | 294.47 | 43.80% | 13.96% |
| single-host | single-host-high-traffic | 0 | 8 | 1m | 0 | 11,876 | 104.09 | 82.67% | 25.18% |
| cpu-split | cpu-backend040-postgres060 | 99 | 8 | 1m | 0 | 1,880 | 601.85 | 40.25% | 10.30% |
| cpu-split | cpu-backend100-postgres060 | 0 | 8 | 1m | 0 | 38,152 | 82.27 | 106.92% | 51.35% |
| long-soak | long-soak-high-traffic | 0 | 8 | 30m | 0 | 2,870,904 | 44.66 | 85.73% | 56.42% |

`worst p95 ms`는 hot first, hot cursor, cold first, cold cursor 중 가장 큰 p95 값입니다.

## Interpretation

### single-host default

- `admission=3`에서는 p95가 300ms 아래로 유지됐지만 `transaction_429_rate=12.83%`가 발생했다.
- 이 profile은 실패가 아니라 보호 동작 확인이다. default guard는 t3.micro에서 과부하를 fail-fast로 제한하지만 VU=8 처리량은 일부 포기한다.
- accepted p95만 보면 hot/cold 조회 SQL 자체는 기존 SLO 안에 있다.

### single-host high-traffic

- `admission=8`, backend `0.80 CPU`, PostgreSQL `0.60 CPU`에서 1분 profile은 429 없이 통과했다.
- worst p95는 104.09ms로 hot/cold 기준 SLO 안에 있고, backend CPU는 82.67%까지 올라갔다.
- default `max=3`보다 처리량 여유가 있으나 CPU headroom은 크지 않다. adaptive admission 작업은 이 결과를 근거로 bounded 상향만 허용해야 한다.

### CPU split

- backend `0.40 CPU` profile은 k6 threshold status `99`로 실패했고 worst p95가 601.85ms까지 상승했다.
- PostgreSQL CPU는 10.30%로 낮아 DB보다 backend CPU budget이 병목임을 보여준다.
- backend `1.00 CPU` profile은 429 없이 통과했지만 backend CPU가 106.92%, PostgreSQL CPU가 51.35%까지 올라가 단일 로컬 호스트 측정에서는 k6/backend/PostgreSQL CPU 공유 영향을 같이 봐야 한다.

### 30분 long soak

- backend `0.80 CPU`, PostgreSQL `0.60 CPU`, `admission=8`, `duration=30m`에서 2,870,904 requests를 429 없이 처리했다.
- worst p95는 44.66ms로 1분 high-traffic보다 낮고, backend CPU 85.73%, PostgreSQL CPU 56.42% 수준이다.
- long soak 기준으로는 1억 row read path 자체보다 CPU budget과 admission policy가 운영 결정 지점이다.

## Capacity Decision

- 유지: 현재 default `admission=3`은 보수적인 보호 기본값으로 유지할 수 있다.
- 다음 구현 우선순위: hard SLO threshold, adaptive admission, overload 429 ratio gate 순서가 타당하다.
- 분리 필요: 원격 k6 load generator가 없으면 단일 호스트 CPU 공유 때문에 backend/PostgreSQL 병목 판정이 섞인다.
- 보류: DB index/schema 조정은 이번 결과만으로는 근거가 약하다. p95와 PostgreSQL CPU가 SLO 밖 병목으로 보이지 않는다.

## Raw Data

- raw TSV: `docs/performance-results/transaction-100m-capacity-gate-20260426-capacity-summary.tsv`
- 원본 artifact는 ignored 경로인 `build/reports/k6/**` 아래에만 둔다.

## Verification

- `test -f docs/performance-results/transaction-100m-capacity-gate-20260426-capacity-summary.tsv`
- `test -f docs/performance-results/transaction-100m-capacity-gate-20260426-summary.md`
- `git diff --check`
