# Redis Fit Evaluation Design

> Issue: `#140`
> Branch: `perf/redis-fit-evaluation`

## Goal

Redis를 지금 도입해야 하는지 여부를 현재 코드와 운영 제약 기준으로 판단한다. 도입이 필요하다면 첫 사용처를 하나로 좁히고, 기본 운영 경로는 기존 PostgreSQL + bounded in-memory 구조를 유지한다.

## Current State

- notification SSE fan-out은 PostgreSQL `LISTEN/NOTIFY` + local session registry로 동작한다.
- login throttling은 `LoginThrottleGuard`의 bounded in-memory window로 동작한다.
- Redis 런타임, 의존성, 설정, 운영 절차는 아직 없다.
- `t3.micro` 제약상 새 인프라는 분명한 병목 해결 지점이 있을 때만 도입해야 한다.

## Options

### Option A. Redis 미도입 유지

현재 구조를 그대로 유지한다. 운영 단순성과 비용은 가장 좋지만, login throttling은 multi-instance 환경에서 인스턴스별로 분산되어 전체 기준 일관성이 없다. SSE는 이미 PostgreSQL fan-out이 있어 Redis 부재가 즉시 병목으로 보이지 않는다.

### Option B. Redis를 login throttling 첫 사용처로 제한

Redis를 분산 카운터 저장소로만 도입한다. `security.login-throttling.store=memory|redis` 선택을 두고, 기본값은 `memory`로 유지한다. Redis 선택 시에만 IP/global throttle 카운터를 Redis에 저장하고 TTL로 자동 만료한다. 현재 구조에서 instance 간 공유 상태 필요성이 가장 명확한 지점만 보완할 수 있다.

### Option C. Redis를 SSE 분산 제어에 먼저 도입

Redis pub/sub 또는 shared session registry로 SSE 분산 상태를 옮긴다. 그러나 현재 SSE는 PostgreSQL `LISTEN/NOTIFY` 경로가 이미 있고, reconnect/replay/중복 해석까지 함께 복잡해진다. 첫 도입 범위로는 비용 대비 이득이 작다.

## Decision

권장안은 **Option B**다.

- Redis는 “플랫폼 공통 필수 인프라”가 아니라 **좁은 목적의 선택적 저장소**로 도입한다.
- 첫 사용처는 **login throttling** 하나로 제한한다.
- SSE는 **PostgreSQL `LISTEN/NOTIFY` 유지**가 기본 결론이다.

## Why This Decision

- login throttling은 현재 구현이 명확히 per-instance 경계에 묶여 있다.
- SSE는 이미 PostgreSQL 기반 분산 fan-out이 있어 Redis가 중복 투자에 가깝다.
- bounded in-memory throttling 기본값을 유지하면 Redis 장애나 미설정 상태에서도 현재 단일 인스턴스 운영 패턴이 깨지지 않는다.
- Redis 도입을 storage abstraction 수준으로 한정하면 이후 필요 시 다른 경로로 확장할 수 있지만, 지금 PR 범위는 작게 유지된다.

## Scope

### In Scope

- Redis 도입 필요성 문서화
- login throttling 저장소 추상화
- `memory` 기본 구현 유지
- `redis` 선택 구현 추가
- 최소 설정 추가
- 관련 테스트 및 문서 업데이트

### Out of Scope

- SSE transport/protocol 변경
- Redis pub/sub, cache, session store, distributed lock
- domain/persistence 계층 재설계
- notification fan-out 경로 변경

## Proposed Architecture

### 1. Store abstraction

`LoginThrottleGuard`가 직접 window 상태를 들고 있지 않고, throttle 상태 저장 책임을 별도 저장소 인터페이스로 분리한다.

- `MemoryLoginThrottleStore`
- `RedisLoginThrottleStore`

`LoginThrottleGuard`는 저장소 선택과 관계없이 다음 계약만 사용한다.

- 현재 IP/global window 검사
- 시도 기록
- retry-after 계산에 필요한 최소 정보 조회
- 테스트용 초기화

### 2. Default path stays memory

기본 설정은 `security.login-throttling.store=memory`로 둔다. Redis 설정이 없어도 기존 동작과 회귀 범위가 유지된다.

### 3. Redis path stays narrow

Redis 구현은 throttle counter만 다룬다.

- IP 키: `auth:login:throttle:ip:<hash>`
- Global 키: `auth:login:throttle:global`

각 키는 TTL을 window와 동일하게 둔다. 복잡한 Lua script나 광범위 자료구조는 도입하지 않는다. 1차 목표는 “instance 간 공유된 burst counter”이지 정교한 rate-limiting 플랫폼이 아니다.

### 4. Fail-fast configuration

저장소를 `redis`로 선택했는데 Redis 연결 필수 설정이 없으면 부팅 시 차단한다. 잘못된 운영 설정을 runtime fallback으로 숨기지 않는다.

## Config Direction

- `security.login-throttling.store=memory|redis`
- Redis 설정은 Spring Boot Redis 기본 설정을 활용한다.
- Redis 선택 시에만 관련 bean이 활성화된다.

## Error Handling

- `memory` 경로는 현재와 동일하게 bounded local state 사용
- `redis` 경로는 필수 설정 누락 시 부팅 fail-fast
- Redis 장애 시 이번 범위에서는 조용한 memory fallback을 두지 않는다
  - 이유: 운영자가 분산 throttle을 기대했는데 silently local throttle로 내려가면 보안/운영 해석이 틀어진다

## Testing

- 기존 login throttling 통합 테스트는 기본 `memory` 경로 회귀 검증으로 유지
- 저장소 선택 config 테스트 추가
- Redis 구현에는 Testcontainers Redis 통합 테스트 추가
- 전체 검증은 `./back/gradlew -p back check`

## Operational Notes

- Redis는 아직 필수 인프라가 아니다
- 단일 인스턴스 또는 작은 트래픽에서는 `memory` 기본값 유지가 권장안이다
- multi-instance login burst 일관성이 실제 요구로 확인되면 `redis` 선택을 켠다
- SSE는 Redis 도입 대상에서 제외하고 PostgreSQL fan-out 유지 근거를 문서에 남긴다

## Revisit Triggers

다음 조건이 확인되면 Redis 사용 범위를 재검토한다.

- login throttling이 multi-instance에서 실제 우회된다
- Nginx/edge throttling만으로 충분하지 않다
- PostgreSQL fan-out이 SSE scale-out 병목으로 관측된다
- 단일 throttle counter를 넘어 shared session/presence가 실운영 요구로 확정된다
