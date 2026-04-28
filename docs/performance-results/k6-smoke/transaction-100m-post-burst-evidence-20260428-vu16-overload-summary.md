# transaction-100m-post-burst-evidence-20260428-vu16-overload-summary

## Archive Metadata

- archivedAt: 2026-04-28T02:08:24Z
- resultPurpose: smoke
- resultStatus: 0
- reportClass: transaction-100m-smoke
- sourceMarkdown: build/reports/k6/transaction-100m-post-burst-evidence-20260428-vu16-overload-summary.md
- sourceJson: build/reports/k6/transaction-100m-post-burst-evidence-20260428-vu16-overload-summary.json

# k6 Transaction 100m Load Test

## Environment

- baseUrl: http://aquila-bank-backend:8080
- run id: transaction-100m-post-burst-evidence-20260428
- vus: 16
- duration: 30s
- warmup duration: 10s
- scenario mode: constant-vus
- arrival rate: 8/1s
- burst rate: 16/1s
- burst duration: 30s
- pre allocated VUs: 16
- max VUs: 16
- limit: 50
- observability mode: prometheus
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
- overload 429 rate threshold: 0.015
- burst 429 rate threshold: 0.1
- effective overload 429 rate threshold: 0.015
- overload 503 rate threshold: 0

## Results

- http_req_failed rate: 0.007374491821877659
- checks rate: 1
- transaction 429 rate: 0.008831321754489255
- transaction 503 rate: 0
- transaction 503 count: 0
- hot first p95 ms: 4.458229499999998
- hot first p99 ms: 14.483657759999991
- hot first p99.9 ms: 69.30988988801221
- hot first max ms: 242.899376
- hot cursor p95 ms: 4.1985874999999995
- hot cursor p99 ms: 12.698629869999989
- hot cursor p99.9 ms: 52.1499517690054
- hot cursor max ms: 123.181542
- cold first p95 ms: 4.166249799999998
- cold first p99 ms: 14.096815039999989
- cold first p99.9 ms: 55.00630229600262
- cold first max ms: 243.024083
- cold cursor p95 ms: 4.0405415
- cold cursor p99 ms: 12.667896499999998
- cold cursor p99.9 ms: 59.10028305000225
- cold cursor max ms: 123.447708

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- overload mode에서는 admission guard 429를 rejected sample로 집계합니다.
- overload mode에서도 503은 app/backend failure 신호라 hard fail로 분리합니다.
- warmup phase는 endpoint/JVM/cache/pool 초기화를 분리하고, custom latency Trend는 measured phase만 기록합니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- observability mode가 `prometheus`이면 Prometheus remote write와 summary 파일을 함께 남깁니다.
