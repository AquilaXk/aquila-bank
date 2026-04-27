# transaction-100m-latest-main-gate-retest-vu3-20260427-summary

## Archive Metadata

- archivedAt: 2026-04-27T01:24:24Z
- sourceMarkdown: build/reports/k6/transaction-100m-latest-main-gate-retest-vu3-20260427-summary.md
- sourceJson: build/reports/k6/transaction-100m-latest-main-gate-retest-vu3-20260427-summary.json

# k6 Transaction 100m Load Test

## Environment

- baseUrl: http://aquila-bank-backend:8080
- vus: 3
- duration: 30s
- limit: 50
- observability mode: summary-only
- overload mode: false
- max retry-after sleep seconds: 1
- hot account id: 910000001
- cold account id: 910000002
- hot p95 threshold ms: 350
- cold p95 threshold ms: 750
- hot p99 threshold ms: 750
- cold p99 threshold ms: 1500
- hot max threshold ms: 3000
- cold max threshold ms: 5000
- http failed rate threshold: 0.01
- overload 429 rate threshold: disabled outside overload mode

## Results

- http_req_failed rate: 0
- checks rate: 1
- transaction 429 rate: 0
- hot first p95 ms: 2.544709
- hot first p99 ms: n/a
- hot first max ms: 174.123958
- hot cursor p95 ms: 2.455333
- hot cursor p99 ms: n/a
- hot cursor max ms: 85.31975
- cold first p95 ms: 2.5395
- cold first p99 ms: n/a
- cold first max ms: 71.356
- cold cursor p95 ms: 2.532458
- cold cursor p99 ms: n/a
- cold cursor max ms: 65.385167

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- overload mode에서는 admission guard 429를 rejected sample로 집계합니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- observability mode가 `summary-only`이면 Prometheus remote write 없이 summary 파일만 남깁니다.
