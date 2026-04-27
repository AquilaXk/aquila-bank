# transaction-100m-post-capacity-retest-vu16-overload-20260427-summary

## Archive Metadata

- archivedAt: 2026-04-27T05:51:46Z
- sourceMarkdown: build/reports/k6/transaction-100m-post-capacity-retest-vu16-overload-20260427-summary.md
- sourceJson: build/reports/k6/transaction-100m-post-capacity-retest-vu16-overload-20260427-summary.json

# k6 Transaction 100m Load Test

## Environment

- baseUrl: http://aquila-bank-backend:8080
- vus: 16
- duration: 30s
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
- hot max threshold ms: 3000
- cold max threshold ms: 5000
- http failed rate threshold: disabled in overload mode
- overload 429 rate threshold: 0.2
- overload 503 rate threshold: 0

## Results

- http_req_failed rate: 0.005822025169062654
- checks rate: 1
- transaction 429 rate: 0.005822025169062654
- transaction 503 rate: 0
- transaction 503 count: 0
- hot first p95 ms: 2.308443749999999
- hot first p99 ms: 8.818320479999999
- hot first max ms: 183.138583
- hot cursor p95 ms: 2.217042
- hot cursor p99 ms: 8.815116799999997
- hot cursor max ms: 154.264458
- cold first p95 ms: 2.1418578
- cold first p99 ms: 8.943271359999981
- cold first max ms: 215.395125
- cold cursor p95 ms: 2.159458
- cold cursor p99 ms: 9.204483399999999
- cold cursor max ms: 214.985917

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- overload mode에서는 admission guard 429를 rejected sample로 집계합니다.
- overload mode에서도 503은 app/backend failure 신호라 hard fail로 분리합니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- observability mode가 `summary-only`이면 Prometheus remote write 없이 summary 파일만 남깁니다.
