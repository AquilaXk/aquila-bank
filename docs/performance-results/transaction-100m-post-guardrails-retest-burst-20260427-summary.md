# transaction-100m-post-guardrails-retest-burst-20260427-summary

## Archive Metadata

- archivedAt: 2026-04-27T06:57:01Z
- sourceMarkdown: build/reports/k6/transaction-100m-post-guardrails-retest-burst-20260427-summary.md
- sourceJson: build/reports/k6/transaction-100m-post-guardrails-retest-burst-20260427-summary.json

# k6 Transaction 100m Load Test

## Environment

- baseUrl: http://aquila-bank-backend:8080
- vus: 16
- duration: 20s
- scenario mode: burst
- arrival rate: 8/1s
- burst rate: 256/1s
- burst duration: 20s
- pre allocated VUs: 64
- max VUs: 64
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
- hot p99.9 threshold ms: 1200
- cold p99.9 threshold ms: 2500
- hot max threshold ms: 3000
- cold max threshold ms: 5000
- http failed rate threshold: disabled in overload mode
- overload 429 rate threshold: 0.2
- overload 503 rate threshold: 0

## Results

- http_req_failed rate: 0.014070962190615984
- checks rate: 1
- transaction 429 rate: 0.014070962190615984
- transaction 503 rate: 0
- transaction 503 count: 0
- hot first p95 ms: 3.158849799999999
- hot first p99 ms: 21.357377999999976
- hot first p99.9 ms: 66.25065072800278
- hot first max ms: 180.747584
- hot cursor p95 ms: 2.2729285
- hot cursor p99 ms: 6.935003919999991
- hot cursor p99.9 ms: 42.479320000010965
- hot cursor max ms: 145.552917
- cold first p95 ms: 1.9959833999999999
- cold first p99 ms: 5.309453479999996
- cold first p99.9 ms: 23.490659396000982
- cold first max ms: 66.956791
- cold cursor p95 ms: 2.044345499999997
- cold cursor p99 ms: 4.92627641
- cold cursor p99.9 ms: 22.439585823000982
- cold cursor max ms: 82.400792

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- overload mode에서는 admission guard 429를 rejected sample로 집계합니다.
- overload mode에서도 503은 app/backend failure 신호라 hard fail로 분리합니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- observability mode가 `summary-only`이면 Prometheus remote write 없이 summary 파일만 남깁니다.
