# Aquila Bank Backend

Spring Boot 4 기반 백엔드 애플리케이션입니다.

## Goal

- 실시간 알림과 1억 건 규모의 거래 조회를 `t3.micro` 환경에서도 원활하게 처리하는 구조를 목표로 합니다.
- 로컬/배포 환경 모두 `PostgreSQL 18`을 표준 DB 버전으로 사용합니다.

## Package Structure

```text
com.aquilabank
├── domain
├── global
└── standard
    └── util
```

- `domain`: 도메인 엔티티, 유스케이스, 포트, 도메인 서비스
- `global`: 설정, 보안, 웹 어댑터, DB 어댑터, 예외 처리
- `standard/util`: 공통 규약과 최소 유틸리티

## Architecture

- 헥사고날 아키텍처 준수를 명시적 원칙으로 삼습니다.
- domain은 Spring MVC, JPA, 외부 API SDK에 직접 의존하지 않습니다.
- inbound/outbound adapter, config, filter, interceptor, repository 구현체는 `global`에 둡니다.
- 공통 helper는 `standard/util`에 두되, 도메인 규칙이나 유스케이스 판단은 넣지 않습니다.

## Stack

- Java 21
- Gradle Kotlin DSL
- Spring Boot 4
- PostgreSQL 18
- Flyway
- Spring JDBC / HikariCP
- Spring Boot Actuator

## Run

```bash
./gradlew bootRun
```

## Local Database

```bash
cp ../.env.example ../.env
cd ..
docker compose up -d postgres
```

로컬 DB는 루트 [compose.yml](/Users/aquila/Custom/GitProjects/aquila-bank/compose.yml)의 `postgres:18` 컨테이너를 기준으로 사용합니다.

백엔드는 [back/.env.example](/Users/aquila/Custom/GitProjects/aquila-bank/back/.env.example)를 복사한 뒤 실행합니다.

```bash
cp back/.env.example back/.env
set -a
source back/.env
set +a
./back/gradlew -p back bootRun
```

- 애플리케이션 시작 시 `back/src/main/resources/db/migration`의 Flyway 마이그레이션이 자동 적용됩니다.
- 기본값은 `t3.micro`를 전제로 작은 커넥션 풀과 짧은 DB 타임아웃을 사용합니다.
- 운영 기본 인증 방식은 bearer JWT 입니다.
- `dev`/`test` 프로필에서는 필요 시 `X-Account-Id` 헤더 fallback을 사용할 수 있습니다.

## Test

```bash
./gradlew test
```

## Java Style

- Google Java Style 기반 포맷 검사는 Spotless로 강제합니다.
- 검사: `./gradlew check`
- 자동 정리: `./gradlew spotlessApply`

## Local Concurrency

- 같은 워크트리에서 여러 스레드가 동시에 `test`/`check`를 돌리면 `back/build/test-results`를 공유해 충돌할 수 있습니다.
- 로컬 검증은 `tools/test/with-resource-lock.sh back-gradle-check ...`로 직렬화하는 것을 기본값으로 사용합니다.
