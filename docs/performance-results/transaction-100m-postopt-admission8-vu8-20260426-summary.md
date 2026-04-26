# transaction-100m-postopt-admission8-vu8-20260426-summary

## Archive Metadata

- archivedAt: 2026-04-26T09:14:28Z
- sourceMarkdown: build/reports/k6/transaction-100m-postopt-admission8-vu8-20260426-summary.md
- sourceJson: build/reports/k6/transaction-100m-postopt-admission8-vu8-20260426-summary.json

# k6 Transaction 100m Load Test

## Environment

- baseUrl: http://aquila-bank-backend:8080
- vus: 8
- duration: 1m
- limit: 50
- hot account id: 910000001
- cold account id: 910000002
- hot p95 threshold ms: 350
- cold p95 threshold ms: 750
- http failed rate threshold: 0.01

## Results

- http_req_failed rate: 0
- checks rate: 1
- hot first p95 ms: 6.666007699999997
- hot cursor p95 ms: 6.364991999999958
- cold first p95 ms: 6.61883269999998
- cold cursor p95 ms: 6.464070299999989

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- Prometheus remote write 대상은 `K6_PROMETHEUS_RW_SERVER_URL`입니다.
