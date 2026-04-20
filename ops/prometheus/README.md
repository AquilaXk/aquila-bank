# Prometheus Monitoring Baseline

`ops/prometheus`는 Aquila Bank backend가 이미 export 중인 metric을 기준으로 Grafana dashboard와 Prometheus alert rule baseline을 보관하는 디렉터리입니다. 실제 Prometheus server, Grafana provisioning, Alertmanager routing은 환경별로 다르므로 이번 baseline은 import/apply 가능한 자산만 저장소에 고정합니다.

## 포함 파일

- dashboard:
  - `grafana/aquila-bank-overview.json`
- alert rule:
  - `rules/aquila-bank-alerts.yml`

## Dashboard Baseline

`grafana/aquila-bank-overview.json`는 현재 `back/README.md`의 Prometheus metric을 바로 볼 수 있게 만든 overview dashboard입니다.

- 포함 패널:
  - outbox dispatch lag / failed count / stale sending
  - notification consumer lag / DLQ count
  - SSE total session, account/user/total trend, reject/drop trend
  - transaction query rate by `query_shape`
  - transaction 평균 latency by `query_shape`
- datasource 기준:
  - Grafana datasource variable 이름은 `datasource`입니다.
  - import 직후 Prometheus datasource를 한 번 선택하면 모든 panel이 그 값을 재사용합니다.
- query 기준:
  - transaction latency는 histogram percentile이 아니라 `sum(rate(..._sum)) / sum(rate(..._count))` 평균값을 사용합니다.
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
   - `aquila_transaction_query_latency_seconds_count`

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
- transaction baseline:
  - success query 평균 latency `> 750ms`

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

## Validation

로컬 baseline 자산 문법 확인은 아래 스크립트로 먼저 닫습니다.

```bash
bash tools/ops/validate-prometheus-assets.sh
```

- dashboard JSON은 `uid`, panel 개수, JSON syntax를 같이 확인합니다.
- alert rule YAML은 group/rule/expr 존재 여부와 YAML syntax를 같이 확인합니다.
- 실제 Prometheus 적용 전에는 여기에 더해 `promtool check rules`를 추가로 수행합니다.

## Threshold Tuning

- outbox/notification threshold는 현재 README와 actuator health 기본값을 기준으로 둔 값입니다.
- `AquilaNotificationSseSessionPressureHigh`의 `56`은 기본 `NOTIFICATION_SSE_MAX_TOTAL_SESSIONS=64`의 `87.5%` baseline입니다.
- transaction latency `750ms`는 query mix가 가벼운 환경을 전제로 한 출발값입니다. 실제 production에서는 `query_shape`, account volume, DB latency 분포를 보고 조정합니다.
- notification lag/DLQ alert는 `NOTIFICATION_INBOX_CONSUMER_OPS_ENABLED=true`가 아니면 metric 자체가 export되지 않을 수 있습니다.
- multi-instance SSE 합계는 Grafana/Prometheus 쿼리에서 인스턴스 합산으로 해석하고, 단일 instance alert는 node별 pressure 확인 용도로만 씁니다.
