# transaction-100m-post-burst-evidence-20260428-burst-256-summary

## Archive Metadata

- archivedAt: 2026-04-28T02:09:07Z
- resultPurpose: smoke
- resultStatus: 0
- reportClass: transaction-100m-smoke
- sourceMarkdown: build/reports/k6/transaction-100m-post-burst-evidence-20260428-burst-256-summary.md
- sourceJson: build/reports/k6/transaction-100m-post-burst-evidence-20260428-burst-256-summary.json

# k6 Transaction 100m Load Test

## Environment

- baseUrl: http://aquila-bank-backend:8080
- run id: transaction-100m-post-burst-evidence-20260428
- vus: 16
- duration: 20s
- warmup duration: 10s
- scenario mode: burst
- arrival rate: 8/1s
- burst rate: 256/1s
- burst duration: 20s
- pre allocated VUs: 256
- max VUs: 256
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
- effective overload 429 rate threshold: 0.1
- overload 503 rate threshold: 0

## Results

- http_req_failed rate: 0.0067270826071802025
- checks rate: 1
- transaction 429 rate: 0.009644695417520364
- transaction 503 rate: 0
- transaction 503 count: 0
- hot first p95 ms: 3.0585420999999964
- hot first p99 ms: 10.346523309999998
- hot first p99.9 ms: 94.3745919190033
- hot first max ms: 112.250417
- hot cursor p95 ms: 2.038731349999998
- hot cursor p99 ms: 4.963151509999999
- hot cursor p99.9 ms: 65.5250096250093
- hot cursor max ms: 117.504084
- cold first p95 ms: 1.8525082999999984
- cold first p99 ms: 4.686722719999999
- cold first p99.9 ms: 13.406779787001682
- cold first max ms: 84.993167
- cold cursor p95 ms: 1.8679437499999998
- cold cursor p99 ms: 4.684766839999999
- cold cursor p99.9 ms: 12.557088934000735
- cold cursor max ms: 81.927291

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- overload mode에서는 admission guard 429를 rejected sample로 집계합니다.
- overload mode에서도 503은 app/backend failure 신호라 hard fail로 분리합니다.
- warmup phase는 endpoint/JVM/cache/pool 초기화를 분리하고, custom latency Trend는 measured phase만 기록합니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- observability mode가 `prometheus`이면 Prometheus remote write와 summary 파일을 함께 남깁니다.
