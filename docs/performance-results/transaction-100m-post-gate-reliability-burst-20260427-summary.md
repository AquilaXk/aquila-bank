# k6 Transaction 100m Load Test

## Environment

- baseUrl: http://aquila-bank-backend:8080
- vus: 16
- duration: 20s
- warmup duration: 10s
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
- overload 429 rate threshold: 0.02
- overload 503 rate threshold: 0

## Results

- http_req_failed rate: 0.07283236994219654
- checks rate: 1
- transaction 429 rate: 0.08730800323362975
- transaction 503 rate: 0
- transaction 503 count: 0
- hot first p95 ms: 22.501231099999927
- hot first p99 ms: 481.0704898699999
- hot first p99.9 ms: 1123.7619915240095
- hot first max ms: 1292.579001
- hot cursor p95 ms: 10.018908299999964
- hot cursor p99 ms: 88.57033323999983
- hot cursor p99.9 ms: 665.7824797080591
- hot cursor max ms: 934.60725
- cold first p95 ms: 8.394133399999998
- cold first p99 ms: 54.69290199999999
- cold first p99.9 ms: 156.31181014401585
- cold first max ms: 311.673375
- cold cursor p95 ms: 7.328677399999996
- cold cursor p99 ms: 60.988711359999954
- cold cursor p99.9 ms: 145.46916466400157
- cold cursor max ms: 1009.606917

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- overload mode에서는 admission guard 429를 rejected sample로 집계합니다.
- overload mode에서도 503은 app/backend failure 신호라 hard fail로 분리합니다.
- warmup phase는 endpoint/JVM/cache/pool 초기화를 분리하고, custom latency Trend는 measured phase만 기록합니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- observability mode가 `summary-only`이면 Prometheus remote write 없이 summary 파일만 남깁니다.
