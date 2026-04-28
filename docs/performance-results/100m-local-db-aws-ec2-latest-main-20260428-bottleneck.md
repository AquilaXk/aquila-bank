# 100m local DB AWS EC2 latest-main bottleneck

## Summary

- 기준: `main @ 00f5bef`
- 이슈: #525
- 브랜치: `perf/100m-local-docker-primary-latest-main-retest`
- 목적: 로컬 Docker PostgreSQL 18의 1억 row fixture를 DB primary evidence로 두고 AWS App EC2 경로에서 실제 부하 전제 조건을 검증
- 결론: 로컬 1억 row 조회 smoke는 통과했지만, EC2 전체 부하는 nginx backend proxy 400과 prod 인증 계약 401 때문에 아직 의미 있게 실행할 수 없다.

## Environment

| Component | Result | Evidence |
| --- | --- | --- |
| local PostgreSQL | up, healthy | `aquila-bank-postgres-loadtest`, port `15432` |
| local backend | up | `aquila-bank-backend-loadtest`, port `18080` |
| local observability | up | Prometheus `19090`, Grafana `13001`, exporter `19187` |
| AWS App EC2 | reachable | frontend `/` returns 200 |
| EC2 backend direct | reachable | `127.0.0.1:18080/actuator/health` returns 200 |
| EC2 nginx backend proxy | failed | `/actuator/health`, `/api/v1/transactions` return 400 |
| EC2 DB tunnel | listener exists | `127.0.0.1:25432` on EC2 host |

운영 URL, secret, token 값은 문서에 기록하지 않았다.

## Local 100m Fixture Probe

Command:

```bash
LOADTEST_POSTGRES_CONTAINER_NAME=aquila-bank-postgres-loadtest \
  tools/test/run-transaction-100m-fixture-dataset-probe.sh
```

Result:

| Check | Status | Value |
| --- | --- | ---: |
| hot rows estimate | pass | 50,000,000 |
| archive rows estimate | pass | 50,000,028 |
| total rows estimate | pass | 100,000,028 |
| hot bounded window | pass | 51 |
| cold bounded window | pass | 51 |

## Local 100m k6 Smoke

Command:

```bash
K6_HOT_ACCOUNT_ID=910000001 \
K6_HOT_FROM=2026-04-01T00:00:00Z \
K6_HOT_TO=2026-04-30T00:00:00Z \
K6_COLD_ACCOUNT_ID=910000002 \
K6_COLD_FROM=2026-01-01T00:00:00Z \
K6_COLD_TO=2026-01-31T00:00:00Z \
K6_REPORT_NAME=transaction-100m-local-db-ec2-blocked-smoke-20260428 \
K6_RUN_ID=transaction-100m-local-db-ec2-blocked-smoke-20260428 \
K6_GENERATOR_MODE=local \
K6_RUN_PURPOSE=smoke \
K6_OBSERVABILITY_MODE=summary-only \
K6_VUS=3 \
K6_DURATION=30s \
K6_WARMUP_DURATION=0s \
K6_EXPLAIN_SNAPSHOT=false \
K6_POSTGRES_EXPORTER_STABLE_GATE=false \
K6_POSTGRES_RECOVERY_NOISE_WINDOW_SECONDS=0 \
tools/test/run-k6-transaction-100m-loadtest.sh --no-up --no-deps
```

Result:

| Metric | Value |
| --- | ---: |
| iterations | 19,698 |
| interrupted iterations | 0 |
| http failed rate | 0 |
| checks rate | 1 |
| transaction 429 rate | 0 |
| transaction 503 rate | 0 |
| hot first p95 | 1.569 ms |
| hot cursor p95 | 1.558 ms |
| cold first p95 | 1.589 ms |
| cold cursor p95 | 1.599 ms |

Archived k6 result:

- `docs/performance-results/k6-smoke/transaction-100m-local-db-ec2-blocked-smoke-20260428-summary.md`

## EC2 Preflight

Public EC2 nginx:

| Endpoint | Result |
| --- | --- |
| `GET /` | 200 |
| `GET /actuator/health` | 400 |
| `GET /api/v1/transactions?...` | 400 |

EC2 internal:

| Endpoint | Result |
| --- | --- |
| `GET 127.0.0.1:18080/actuator/health` | 200 |
| `GET 127.0.0.1:18080/api/v1/transactions?...` with k6 bootstrap headers | 401 |
| `GET 127.0.0.1/actuator/health` through nginx | 400 |

Backend log excerpt identified the nginx proxy root cause:

```text
java.lang.IllegalArgumentException: The character [_] is never valid in a domain name.
```

The current rendered nginx config uses upstream names with underscores:

```nginx
upstream aquila_bank_backend {
  server aquila-bank-backend-a:8080;
}
```

`ops/deploy/ec2/bluegreen-deploy.sh` sets `proxy_set_header Host $host` at the server level, but each backend location also sets `proxy_set_header Connection ""`. In nginx, lower-level `proxy_set_header` directives stop inheritance of the parent `proxy_set_header` set, so the proxied Host falls back to `$proxy_host`, which is `aquila_bank_backend`. Tomcat 11 rejects that Host because underscores are invalid in domain names.

The direct backend transaction query returned 401 because EC2 runs prod security. The k6 script relies on `X-Account-Id`/`X-Subject` bootstrap auth, but `BootstrapHeaderAuthenticationFilter` is only active for `dev` and `test` profiles. This is correct for production safety, but it means EC2 loadtest needs a separate safe auth contract or fixture JWT path.

## Bottleneck Order

1. EC2 nginx backend proxy returns 400 before any backend query load can be measured.
2. EC2 direct backend transaction read returns 401, so k6 cannot exercise the 1억 row query endpoint in prod mode with current bootstrap headers.
3. There is no EC2 Docker context/off-host k6 runner configured; current `.env.local-capacity` points to local Docker `desktop-linux`.
4. Local DB and local backend 1억 row bounded read path passed a short smoke, so the next blocker is EC2 edge/runtime wiring, not the read query itself.

## Excluded Completed Work

Already merged and not listed again as new work:

- #504 `[Fix] t3micro aggregate required capacity prerequisite 실패를 non-zero 처리`
- #506 `[Perf] 100m capacity evidence collection 보강`
- #519 `[Chore] 100m 로컬 Docker PostgreSQL 기준 전환`
- #520 `[Chore] AWS App EC2 단독 Terraform 전환`
- #522 `[CI] EC2 blue-green 무중단 CD 구성`
- #492, #488, #486, #484, #472, #470, #460 계열 k6/observability/off-host runner 보강

## Next Issue/PR Work List

| Order | Issue/PR title | Template | Branch | Why now |
| ---: | --- | --- | --- | --- |
| 1 | `[Fix] EC2 nginx backend proxy Host header 400 수정` | `bug_report.yml` | `fix/ec2-nginx-backend-host-header` | backend 경유 health/API가 400이라 EC2 부하 테스트 자체가 막힌다. Location별 `Host`, `X-Forwarded-*` 전달 또는 hyphen upstream으로 Tomcat Host reject를 제거한다. |
| 2 | `[Perf] EC2 local DB loadtest 인증 계약 추가` | `performance_request.yml` | `perf/ec2-local-db-loadtest-auth-contract` | prod profile에서는 bootstrap header auth가 비활성이라 k6가 401만 받는다. 운영 노출 없이 fixture JWT 또는 제한된 loadtest profile 계약을 고정한다. |
| 3 | `[Perf] EC2 local DB tunnel preflight 추가` | `performance_request.yml` | `perf/ec2-local-db-tunnel-preflight` | EC2 host의 `127.0.0.1:25432` listener 존재만으로는 backend container에서 로컬 DB fixture 접근 가능성을 닫을 수 없다. container→host-gateway→PostgreSQL read-only probe를 추가한다. |
| 4 | `[Perf] EC2 direct-backend 100m k6 smoke runner 추가` | `performance_request.yml` | `perf/ec2-direct-backend-100m-k6-smoke` | nginx를 제외하고 EC2 backend+로컬 DB 조합의 순수 query latency를 먼저 측정한다. |
| 5 | `[Perf] EC2 nginx 경유 100m k6 smoke runner 추가` | `performance_request.yml` | `perf/ec2-nginx-100m-k6-smoke` | #1/#2 이후 nginx 경유 latency/429/503을 direct backend와 비교해 edge overhead를 분리한다. |
| 6 | `[Perf] EC2 local DB 307/s burst gate 추가` | `performance_request.yml` | `perf/ec2-local-db-307-burst-gate` | 로컬에서 검증한 120% 목표선을 EC2 App 경로에서도 같은 기준으로 닫는다. |
| 7 | `[Perf] EC2 t3.small CPU credit 및 Docker resource snapshot 연결` | `performance_request.yml` | `perf/ec2-t3small-resource-snapshot` | CloudWatch collector는 있으나 현재 local placeholder env라 EC2 실제 CPU credit/Network/EBS 증거가 없다. |
| 8 | `[CI] EC2 blue-green post-switch backend API smoke gate 추가` | `task_request.yml` | `ci/ec2-bluegreen-backend-api-smoke-gate` | #522 배포 smoke가 프런트 200만으로 통과해 nginx backend 400을 잡지 못했다. `/actuator/health`, 대표 `/api` 경로를 배포 직후 검증한다. |
| 9 | `[Perf] EC2 direct vs nginx latency comparison report 추가` | `performance_request.yml` | `perf/ec2-direct-nginx-latency-comparison` | nginx 수정 후 direct/backend와 public/nginx p95/p99 차이를 같은 run id로 비교해 병목 위치를 수치화한다. |
| 10 | `[Chore] EC2 local DB capacity env template 분리` | `task_request.yml` | `chore/ec2-local-db-capacity-env-template` | `.env.local-capacity`는 local Docker context용이다. EC2 App + local DB + SSH tunnel용 local-only template과 doctor를 분리해야 재현성이 생긴다. |

## Decision

EC2 full load/burst는 지금 실행하지 않는다. 현재 상태에서 실행하면 400/401 precondition failure만 증폭하므로, 먼저 #1과 #2를 닫은 뒤 direct-backend smoke → nginx smoke → 307/s burst 순서로 진행한다.
