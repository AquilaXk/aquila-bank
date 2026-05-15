# OCI A1 100M load and traffic defense evidence 2026-05-15

## 기준

- 실행일: 2026-05-15 KST
- 코드 기준: `origin/main` `22969aa78a669eb3ce160c0771d370e92c3e9d52`
- 서버 제약: OCI A1 4 OCPU / 24GB / data 200GB, app + PostgreSQL 18 + Nginx
- 조회 계약: `accountId + 기간 + keyset cursor + limit=50`
- issue: #978
- 30m soak run id: `25903215403`
- burst defense run id: `25904269739`
- 100M replay probe run id: `25904478347`
- 30m soak artifact id: `7011528630`
- burst matrix artifact id: `7011595605`
- replay artifact id: `7011623139`

운영 URL, 인증값, database URL, token, runner 내부 경로 원문은 기록하지 않는다.

## 핵심 결론

- 30분 read soak는 `success`다. gate p95/p99/p99.9/max는 `7.28765 / 10.3369 / 19.795165344001298 / 209.895ms`이고, Hikari pending/warning, backend/unknown 429, 5xx, Nginx 499가 모두 `0`이다.
- burst defense matrix는 promotion target `80/s`까지 pass다. `32/48/64/80/s` 모두 429/5xx/499 없이 통과했고, `96/s`는 observe 구간에서 edge-only 429 rate `1.5365%`가 발생했다.
- 1억 건 fixture 상태는 replay preflight에서 row estimate `100075688`, planner stats freshness `ok`로 확인됐다.
- 100M replay latency workflow는 `hot_first iteration 1 returned HTTP 308`로 실패했다. 이는 HTTP -> HTTPS redirect를 replay curl이 follow하지 않는 계약 문제로 보이며, 이번 문서에서는 replay latency evidence로 사용하지 않는다.
- 가장 큰 과거 대비 개선값은 `20260501-rerun1`의 burst80 429 rate `59.99%`에서 이번 burst80 `0%`로 내려간 `100%` 감소다.

## 30m soak live evidence

| metric | value |
| --- | ---: |
| run id | `25903215403` |
| workflow | `Transaction Read 30m Soak Live Evidence` |
| conclusion | `success` |
| gate status | `pass` |
| k6 http reqs | `112351` |
| accepted 200 | `112331` |
| checks rate | `1` |
| http_req_failed rate | `0` |
| summary p95/p99/p99.9/max | `7.096039 / 9.951303 / 18.919816 / 209.895338ms` |
| gate p95/p99/p99.9/max | `7.28765 / 10.3369 / 19.795165344001298 / 209.895ms` |
| backend/unknown 429 | `0 / 0` |
| 5xx / Nginx 499 | `0 / 0` |
| Hikari pending max | `0` |
| Hikari validation warnings | `0` |
| PostgreSQL temp file delta | `0` |
| PostgreSQL checkpoint delta | `6` |
| Nginx request/upstream p95 | `5ms / 5ms` |

판단:

- 30분 지속 부하에서 query path, DB pool, Hikari lifetime, edge rejection, 5xx/499 모두 hard-zero gate를 통과했다.
- current run의 p95는 최신 안정 baseline `20260511-rerun19`의 evidence p95 `6.866ms`보다 약 `6.141%` 느리다. 다만 p99.9와 max가 낮고 hard-zero gate가 유지돼 장애성 회귀로 보지 않는다.

## burst traffic defense

| burst | status | accepted 200 | total 429 | edge 429 | backend 429 | 5xx | 499 | accepted p95 | retry-after p95 |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 32/s | pass | `3840` | `0` | `0` | `0` | `0` | `0` | `3.810ms` | `0ms` |
| 48/s | pass | `5766` | `0` | `0` | `0` | `0` | `0` | `3.720ms` | `0ms` |
| 64/s | pass | `7686` | `0` | `0` | `0` | `0` | `0` | `4.290ms` | `0ms` |
| 80/s | pass | `9606` | `0` | `0` | `0` | `0` | `0` | `5.050ms` | `0ms` |
| 96/s | observe | `10638` | `0.015365` | `0.015365` | `0` | `0` | `0` | `6.740ms` | `355.693ms` |

판단:

- 운영 목표가 `80/s`라면 현재 edge/backend 방어선은 통과다.
- `96/s`는 목표 초과 observe 구간이며, edge에서만 fail-fast 429가 발생한다. backend 429, 5xx, 499는 없다.
- app/DB 병목이 아니라 edge admission 경계로 분류한다.

## 100M replay probe

| item | value |
| --- | ---: |
| run id | `25904478347` |
| conclusion | `failure` |
| row estimate | `100075688` |
| hot table estimate/live tuples | `50075660 / 50075662` |
| archive table estimate/live tuples | `50000028 / 50000028` |
| planner stats age | `4.7h / 4.7h` |
| failure | `hot_first iteration 1 returned HTTP 308` |

판단:

- 1억 건 dataset과 planner stats freshness는 확인됐다.
- replay latency는 실패 run이라 개선 근거로 쓰지 않는다.
- 동일 API를 사용하는 k6 soak/burst는 authenticated token으로 pass했으므로, 현 병목은 read query 자체가 아니라 replay curl의 redirect 처리 또는 staging base URL 계약이다.

## 과거 결과 대비 개선율

| metric | baseline | current | improvement |
| --- | ---: | ---: | ---: |
| burst80 total 429 rate | `59.99%` | `0%` | `100.000%` 감소 |
| burst96 total 429 rate | `64.35%` | `1.5365%` | `97.612%` 감소 |
| burst80 p95 계열 | `405.74ms` | `5.050ms` | `98.755%` 감소 |
| burst96 p95 계열 | `408.71ms` | `6.740ms` | `98.351%` 감소 |
| VU16/30m soak p95 계열 | `392.76ms` | `7.28765ms` | `98.145%` 감소 |

비교 원칙:

- 위 개선율은 같은 traffic family 안에서만 해석한다.
- `burst80_total_429_rate`가 가장 개선폭이 크고, 트래픽 방어 품질을 가장 잘 보여준다.
- `VU16/30m soak`는 과거 2분 고정순서 run과 현재 30분 live evidence라 exact A/B가 아니다. 다만 현재 run이 더 긴 시간 동안 hard-zero gate를 통과했기 때문에 안정성 측면에서는 더 강한 증거다.
- raw 비교표는 `docs/performance-results/oci-a1-100m-load-defense-20260515-comparison.tsv`에 둔다.

## 후속 판단

- 이번 작업은 evidence/test/docs 작업이다. code/DB/runtime 비용을 새로 줄인 변경이 없으므로 `[Perf]` 개선 PR로 주장하지 않는다.
- 다음 실제 성능 개선 PR 후보는 운영 목표가 `96/s` 이상으로 올라갈 때의 edge admission retune이다.
- 100M replay workflow의 HTTP 308은 별도 `[Fix]` 또는 `[Test]`로 분리해 `STAGING_BASE_URL` HTTPS 계약 또는 curl redirect handling을 정리한다.
