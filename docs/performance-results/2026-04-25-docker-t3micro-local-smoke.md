# 2026-04-25 Docker t3.micro Local Smoke Result

## 목적

- 로컬 Docker cgroup 제한에서 대용량 트래픽 대응 smoke와 거래 조회 regression gate를 실행했다.
- 이번 결과는 `t3.micro` 근사 로컬 회귀 검증이다.
- 실제 1억 건 실데이터 HTTP replay, k6, Prometheus, Grafana 기반 검증은 아직 이 저장소의 로컬 실행 환경으로 구성되어 있지 않아 실행하지 않았다.

## 테스트 환경

- 실행 시각: 2026-04-25 01:14:24 KST
- 실행 위치: `/Users/aquila/Custom/GitProjects/aquila-bank`
- Git 기준: `main`
- Docker image: `eclipse-temurin:21-jdk`
- Docker cgroup budget:
  - CPU: `--cpus 2`
  - memory: `--memory 1024m`
  - memory swap: `--memory-swap 1024m`
  - pids: `--pids-limit 384`
- JVM/Gradle budget:
  - `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70 -XX:InitialRAMPercentage=40`
  - `GRADLE_OPTS=-Dorg.gradle.jvmargs=-Xmx512m -Dorg.gradle.daemon=false -Dorg.gradle.parallel=false`

## 실행 결과

### 대용량 트래픽 mixed workload

명령:

```bash
tools/test/run-docker-t3micro-capacity-smoke.sh
```

검증 범위:

- transaction query concurrency
- transfer write / outbox side effect
- notification fanout
- SSE stream / duplicate / backpressure guard

결과:

- 상태: 통과
- Docker 제한: `2 vCPU / 1GiB / swap 1GiB / pids 384`
- Gradle 결과: `BUILD SUCCESSFUL in 6s`
- 실행 task: `cleanTest`, `test`

### 거래 조회 regression gate

명령:

```bash
tools/test/with-resource-lock.sh back-gradle-docker-t3micro-testclasses ./back/gradlew -p back testClasses
docker run --rm \
  --name aquila-bank-docker-t3micro-transaction-plan \
  --cpus 2 \
  --memory 1024m \
  --memory-swap 1024m \
  --pids-limit 384 \
  --workdir /workspace \
  --user 501:20 \
  -e HOME=/tmp \
  -e GRADLE_USER_HOME=/tmp/.gradle \
  -e JAVA_TOOL_OPTIONS='-XX:MaxRAMPercentage=70 -XX:InitialRAMPercentage=40' \
  -e GRADLE_OPTS='-Dorg.gradle.jvmargs=-Xmx512m -Dorg.gradle.daemon=false -Dorg.gradle.parallel=false' \
  -v /Users/aquila/Custom/GitProjects/aquila-bank:/workspace \
  -v /Users/aquila/.gradle:/tmp/.gradle \
  eclipse-temurin:21-jdk \
  bash -lc 'tools/test/run-transaction-query-plan-regression-gate.sh'
```

검증 범위:

- baseline hot account query
- long-history partition-fit fixture
- account-scoped keyset plan
- `Seq Scan` / `Sort` 회귀 방지
- statement timeout / query timeout / request timeout
- transaction concurrency SLO

결과:

- 상태: 통과
- Docker 제한: `2 vCPU / 1GiB / swap 1GiB / pids 384`
- Gradle 결과: `BUILD SUCCESSFUL in 5s`

## 해석

- 현재 저장소에 있는 Docker t3.micro 근사 smoke와 거래 조회 regression gate는 로컬 cgroup 제한 안에서 통과했다.
- 이 결과는 “1억 건 실데이터를 로컬 PostgreSQL에 적재한 뒤 HTTP로 조회했다”는 의미가 아니다.
- 현재 1억 건 실데이터 replay는 `tools/ops/transaction-read-model-staging-replay.sh` 기준으로 staging/RDS 환경의 `reltuples` estimate와 hot/cold account를 요구한다.
- 로컬에서 k6, Prometheus, Grafana를 함께 띄워 HTTP p95와 dashboard artifact를 남기는 환경은 아직 없다.

## 후속 작업

- k6 + Prometheus + Grafana compose profile 추가
- backend app container 또는 t3.micro 제한 host runner 고정
- 1억 건 로컬 데이터 적재 또는 staging replay와 동일한 report contract 결정
- `docs/performance-results/YYYY-MM-DD-*.md` 형식으로 모든 성능 실행 결과 자동 기록
- k6 summary JSON, Prometheus snapshot query, Grafana dashboard 링크 또는 export artifact를 report에 연결
