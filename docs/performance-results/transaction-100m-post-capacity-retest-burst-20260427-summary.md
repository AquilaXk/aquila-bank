# transaction-100m-post-capacity-retest-burst-20260427-summary

## Archive Metadata

- archivedAt: 2026-04-27T05:53:45Z
- sourceMarkdown: build/reports/k6/transaction-100m-post-capacity-retest-burst-20260427-summary.md
- sourceJson: build/reports/k6/transaction-100m-post-capacity-retest-burst-20260427-summary.json

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
- hot max threshold ms: 3000
- cold max threshold ms: 5000
- http failed rate threshold: disabled in overload mode
- overload 429 rate threshold: 0.2
- overload 503 rate threshold: 0

## Results

- http_req_failed rate: 0.008125977893302378
- checks rate: 1
- transaction 429 rate: 0.008125977893302378
- transaction 503 rate: 0
- transaction 503 count: 0
- hot first p95 ms: 2.3876331999999985
- hot first p99 ms: 6.821414999999995
- hot first max ms: 386.442459
- hot cursor p95 ms: 1.9169204999999994
- hot cursor p99 ms: 4.57220683
- hot cursor max ms: 383.815584
- cold first p95 ms: 1.8218669999999997
- cold first p99 ms: 3.804214999999998
- cold first max ms: 382.932292
- cold cursor p95 ms: 1.9059315499999976
- cold cursor p99 ms: 4.07786492
- cold cursor max ms: 384.288625

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- overload mode에서는 admission guard 429를 rejected sample로 집계합니다.
- overload mode에서도 503은 app/backend failure 신호라 hard fail로 분리합니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- observability mode가 `summary-only`이면 Prometheus remote write 없이 summary 파일만 남깁니다.
