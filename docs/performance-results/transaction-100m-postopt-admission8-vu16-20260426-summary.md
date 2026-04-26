# transaction-100m-postopt-admission8-vu16-20260426-summary

## Archive Metadata

- archivedAt: 2026-04-26T09:25:22Z
- sourceMarkdown: build/reports/k6/transaction-100m-postopt-admission8-vu16-20260426-summary.md
- sourceJson: build/reports/k6/transaction-100m-postopt-admission8-vu16-20260426-summary.json

# k6 Transaction 100m Load Test

## Environment

- baseUrl: http://aquila-bank-backend:8080
- vus: 16
- duration: 1m
- limit: 50
- hot account id: 910000001
- cold account id: 910000002
- hot p95 threshold ms: 350
- cold p95 threshold ms: 750
- http failed rate threshold: 0.01

## Results

- http_req_failed rate: 0.7494471433676665
- checks rate: 0.40070734364168803
- hot first p95 ms: 3.2143376499999983
- hot cursor p95 ms: 5.725133399999997
- cold first p95 ms: 5.964445899999993
- cold cursor p95 ms: 5.979516599999991

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- Prometheus remote write 대상은 `K6_PROMETHEUS_RW_SERVER_URL`입니다.
