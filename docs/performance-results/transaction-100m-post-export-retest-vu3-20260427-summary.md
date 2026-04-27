# transaction-100m-post-export-retest-vu3-20260427-summary

## Archive Metadata

- archivedAt: 2026-04-27T04:05:56Z
- sourceMarkdown: build/reports/k6/transaction-100m-post-export-retest-vu3-20260427-summary.md
- sourceJson: build/reports/k6/transaction-100m-post-export-retest-vu3-20260427-summary.json

# k6 Transaction 100m Load Test

## Environment

- baseUrl: http://aquila-bank-backend:8080
- vus: 3
- duration: 30s
- scenario mode: constant-vus
- arrival rate: 8/1s
- burst rate: 16/1s
- burst duration: 20s
- pre allocated VUs: 3
- max VUs: 3
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
- hot first p95 ms: 5.243299899999982
- hot first p99 ms: n/a
- hot first max ms: 294.020958
- hot cursor p95 ms: 5.1392373
- hot cursor p99 ms: n/a
- hot cursor max ms: 151.691583
- cold first p95 ms: 4.864229499999994
- cold first p99 ms: n/a
- cold first max ms: 135.343417
- cold cursor p95 ms: 5.040824899999994
- cold cursor p99 ms: n/a
- cold cursor max ms: 136.88825

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- overload mode에서는 admission guard 429를 rejected sample로 집계합니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- observability mode가 `summary-only`이면 Prometheus remote write 없이 summary 파일만 남깁니다.
