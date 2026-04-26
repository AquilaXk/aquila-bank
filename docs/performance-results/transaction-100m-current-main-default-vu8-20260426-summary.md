# transaction-100m-current-main-default-vu8-20260426-summary

## Archive Metadata

- archivedAt: 2026-04-26T12:47:29Z
- sourceMarkdown: build/reports/k6/transaction-100m-current-main-default-vu8-20260426-summary.md
- sourceJson: build/reports/k6/transaction-100m-current-main-default-vu8-20260426-summary.json

# k6 Transaction 100m Load Test

## Environment

- baseUrl: http://aquila-bank-backend:8080
- vus: 8
- duration: 1m
- limit: 50
- overload mode: false
- max retry-after sleep seconds: 1
- hot account id: 910000001
- cold account id: 910000002
- hot p95 threshold ms: 350
- cold p95 threshold ms: 750
- http failed rate threshold: 0.01

## Results

- http_req_failed rate: 0.7235015341616794
- checks rate: 0.43321394147815845
- transaction 429 rate: 0.7235015341616794
- hot first p95 ms: 2.119295599999996
- hot cursor p95 ms: 2.83337085
- cold first p95 ms: 3.0514836999999964
- cold cursor p95 ms: 3.0467811499999984

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- overload mode에서는 admission guard 429를 rejected sample로 집계합니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- Prometheus remote write 대상은 `K6_PROMETHEUS_RW_SERVER_URL`입니다.
