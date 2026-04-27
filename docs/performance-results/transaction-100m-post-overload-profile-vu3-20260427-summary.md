# transaction-100m-post-overload-profile-vu3-20260427-summary

## Archive Metadata

- archivedAt: 2026-04-27T13:05:29Z
- sourceMarkdown: build/reports/k6/transaction-100m-post-overload-profile-vu3-20260427-summary.md
- sourceJson: build/reports/k6/transaction-100m-post-overload-profile-vu3-20260427-summary.json

# k6 Transaction 100m Load Test

## Environment

- baseUrl: http://aquila-bank-backend:8080
- vus: 3
- duration: 30s
- warmup duration: 10s
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
- hot p99.9 threshold ms: 1200
- cold p99.9 threshold ms: 2500
- hot max threshold ms: 3000
- cold max threshold ms: 5000
- http failed rate threshold: 0.01
- overload 429 rate threshold: disabled outside overload mode
- burst 429 rate threshold: disabled outside overload mode
- effective overload 429 rate threshold: disabled outside overload mode
- overload 503 rate threshold: disabled outside overload mode

## Results

- http_req_failed rate: 0
- checks rate: 1
- transaction 429 rate: 0
- transaction 503 rate: 0
- transaction 503 count: n/a
- hot first p95 ms: 1.187999599999999
- hot first p99 ms: 2.4908886399999974
- hot first p99.9 ms: 12.544145136000854
- hot first max ms: 41.390709
- hot cursor p95 ms: 1.1993251999999999
- hot cursor p99 ms: 2.639366999999998
- hot cursor p99.9 ms: 12.461267228000574
- hot cursor max ms: 38.631833
- cold first p95 ms: 1.2362831999999992
- cold first p99 ms: 2.5518852799999996
- cold first p99.9 ms: 13.555647864000811
- cold first max ms: 36.461792
- cold cursor p95 ms: 1.2361917999999985
- cold cursor p99 ms: 2.6588627199999966
- cold cursor p99.9 ms: 12.874571908000092
- cold cursor max ms: 42.823709

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- overload mode에서는 admission guard 429를 rejected sample로 집계합니다.
- overload mode에서도 503은 app/backend failure 신호라 hard fail로 분리합니다.
- warmup phase는 endpoint/JVM/cache/pool 초기화를 분리하고, custom latency Trend는 measured phase만 기록합니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- observability mode가 `summary-only`이면 Prometheus remote write 없이 summary 파일만 남깁니다.
