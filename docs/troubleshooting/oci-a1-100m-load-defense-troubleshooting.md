# OCI A1 100M load defense troubleshooting

## 목적

OCI A1 4 OCPU / 24GB + data 200GB에서 1억 건 transaction read model과 대용량 트래픽 방어를 점검할 때, 어떤 artifact를 신뢰하고 어떤 증거를 성능 개선으로 보지 말아야 하는지 정리한다.

## 이번 run 선택 기준

가장 개선폭이 큰 자료는 `burst80 total 429 rate`다.

| 비교 | 과거 | 현재 | 개선율 |
| --- | ---: | ---: | ---: |
| `20260501-rerun1` burst80 429 | `59.99%` | `0%` | `100.000%` 감소 |
| `20260501-rerun1` burst80 p95 계열 | `405.74ms` | `5.050ms` | `98.755%` 감소 |
| `20260501-rerun1` VU16/soak p95 계열 | `392.76ms` | `7.28765ms` | `98.145%` 감소 |

선택 이유:

- 429는 트래픽 방어선의 직접 지표다.
- `80/s`는 promotion target으로 gate가 적용되는 rate다.
- 현재 run은 429만 낮춘 것이 아니라 backend 429, 5xx, 499도 `0`으로 유지했다.

## 현재 정상 기준

### 30분 soak

- run id: `25903215403`
- p95/p99/p99.9/max: `7.28765 / 10.3369 / 19.795165344001298 / 209.895ms`
- accepted 200: `112331`
- backend/unknown 429: `0 / 0`
- 5xx/499: `0 / 0`
- Hikari pending/warning: `0 / 0`
- PostgreSQL temp file delta: `0`
- Nginx request/upstream p95: `5ms / 5ms`

정상 판정:

- Hikari pending과 validation warning이 0이면 DB pool 또는 connection lifetime 병목으로 보지 않는다.
- Nginx 5xx/499가 0이면 edge delay/timeout 장애로 보지 않는다.
- p99.9가 낮고 temp file delta가 0이면 1억 건 read query가 sort/temp spill로 무너진 상태가 아니다.

### burst defense

- run id: `25904269739`
- promotion target: `80/s`
- `80/s`: 429/5xx/499 `0`, accepted p95 `5.050ms`
- `96/s`: edge-only 429 rate `1.5365%`, backend 429/5xx/499 `0`

정상 판정:

- target rate에서 429가 0이면 방어선이 사용자 요청을 과도하게 잘라내지 않는다.
- target 초과 rate에서 edge-only 429가 발생하고 backend 429/5xx가 0이면 fail-fast 방어로 분류한다.
- backend 429가 발생하면 edge가 막기 전에 backend admission까지 압력이 내려간 것이므로 별도 병목 후보로 본다.

## HTTP 308 replay 실패 처리

100M replay probe run `25904478347`은 row estimate `100075688`과 planner stats freshness를 확인했지만, latency replay는 `hot_first iteration 1 returned HTTP 308`으로 실패했다.

원인 판단:

- 다운로드한 body는 Nginx `308 Permanent Redirect` HTML이다.
- 외부 `http://bank.aquilaxk.site/api/v1/transactions`는 `https://bank.aquilaxk.site/api/v1/transactions`로 308 redirect를 반환한다.
- replay script는 curl에 redirect follow 옵션을 주지 않는다.
- 따라서 이 run은 100M dataset 존재 증거로만 사용하고 replay latency evidence로 쓰지 않는다.

후속 처리:

- `STAGING_BASE_URL`은 HTTPS로 고정한다.
- 또는 replay script에서 redirect follow를 명시적으로 허용할지 별도 `[Fix]`로 검토한다.
- 단, k6 soak/burst가 이미 authenticated API를 성공 호출했으므로 read query 장애로 오판하지 않는다.

## 병목 분류 절차

1. row estimate가 `100000000` 이상인지 확인한다.
2. planner stats freshness가 `ok`인지 확인한다.
3. 30분 soak에서 Hikari pending/warning, 5xx, 499, backend/unknown 429를 먼저 본다.
4. burst matrix에서 target rate `80/s`와 observe rate `96/s`를 분리한다.
5. target rate 실패면 운영 방어선 회귀로 본다.
6. observe rate edge-only 429면 운영 목표 상향 전까지 장애성 병목으로 보지 않는다.
7. code/DB/runtime을 실제로 바꾸지 않았다면 개선 PR이 아니라 evidence 문서로만 남긴다.

## 다음 성능 개선 후보

| priority | type | 조건 | 작업 |
| --- | --- | --- | --- |
| P0 | `[Fix]` 또는 `[Test]` | 100M replay latency evidence가 꼭 필요할 때 | replay HTTP 308 base URL/redirect 계약 정리 |
| P1 | `[Perf]` | 운영 목표가 `96/s` 이상일 때 | edge admission budget retune, before/after burst96 live artifact 필요 |
| P1 | `[Perf]` | mixed workload write 429를 줄이는 목표가 확정될 때 | write edge rejection cost reduction, backend/unknown 429 및 5xx hard-zero 유지 |
| P2 | `[Test]` | source 다양성 검증 필요 | independent public multi-source evidence 추가 |

## 재실행 명령

GitHub Actions에서 실행한다. 운영 URL과 token은 secret에서만 읽고 문서에 남기지 않는다.

```bash
gh workflow run transaction-read-30m-soak-live-evidence.yml --ref main \
  -f report_name=oci-a1-100m-load-defense-YYYYMMDD-soak \
  -f duration=30m \
  -f vus=16 \
  -f hot_account_id=910000001 \
  -f cold_account_id=910000002 \
  -f hot_from=2026-04-01T00:00:00Z \
  -f hot_to=2026-04-30T00:00:00Z \
  -f cold_from=2026-01-01T00:00:00Z \
  -f cold_to=2026-01-31T00:00:00Z
```

```bash
gh workflow run oci-k6-burst-reject-curve-matrix.yml --ref main \
  -f report_name=oci-a1-100m-load-defense-YYYYMMDD-burst \
  -f duration=1m \
  -f burst_rates=32,48,64,80,96 \
  -f promotion_target_rate=80 \
  -f hot_account_id=910000001 \
  -f cold_account_id=910000002 \
  -f hot_from=2026-04-01T00:00:00Z \
  -f hot_to=2026-04-30T00:00:00Z \
  -f cold_from=2026-01-01T00:00:00Z \
  -f cold_to=2026-01-31T00:00:00Z
```
