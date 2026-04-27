# transaction-100m-post-overload-profile-vu16-overload-20260427-summary

## Archive Metadata

- archivedAt: 2026-04-27T13:06:50Z
- sourceMarkdown: build/reports/k6/transaction-100m-post-overload-profile-vu16-overload-20260427-summary.md
- sourceJson: build/reports/k6/transaction-100m-post-overload-profile-vu16-overload-20260427-summary.json

# k6 Transaction 100m Load Test

## Environment

- baseUrl: http://aquila-bank-backend:8080
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

- http_req_failed rate: 0.0035115521060308658
- checks rate: 1
- transaction 429 rate: 0.003959109504908281
- transaction 503 rate: 0
- transaction 503 count: 0
- hot first p95 ms: 1.2469707999999997
- hot first p99 ms: 3.5181605599999988
- hot first p99.9 ms: 12.691568750000195
- hot first max ms: 44.580709
- hot cursor p95 ms: 1.2110400999999988
- hot cursor p99 ms: 2.9920076799999973
- hot cursor p99.9 ms: 13.998792918000175
- hot cursor max ms: 56.253625
- cold first p95 ms: 1.1627785499999999
- cold first p99 ms: 2.805119789999999
- cold first p99.9 ms: 13.728642821000262
- cold first max ms: 55.933709
- cold cursor p95 ms: 1.169051499999999
- cold cursor p99 ms: 2.830197999999997
- cold cursor p99.9 ms: 13.549271975000021
- cold cursor max ms: 53.407458

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- overload mode에서는 admission guard 429를 rejected sample로 집계합니다.
- overload mode에서도 503은 app/backend failure 신호라 hard fail로 분리합니다.
- warmup phase는 endpoint/JVM/cache/pool 초기화를 분리하고, custom latency Trend는 measured phase만 기록합니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- observability mode가 `summary-only`이면 Prometheus remote write 없이 summary 파일만 남깁니다.
