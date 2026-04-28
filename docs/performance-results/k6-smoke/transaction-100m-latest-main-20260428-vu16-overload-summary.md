# transaction-100m-latest-main-20260428-vu16-overload-summary

## Archive Metadata

- archivedAt: 2026-04-28T00:23:38Z
- resultPurpose: smoke
- reportClass: transaction-100m-smoke
- sourceMarkdown: build/reports/k6/transaction-100m-latest-main-20260428-vu16-overload-summary.md
- sourceJson: build/reports/k6/transaction-100m-latest-main-20260428-vu16-overload-summary.json

# k6 Transaction 100m Load Test

## Environment

- baseUrl: http://aquila-bank-backend:8080
- run id: transaction-100m-latest-main-20260428-vu16-overload
- vus: 16
- duration: 30s
- warmup duration: 10s
- scenario mode: constant-vus
- arrival rate: 8/1s
- burst rate: 16/1s
- burst duration: 20s
- pre allocated VUs: 16
- max VUs: 16
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
- overload 429 rate threshold: 0.015
- burst 429 rate threshold: 0.1
- effective overload 429 rate threshold: 0.015
- overload 503 rate threshold: 0

## Results

- http_req_failed rate: 0.005267209594424861
- checks rate: 1
- transaction 429 rate: 0.005702337958563011
- transaction 503 rate: 0
- transaction 503 count: 0
- hot first p95 ms: 2.3940358999999996
- hot first p99 ms: 9.138222159999994
- hot first p99.9 ms: 26.97497265800048
- hot first max ms: 72.775
- hot cursor p95 ms: 2.2502080999999996
- hot cursor p99 ms: 7.546341239999997
- hot cursor p99.9 ms: 29.44489662700048
- hot cursor max ms: 60.862834
- cold first p95 ms: 2.1458436999999986
- cold first p99 ms: 6.343147499999979
- cold first p99.9 ms: 22.596532208001733
- cold first max ms: 56.498042
- cold cursor p95 ms: 2.264982899999999
- cold cursor p99 ms: 7.184074999999996
- cold cursor p99.9 ms: 31.5724690260052
- cold cursor max ms: 64.385334

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- overload mode에서는 admission guard 429를 rejected sample로 집계합니다.
- overload mode에서도 503은 app/backend failure 신호라 hard fail로 분리합니다.
- warmup phase는 endpoint/JVM/cache/pool 초기화를 분리하고, custom latency Trend는 measured phase만 기록합니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- observability mode가 `summary-only`이면 Prometheus remote write 없이 summary 파일만 남깁니다.
