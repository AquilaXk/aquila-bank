# transaction-100m-post-guardrails-retest-prom-vu3-20260427-summary

## Archive Metadata

- archivedAt: 2026-04-27T06:58:26Z
- sourceMarkdown: build/reports/k6/transaction-100m-post-guardrails-retest-prom-vu3-20260427-summary.md
- sourceJson: build/reports/k6/transaction-100m-post-guardrails-retest-prom-vu3-20260427-summary.json

# k6 Transaction 100m Load Test

## Environment

- baseUrl: http://aquila-bank-backend:8080
- vus: 3
- duration: 15s
- scenario mode: constant-vus
- arrival rate: 8/1s
- burst rate: 16/1s
- burst duration: 20s
- pre allocated VUs: 3
- max VUs: 3
- limit: 50
- observability mode: prometheus
- overload mode: false
- max retry-after sleep seconds: 1
- hot account id: 910000001
- cold account id: 910000002
- hot p95 threshold ms: 350
- cold p95 threshold ms: 750
- hot p99 threshold ms: 750
- cold p99 threshold ms: 1500
- hot p99.9 threshold ms: 1200
- cold p99.9 threshold ms: 2500
- hot max threshold ms: 3000
- cold max threshold ms: 5000
- http failed rate threshold: 0.01
- overload 429 rate threshold: disabled outside overload mode
- overload 503 rate threshold: disabled outside overload mode

## Results

- http_req_failed rate: 0
- checks rate: 1
- transaction 429 rate: 0
- transaction 503 rate: 0
- transaction 503 count: n/a
- hot first p95 ms: 4.700962799999987
- hot first p99 ms: 15.922637419999978
- hot first p99.9 ms: 79.91549275002612
- hot first max ms: 191.57175
- hot cursor p95 ms: 4.788254199999996
- hot cursor p99 ms: 15.865966999999998
- hot cursor p99.9 ms: 60.090482750002515
- hot cursor max ms: 85.146667
- cold first p95 ms: 4.810591299999998
- cold first p99 ms: 14.646416459999994
- cold first p99.9 ms: 66.85986325000002
- cold first max ms: 166.624
- cold cursor p95 ms: 5.050245199999992
- cold cursor p99 ms: 17.143964999999973
- cold cursor p99.9 ms: 65.12198633800098
- cold cursor max ms: 199.87175

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- overload mode에서는 admission guard 429를 rejected sample로 집계합니다.
- overload mode에서도 503은 app/backend failure 신호라 hard fail로 분리합니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- observability mode가 `prometheus`이면 Prometheus remote write와 summary 파일을 함께 남깁니다.
