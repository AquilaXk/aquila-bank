# 100m defensive post-burst-capacity-evidence latest-main bottleneck

## 기준

- 기준 브랜치: `main`
- 기준 SHA: `63a5e44`
- issue: #491
- 실행일: 2026-04-28
- 환경: local Docker loadtest stack, t3.micro 근사 compose, PostgreSQL 18.3, k6, Prometheus, Grafana
- 제외: 이미 main에 반영된 #490, #488, #486, #484, #472, #438, #435 작업은 후속 리스트에서 제외한다.

## 준비 상태

| 항목 | 결과 | 근거 |
| --- | --- | --- |
| backend readiness | 통과 | `GET /actuator/health/readiness` -> `UP` |
| Prometheus readiness | 통과 | `/-/ready` -> ready |
| Grafana health | 통과 | `/api/health` -> `ok` |
| PostgreSQL recovery | 통과 | `pg_is_in_recovery() = f` |
| postgres-exporter | 통과 | Prometheus `pg_up = 1` |
| Docker OOMKilled | 통과 | backend/postgres/prometheus/grafana/alertmanager/exporter 모두 `OOM=false` |

## 100m fixture probe

| 항목 | 값 |
| --- | ---: |
| total estimate | `99,999,884` |
| hot estimate | `49,999,856` |
| archive estimate | `50,000,028` |
| hot bounded window | `51` |
| cold bounded window | `51` |
| result | 통과 |

## k6 multi-scenario

Run id: `transaction-100m-post-burst-evidence-20260428`

| 시나리오 | 결과 | 429 rate | 503 rate | dropped | interrupted | p95 hot first | p95 cold first | 판단 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| VU3 baseline | 통과 | `0` | `0` | `0` | `0` | `2.0797479ms` | `2.0774723ms` | 1억 row bounded read 자체는 여유 있음 |
| VU16 overload | 통과 | `0.0088313218` | `0` | `0` | `0` | `4.4582295ms` | `4.1662498ms` | admission 429 예산 내, 503 없음 |
| burst 256/s | 통과 | `0.0096446954` | `0` | `0` | `0` | `3.0585421ms` | `1.8525083ms` | #490 headroom 보강 후 generator sizing 병목 해소 |

EXPLAIN snapshot은 hot/cold first/cursor 모두 월별 partition index scan 또는 cursor용 index-only scan 경로를 사용했다. `Seq Scan`과 `Sort`는 확인되지 않았다.

## aggregate

| 항목 | 결과 | 근거 |
| --- | --- | --- |
| capacity prerequisite | 실패 | `CAPACITY_K6_DOCKER_CONTEXT,CAPACITY_K6_REMOTE_BASE_URL,CAPACITY_K6_REMOTE_PROMETHEUS_RW_SERVER_URL` 누락 |
| Docker context | 제한 | `default`, `desktop-linux`만 존재 |
| aggregate auto k6 input | 통과 | run-id 기준 `vu3` 대표 summary 선택, stale failed burst summary 미선택 |
| aggregate memory input | 통과 | `post-burst-evidence-20260428-memory-summary.tsv` 선택 |
| aggregate total memory | 통과 | `663.61MiB / 900MiB` |
| aggregate required gate 의미 | 미흡 | `required_gates=capacity,k6,memory`인데 capacity prerequisite `failed` 상태에서도 runner exit code는 `0` |

## 병목 판단

거래 조회 read path는 현재 병목이 아니다. 1억 row fixture에서 VU3, VU16 overload, 256/s burst 모두 index 기반으로 통과했고 503, dropped iteration, interrupted iteration이 모두 0이다.

현재 1순위 병목은 실제 capacity 증거 공백이다. 로컬 Docker는 CPU credit, RDS recovery, gp3 IO, 실제 네트워크를 재현하지 못하며, 현재 로컬에는 off-host k6 실행에 필요한 Docker context와 remote endpoint 값이 없다.

2순위 병목은 gate semantics다. aggregate는 capacity prerequisite 실패를 문서에는 표시하지만, required capacity gate로 지정해도 non-zero로 실패하지 않았다. 이 상태에서는 capacity가 실제로 막혀도 자동화가 성공으로 오인할 수 있다.

3순위 병목은 burst spike 원인 상관 증거다. `run-burst-first-second-spike-correlation.sh` 결과는 dropped/interrupted/Insufficient VUs 0을 남겼지만 Prometheus first-window snapshot은 아직 `missing`이라 burst 초반 CPU/Hikari/DB 원인 확정에는 부족하다.

## 다음 작업 리스트

모두 issue 제목 = PR 제목, issue 1개 = PR 1개 기준이다. 아래 항목은 이번 최신 main 스캔에서 이미 merged 된 작업 제목과 중복되지 않도록 제외 확인했다.

| 순서 | issue/PR 제목 | 템플릿 | 브랜치 | 목적 |
| ---: | --- | --- | --- | --- |
| 1 | `[Fix] t3micro aggregate required capacity prerequisite 실패를 non-zero 처리` | `bug_report.yml` | `fix/t3micro-aggregate-required-capacity-fail` | capacity가 required인데 prerequisite failed여도 exit 0인 현재 gate 의미를 바로잡는다. |
| 2 | `[Perf] off-host 100m capacity baseline 최초 실행 결과 확정` | `performance_request.yml` | `perf/offhost-100m-capacity-baseline-evidence` | #490의 runner/doctor는 구현됐으므로, 실제 remote k6 context로 capacity/soak TSV를 생성해 목표 증거를 닫는다. |
| 3 | `[Perf] staging RDS gp3 100m 30m soak baseline 확정` | `performance_request.yml` | `perf/staging-rds-gp3-100m-soak-baseline` | #438은 smoke runner라서 RDS `db.t4g.small` + gp3 장시간 SLO/CPU credit/IO 한계를 아직 닫지 못했다. |
| 4 | `[Perf] transaction 100m burst 307/s 120% capacity probe 추가` | `performance_request.yml` | `perf/transaction-100m-burst-307-capacity-probe` | 256/s가 통과했으므로 120% 목표선인 약 307/s에서 429/503/dropped 한계를 빠르게 찾는다. |
| 5 | `[Perf] burst first-window Prometheus snapshot 자동 수집` | `performance_request.yml` | `perf/burst-first-window-prometheus-snapshot` | correlation artifact는 생겼지만 CPU/Hikari/PostgreSQL first-window snapshot이 missing이라 spike 원인 확정이 안 된다. |
| 6 | `[Perf] k6 multi-scenario peak CPU/memory sampling 통합` | `performance_request.yml` | `perf/k6-multi-scenario-peak-resource-sampling` | 현재 memory TSV는 수동 snapshot이다. VU3/VU16/burst 실행 중 peak resource를 자동 산출해야 t3.micro headroom을 신뢰할 수 있다. |
| 7 | `[Perf] t3micro defensive full aggregate 실측 리포트 추가` | `performance_request.yml` | `perf/t3micro-defensive-full-aggregate-evidence` | runner는 있으나 이번 aggregate는 k6/memory/capacity prerequisite만 연결됐다. SSE/admission/outbox까지 같은 run으로 묶어 방어 목표를 닫는다. |
| 8 | `[Perf] local Docker vs staging RDS 100m 최신 비교 결과 확정` | `performance_request.yml` | `perf/100m-local-staging-rds-comparison-evidence` | 비교 runner는 있으나 최신 local k6와 staging RDS 결과를 같은 표로 비교한 evidence가 없다. |
| 9 | `[Perf] capacity runner CloudWatch CPU credit/gp3 IO snapshot 연동` | `performance_request.yml` | `perf/capacity-cloudwatch-credit-gp3-snapshot` | 실제 목표 병목인 CPU credit, FreeableMemory, gp3 IO/queue depth를 capacity 결과에 같이 남긴다. |
| 10 | `[CI] off-host 100m capacity prerequisite required gate 추가` | `task_request.yml` | `ci/offhost-100m-capacity-prerequisite-gate` | remote k6 context/URL/Prometheus RW 누락을 PR 또는 수동 workflow에서 조기 fail-fast한다. |

## 실행 산출물

- `docs/performance-results/k6-smoke/transaction-100m-post-burst-evidence-20260428-vu3-summary.md`
- `docs/performance-results/k6-smoke/transaction-100m-post-burst-evidence-20260428-vu16-overload-summary.md`
- `docs/performance-results/k6-smoke/transaction-100m-post-burst-evidence-20260428-burst-256-summary.md`
- `docs/performance-results/t3micro-defensive-post-burst-evidence-20260428.md`
- `build/reports/k6/transaction-100m-post-burst-evidence-20260428-capacity/capacity-prerequisite-failure.env`
- `build/reports/profiling/burst-first-second-post-burst-evidence-20260428/burst-first-second-post-burst-evidence-20260428-first-3s-correlation.md`
