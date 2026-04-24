# transaction-100m-required-index-smoke-summary

## Archive Metadata

- archivedAt: 2026-04-24T17:33:43Z
- sourceMarkdown: build/reports/k6/transaction-100m-required-index-smoke-summary.md
- sourceJson: build/reports/k6/transaction-100m-required-index-smoke-summary.json

## Smoke Scope

- seed total rows: 1,000
- hot read model rows: 500
- archive read model rows: 500
- seed index strategy: `required`
- 실제 1억 row 결과가 아니라 t3.micro 측정 경로 복구용 small smoke 결과입니다.

# k6 Transaction 100m Load Test

## Environment

- baseUrl: http://aquila-bank-backend:8080
- vus: 1
- duration: 5s
- limit: 50
- hot account id: 910000001
- cold account id: 910000002
- hot p95 threshold ms: 350
- cold p95 threshold ms: 750
- http failed rate threshold: 0.01

## Results

- http_req_failed rate: 0
- checks rate: 1
- hot first p95 ms: 0.7912714999999999
- hot cursor p95 ms: 0.7842087
- cold first p95 ms: 0.8102628
- cold cursor p95 ms: 0.8632499999999991

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- Prometheus remote write 대상은 `K6_PROMETHEUS_RW_SERVER_URL`입니다.
