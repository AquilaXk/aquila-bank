# transaction-100m-post-guardrails-retest-vu16-overload-20260427-summary

## Archive Metadata

- archivedAt: 2026-04-27T06:55:52Z
- sourceMarkdown: build/reports/k6/transaction-100m-post-guardrails-retest-vu16-overload-20260427-summary.md
- sourceJson: build/reports/k6/transaction-100m-post-guardrails-retest-vu16-overload-20260427-summary.json

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
- hot p99.9 threshold ms: 1200
- cold p99.9 threshold ms: 2500
- hot max threshold ms: 3000
- cold max threshold ms: 5000
- http failed rate threshold: disabled in overload mode
- overload 429 rate threshold: 0.2
- overload 503 rate threshold: 0

## Results

- http_req_failed rate: 0.007297904191616767
- checks rate: 1
- transaction 429 rate: 0.007297904191616767
- transaction 503 rate: 0
- transaction 503 count: 0
- hot first p95 ms: 3.1128830999999995
- hot first p99 ms: 11.89964698
- hot first p99.9 ms: 77.35917600000079
- hot first max ms: 309.076542
- hot cursor p95 ms: 3.1466266999999997
- hot cursor p99 ms: 11.563191749999985
- hot cursor p99.9 ms: 65.70182488600393
- hot cursor max ms: 252.560166
- cold first p95 ms: 2.973924899999999
- cold first p99 ms: 10.662286369999997
- cold first p99.9 ms: 44.110385821001046
- cold first max ms: 304.422167
- cold cursor p95 ms: 3.0614669999999986
- cold cursor p99 ms: 12.018496559999988
- cold cursor p99.9 ms: 53.05038294800629
- cold cursor max ms: 295.866875

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- overload mode에서는 admission guard 429를 rejected sample로 집계합니다.
- overload mode에서도 503은 app/backend failure 신호라 hard fail로 분리합니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- observability mode가 `summary-only`이면 Prometheus remote write 없이 summary 파일만 남깁니다.
