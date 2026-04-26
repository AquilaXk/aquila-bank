# transaction-100m-current-main-admission8-vu16-overload-20260426-summary

## Archive Metadata

- archivedAt: 2026-04-26T12:53:43Z
- sourceMarkdown: build/reports/k6/transaction-100m-current-main-admission8-vu16-overload-20260426-summary.md
- sourceJson: build/reports/k6/transaction-100m-current-main-admission8-vu16-overload-20260426-summary.json

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
- http failed rate threshold: disabled in overload mode

## Results

- http_req_failed rate: 0.003796236980093482
- checks rate: 1
- transaction 429 rate: 0.003796236980093482
- hot first p95 ms: 4.747908199999968
- hot cursor p95 ms: 4.8301501999999985
- cold first p95 ms: 4.711024999999998
- cold cursor p95 ms: 4.979968749999989

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- overload mode에서는 admission guard 429를 rejected sample로 집계합니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- Prometheus remote write 대상은 `K6_PROMETHEUS_RW_SERVER_URL`입니다.
