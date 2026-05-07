# Prometheus Monitoring Baseline

`ops/prometheus`는 Aquila Bank backend가 이미 export 중인 metric을 기준으로 Grafana dashboard와 Prometheus alert rule baseline을 보관하는 디렉터리입니다. 실제 Prometheus server, Grafana provisioning, Alertmanager routing은 환경별로 다르므로 이번 baseline은 import/apply 가능한 자산만 저장소에 고정합니다.

OCI A1 Flex 4 OCPU / 24GB + data 200GB self-managed PostgreSQL 18 runtime에서는 Prometheus/Grafana/Alertmanager를 같은 app/DB host에 상시 필수 운영하지 않습니다. 이 디렉터리의 자산은 부하테스트 overlay, 별도 관측 host, 또는 장애 분석을 위한 단기 실행 기준으로 사용합니다.

## 포함 파일

- dashboard:
  - `grafana/aquila-bank-overview.json`
- alert rule:
  - `rules/aquila-bank-alerts.yml`
- Prometheus provisioning:
  - `prometheus.yml`
  - `prometheus.local-loadtest.yml`
- Grafana provisioning:
  - `grafana/provisioning/datasources/prometheus.yml`
  - `grafana/provisioning/dashboards/aquila-bank.yml`
- Alertmanager routing:
  - `alertmanager/alertmanager.yml`

## Dashboard Baseline

`grafana/aquila-bank-overview.json`는 현재 `back/README.md`의 Prometheus metric을 바로 볼 수 있게 만든 overview dashboard입니다.

- 포함 패널:
  - outbox dispatch lag / failed count / stale sending
  - notification consumer lag / DLQ count
  - SSE total session, account/user/total trend, reject/drop trend
  - auth throttling reject rate by `entry_point`, `scope`, `store`
  - admission control decision rate / inflight by `group`
  - legacy `t3micro` metric name의 saturation guard request, saturated, query timeout trend
  - DB pool pending/active pressure, query timeout, Postgres lock/slow query signal
  - transaction query rate by `query_shape`
  - transaction p95 latency by `query_shape`
- datasource 기준:
  - Grafana datasource variable 이름은 `datasource`입니다.
  - import 직후 Prometheus datasource를 한 번 선택하면 모든 panel이 그 값을 재사용합니다.
- query 기준:
  - transaction stat latency 평균값은 참고 panel로만 유지하고 alert SLO에는 사용하지 않습니다.
  - transaction shape별 latency SLO는 `aquila_transaction_query_latency_seconds_bucket`의 `histogram_quantile(0.95, ...)`, `histogram_quantile(0.99, ...)`를 사용합니다.
  - admission control panel은 `aquila_api_admission_requests_total`, `aquila_api_admission_inflight`를 `group` 기준으로 나눠 봅니다.
  - saturation guard panel은 reject rate, saturated gauge, query timeout rate를 같은 시간축에 두고 fail-fast와 DB 전조를 같이 봅니다.
  - DB saturation panel은 Hikari pool metric과 Postgres exporter metric을 한 panel에 모아 p95 악화 전 전조를 먼저 봅니다.
  - notification lag panel은 topic label이 있을 때 topic별로 분리해 보여줍니다.
  - SSE reject/drop metric은 앱 재시작 전까지 누적되는 gauge 성격이므로 절대값 trend로만 봅니다.

## Dashboard Import

1. Grafana UI에서 `Dashboards -> New -> Import`로 이동합니다.
2. `ops/prometheus/grafana/aquila-bank-overview.json`를 업로드합니다.
3. `datasource` 변수에 실제 Prometheus datasource를 연결합니다.
4. import 직후 panel query preview에서 다음 metric이 보이는지 먼저 확인합니다.
   - `aquila_outbox_dispatch_lag_seconds`
   - `aquila_notification_consumer_lag_count`
   - `aquila_notification_sse_sessions`
  - `aquila_auth_throttling_reject_count_total`
  - `aquila_api_admission_requests_total`
  - `aquila_api_admission_inflight`
  - `aquila_t3micro_saturation_guard_requests_total`
   - `aquila_transaction_query_latency_seconds_count`
   - `hikaricp_connections_pending`
   - `aquila_t3micro_saturation_guard_query_timeouts_total`

## Alert Rule Baseline

`rules/aquila-bank-alerts.yml`는 현재 README와 health/env 기본값을 따라가는 baseline rule 세트입니다.

- outbox baseline:
  - dispatch lag `> 120s`
  - failed count `> 10`
  - producer timeout failed count `> 0`
  - stale sending count `> 0`
- notification baseline:
  - consumer lag `> 100`
  - DLQ count `> 0`
  - SSE total session `> 56`
- auth baseline:
  - current session active gate reject rate `> 0` for `5m`
  - refresh token reuse detected rate `> 0` for `1m`
  - auth throttling reject increase `>= 5` for `10m`
- runtime guard baseline:
  - endpoint `group`별 admission reject increase `>= 5` for `10m`
  - saturation guard reject + saturated signal 동시 발생 for `5m`
- Postgres/DB saturation baseline:
  - Hikari pending connection `> 0` for `5m`
  - Hikari active/max pool ratio `> 90%` for `10m`
  - query timeout rate `> 0` for `5m`
  - `pg_stat_activity` lock wait custom metric `> 0` for `5m`
  - `pg_stat_statements` average query seconds `> 750ms` for `10m`
- transaction baseline:
  - success query p95 SLO: reference_exact `80ms`, first_page `120ms`, cursor/status/direction `150ms`, amount/mixed `180ms`
  - success query p99 SLO: reference_exact `160ms`, first_page `240ms`, cursor/status/direction `300ms`, amount/mixed `360ms`
  - transaction read 429 budget: `> 5%` for `2m`
  - transaction read 503 hard fail: `> 0` for `1m`

## Postgres Exporter Metrics

Hikari metric과 `aquila_t3micro_saturation_guard_query_timeouts_total`는 backend actuator scrape에서 바로 나옵니다. `pg_*` 계열 metric은 Postgres exporter scrape가 별도로 있어야 합니다.

- default collector 기준:
  - `pg_locks_count`는 lock mode별 보유 lock 수를 보여주며, blocking 원인 후보를 좁히는 보조 지표입니다.
  - `pg_stat_statements_*`는 `--collector.stat_statements=true`와 PostgreSQL `pg_stat_statements` extension이 필요합니다.
- custom lock wait metric:
  - alert rule은 `pg_stat_activity_lock_waiting_count`를 기대합니다.
  - exporter custom query는 `wait_event_type = 'Lock'`인 backend 수를 `datname`별 gauge로 노출합니다.
  - alert label에는 query text, pid, user, requestId를 올리지 않고 incident drill-down에서만 확인합니다.

## Alert Rule Apply

baseline 파일은 실제 Prometheus `rule_files` 경로에 복사하거나 symlink로 연결해 사용합니다.

예시:

```yaml
rule_files:
  - /etc/prometheus/rules/*.yml
```

실제 환경에서는 아래처럼 baseline 파일을 별도 경로로 배치합니다.

```bash
cp ops/prometheus/rules/aquila-bank-alerts.yml /etc/prometheus/rules/
```

그 다음 `promtool check rules` 또는 동등한 검증 후 Prometheus reload를 수행합니다.

## Provisioning Apply

저장소 baseline은 runtime을 강제하지 않고, compose/Kubernetes/systemd 어디서든 같은 mount path로 적용할 수 있게 둡니다. OCI A1 app/DB host에 상시 동거시키는 방식은 기본 운영 목표에서 제외합니다.

- Prometheus:
  - `ops/prometheus/prometheus.yml` -> `/etc/prometheus/prometheus.yml`
  - `ops/prometheus/rules/aquila-bank-alerts.yml` -> `/etc/prometheus/rules/aquila-bank-alerts.yml`
- Alertmanager:
  - `ops/prometheus/alertmanager/alertmanager.yml` -> `/etc/alertmanager/alertmanager.yml`
- Grafana:
  - `ops/prometheus/grafana/provisioning/datasources/prometheus.yml` -> `/etc/grafana/provisioning/datasources/prometheus.yml`
  - `ops/prometheus/grafana/provisioning/dashboards/aquila-bank.yml` -> `/etc/grafana/provisioning/dashboards/aquila-bank.yml`
  - `ops/prometheus/grafana/aquila-bank-overview.json` -> `/var/lib/grafana/dashboards/aquila-bank/aquila-bank-overview.json`

기본 service name은 `prometheus:9090`, `alertmanager:9093`, `aquila-bank-backend:8080`, `postgres-exporter:9187`입니다. 환경별 host, label, receiver sink는 overlay 또는 runtime secret으로 덮어씁니다.

## Local k6 Loadtest Runtime

`compose.loadtest.yml`은 저장소 baseline을 실제 local runtime으로 묶는 overlay입니다.

- backend: `aquila-bank-backend:8080`
- Prometheus: `localhost:9090`
- Grafana: `localhost:3001`
- Alertmanager: `localhost:9093`
- Postgres exporter: `localhost:9187`
- k6 remote write: `http://prometheus:9090/api/v1/write`

Prometheus는 k6 remote write를 받기 위해 `--web.enable-remote-write-receiver`로 실행됩니다. k6 실행 결과는 Prometheus/Grafana에 남고, Markdown/JSON summary는 `build/reports/k6`와 `docs/performance-results`에 남깁니다.

```bash
tools/test/run-k6-transaction-100m-loadtest.sh --print-plan
```

Alertmanager baseline receiver는 route 구조만 고정하며 실제 Slack/PagerDuty/Webhook/Telegram secret은 저장소에 두지 않습니다. 운영 환경에서는 `aquila-bank-critical`, `aquila-bank-warning` receiver에 환경별 notification config를 추가합니다.

배포 전 secret smoke는 아래 env/secret 이름을 기준으로 실제 receiver 구성을 확인합니다.

- 공통:
  - `ALERTMANAGER_RECEIVER_SLACK_ENABLED`
  - `ALERTMANAGER_RECEIVER_PAGERDUTY_ENABLED`
  - `ALERTMANAGER_RECEIVER_WEBHOOK_ENABLED`
  - `ALERTMANAGER_RECEIVER_TELEGRAM_ENABLED`
- Slack:
  - `ALERTMANAGER_RECEIVER_SLACK_WEBHOOK_URL`
- PagerDuty:
  - `ALERTMANAGER_RECEIVER_PAGERDUTY_ROUTING_KEY`
- Webhook:
  - `ALERTMANAGER_RECEIVER_WEBHOOK_URL`
- Telegram:
  - `ALERTMANAGER_RECEIVER_TELEGRAM_BOT_TOKEN`
  - `ALERTMANAGER_RECEIVER_TELEGRAM_CHAT_ID`

규칙은 단순합니다.

- staging/production 배포 전에는 최소 한 개 이상의 실제 receiver를 `enabled=true`로 둡니다.
- `enabled=true`인 receiver는 해당 secret이 비어 있으면 fail-fast 합니다.
- receiver를 쓰지 않으면 `enabled=false`로 두고 secret은 비워 둡니다.
- secret 값은 저장소에 기록하지 않고 GitHub Environment secret 또는 동등한 runtime secret으로만 주입합니다.

## Validation

로컬 baseline 자산 문법 확인은 아래 스크립트로 먼저 닫습니다.

```bash
bash tools/ops/validate-prometheus-assets.sh
ALERTMANAGER_RECEIVER_SECRET_SMOKE_ENVIRONMENT=staging \
ALERTMANAGER_RECEIVER_SLACK_ENABLED=true \
ALERTMANAGER_RECEIVER_SLACK_WEBHOOK_URL=https://hooks.slack.example/services/test \
  bash tools/ops/validate-alertmanager-receiver-secrets.sh
```

- dashboard JSON은 `uid`, panel 개수, JSON syntax를 같이 확인합니다.
- alert rule YAML은 group/rule/expr 존재 여부와 YAML syntax를 같이 확인합니다.
- Prometheus provisioning은 rule file, Alertmanager target, backend/Postgres scrape job을 확인합니다.
- Grafana provisioning은 datasource uid/type과 dashboard provider path를 확인합니다.
- staging/production workflow는 같은 `tools/ops/validate-alertmanager-receiver-secrets.sh`를 실제 secret/env로 실행합니다.
- Alertmanager routing은 severity grouping과 critical/warning receiver route를 확인합니다.
- receiver secret smoke는 실제 sink enable/secret 누락을 fail-fast로 확인합니다.
- 실제 Prometheus 적용 전에는 여기에 더해 `promtool check rules`를 추가로 수행합니다.

## Threshold Tuning

- outbox/notification threshold는 현재 README와 actuator health 기본값을 기준으로 둔 값입니다.
- Hikari pool alert는 `DB_POOL_MAX_SIZE=4`, `DB_CONNECTION_TIMEOUT_MS=3000`, `DB_LOCK_TIMEOUT_MS=1000`의 작은 단일 노드 기본값을 기준으로 둡니다. remote baseline은 OCI A1 4 OCPU / 24GB + data 200GB입니다.
- `AquilaDbPoolPendingWaitDetected`는 root cause가 아니라 queueing 전조입니다. 같은 시간대 lock wait, slow query, DB CPU, transaction p95를 같이 확인합니다.
- `AquilaPostgresSlowQueryDetected`는 `pg_stat_statements`의 database-level 평균을 사용합니다. query별 drill-down은 별도 dashboard 또는 psql에서 `queryid` 기준으로 수행합니다.
- `AquilaPostgresLockWaitDetected`는 custom exporter metric이 없으면 평가 series가 없으므로, 환경별 exporter 설정 적용 후 Prometheus rule을 활성화합니다.
- `AquilaNotificationSseSessionPressureHigh`의 `56`은 기본 `NOTIFICATION_SSE_MAX_TOTAL_SESSIONS=64`의 `87.5%` baseline입니다.
- transaction p95/p99 SLO는 baseline fixture 기준 회귀 감지선입니다. 실제 production에서는 `query_shape`, account volume, DB latency 분포를 보고 threshold와 `for` 시간을 같이 조정합니다.
- transaction read 429 alert는 burst admission budget 초과 신호이고, hot/archive admission group과 edge limiter를 분리해 확인합니다. 503 alert는 보호 실패 hard fail 신호입니다. 평균 latency alert는 tail latency를 가리므로 SLO 기준에서 제외합니다.
- notification lag/DLQ alert는 `NOTIFICATION_INBOX_CONSUMER_OPS_ENABLED=true`가 아니면 metric 자체가 export되지 않을 수 있습니다.
- multi-instance SSE 합계는 Grafana/Prometheus 쿼리에서 인스턴스 합산으로 해석하고, 단일 instance alert는 node별 pressure 확인 용도로만 씁니다.
- `AquilaCurrentSessionActiveGateRejectDetected`는 `reason_code`만 집계합니다. `requestId`, `userId`, `sessionId`, `path`는 cardinality 때문에 alert label로 올리지 않고 app structured log에서 drill-down합니다.
- `AquilaRefreshTokenReuseDetected`는 공격성 재사용 후보라 critical baseline입니다. `requestId`, `userId`, `reusedSessionId`, `familyRootId`는 cardinality 때문에 alert label로 올리지 않고 app structured log에서 drill-down합니다.
- `AquilaAuthThrottlingRejectBurstDetected`는 `entry_point`, `scope`, `store` 축만 사용합니다. IP, user, requestId, path는 cardinality 때문에 metric/alert label에 올리지 않고 app structured log에서 drill-down합니다.
- DB saturation alert rollback은 `aquila-bank-postgres` group 제거 또는 해당 rule의 threshold/`for` 시간 조정으로 수행합니다. 앱 API 계약과 DB schema는 그대로 유지합니다.
- provisioning rollback은 mount에서 해당 baseline 파일을 제거하거나 직전 runtime config로 되돌린 뒤 Prometheus/Grafana/Alertmanager를 reload합니다.
