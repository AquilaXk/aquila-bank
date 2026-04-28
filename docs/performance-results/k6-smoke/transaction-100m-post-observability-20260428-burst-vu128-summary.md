# k6 Transaction 100m Load Test

## Environment

- baseUrl: http://aquila-bank-backend:8080
- run id: transaction-100m-post-observability-20260428-burst-vu128
- vus: 16
- duration: 20s
- warmup duration: 10s
- scenario mode: burst
- arrival rate: 8/1s
- burst rate: 256/1s
- burst duration: 20s
- pre allocated VUs: 128
- max VUs: 128
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
- effective overload 429 rate threshold: 0.1
- overload 503 rate threshold: 0

## Results

- http_req_failed rate: 0.013227915462977042
- checks rate: 1
- transaction 429 rate: 0.017689218726172927
- transaction 503 rate: 0
- transaction 503 count: 0
- hot first p95 ms: 4.742162799999999
- hot first p99 ms: 23.157158219999996
- hot first p99.9 ms: 89.78088263600594
- hot first max ms: 166.650792
- hot cursor p95 ms: 3.053408049999997
- hot cursor p99 ms: 9.231369119999993
- hot cursor p99.9 ms: 50.39118696900009
- hot cursor max ms: 74.310708
- cold first p95 ms: 2.677958499999999
- cold first p99 ms: 7.345064599999991
- cold first p99.9 ms: 25.93357798500155
- cold first max ms: 108.231375
- cold cursor p95 ms: 2.5684249999999995
- cold cursor p99 ms: 7.785445159999998
- cold cursor p99.9 ms: 34.65676224200053
- cold cursor max ms: 246.723584

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- overload mode에서는 admission guard 429를 rejected sample로 집계합니다.
- overload mode에서도 503은 app/backend failure 신호라 hard fail로 분리합니다.
- warmup phase는 endpoint/JVM/cache/pool 초기화를 분리하고, custom latency Trend는 measured phase만 기록합니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- observability mode가 `summary-only`이면 Prometheus remote write 없이 summary 파일만 남깁니다.
