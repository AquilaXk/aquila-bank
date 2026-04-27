# transaction-100m-latest-main-gate-retest-vu8-overload-20260427-summary

## Archive Metadata

- archivedAt: 2026-04-27T01:25:16Z
- sourceMarkdown: build/reports/k6/transaction-100m-latest-main-gate-retest-vu8-overload-20260427-summary.md
- sourceJson: build/reports/k6/transaction-100m-latest-main-gate-retest-vu8-overload-20260427-summary.json

# k6 Transaction 100m Load Test

## Environment

- baseUrl: http://aquila-bank-backend:8080
- vus: 8
- duration: 30s
- limit: 50
- observability mode: summary-only
- overload mode: true
- max retry-after sleep seconds: 1
- hot account id: 910000001
- cold account id: 910000002
- hot p95 threshold ms: 350
- cold p95 threshold ms: 750
- hot p99 threshold ms: 750
- cold p99 threshold ms: 1500
- hot max threshold ms: 3000
- cold max threshold ms: 5000
- http failed rate threshold: disabled in overload mode
- overload 429 rate threshold: 0.8

## Results

- http_req_failed rate: 0
- checks rate: 1
- transaction 429 rate: 0
- hot first p95 ms: 6.254201749999998
- hot first p99 ms: n/a
- hot first max ms: 144.715583
- hot cursor p95 ms: 6.095252699999995
- hot cursor p99 ms: n/a
- hot cursor max ms: 145.436458
- cold first p95 ms: 5.911756899999998
- cold first p99 ms: n/a
- cold first max ms: 144.029417
- cold cursor p95 ms: 5.995316699999978
- cold cursor p99 ms: n/a
- cold cursor max ms: 145.91425

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- overload mode에서는 admission guard 429를 rejected sample로 집계합니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- observability mode가 `summary-only`이면 Prometheus remote write 없이 summary 파일만 남깁니다.
