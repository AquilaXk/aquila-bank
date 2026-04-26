# transaction-100m-latest-main-vu16-overload-20260427-summary

## Archive Metadata

- archivedAt: 2026-04-26T17:15:14Z
- sourceMarkdown: build/reports/k6/transaction-100m-latest-main-vu16-overload-20260427-summary.md
- sourceJson: build/reports/k6/transaction-100m-latest-main-vu16-overload-20260427-summary.json

# k6 Transaction 100m Load Test

## Environment

- baseUrl: http://aquila-bank-backend:8080
- vus: 16
- duration: 1m
- limit: 50
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
- overload 429 rate threshold: 0.2

## Results

- http_req_failed rate: 0.005232686850526288
- checks rate: 1
- transaction 429 rate: 0.005232686850526288
- hot first p95 ms: 1.4898171499999981
- hot first p99 ms: n/a
- hot first max ms: 227.846209
- hot cursor p95 ms: 1.382417
- hot cursor p99 ms: n/a
- hot cursor max ms: 195.10275
- cold first p95 ms: 1.3300747999999984
- cold first p99 ms: n/a
- cold first max ms: 84.641042
- cold cursor p95 ms: 1.3527874999999996
- cold cursor p99 ms: n/a
- cold cursor max ms: 86.858125

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- overload mode에서는 admission guard 429를 rejected sample로 집계합니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- Prometheus remote write 대상은 `K6_PROMETHEUS_RW_SERVER_URL`입니다.
