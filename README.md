# Aquila Bank

실시간 알림과 1억 건 규모의 거래 조회를 `t3.micro` 환경에서도 원활하게 처리하는 것을 목표로 하는 웹뱅킹 프로젝트입니다.

## Overview

- 목표:
  - 실시간 알림 지원
  - 1억 건 규모 거래 조회 지원
  - 제한된 인프라(`t3.micro`)에서도 운영 가능한 구조 지향
  - 로컬/배포 환경 모두 `PostgreSQL 18` 표준화
- 원칙:
  - 헥사고날 아키텍처 준수
  - 도메인과 인프라 경계 분리
  - 조회 성능, 정합성, 운영 단순성 우선

## Project Architecture Structure

```text
.
├── front
├── back
├── .github
└── compose.yml
```

- `front`: 고객/운영 웹 애플리케이션
- `back`: API, 도메인, 배치, 어댑터
- `.github`: 이슈/PR 템플릿과 협업 메타 설정
- `compose.yml`: 로컬 개발용 PostgreSQL 18 실행 기준

## Runtime Baseline

- database: `PostgreSQL 18`
- local environment: `PostgreSQL 18`
- deployed environment: `PostgreSQL 18`

## Backend Package Structure

```text
com.aquilabank
├── domain
├── global
└── standard
    └── util
```

- `domain`: 핵심 도메인, 유스케이스, 포트
- `global`: 설정, 보안, 웹, 영속성, 예외 처리 등 인프라/어댑터
- `standard/util`: 공통 기준과 최소 유틸리티

## Domain Structure

- `auth`: 로그인, 세션, MFA, 권한
- `account`: 고객 계좌, 잔액, 상태
- `transaction`: 대량 거래 조회, 검색, 상세 응답
- `ledger`: 거래 정합성, 원장 기록, 감사 추적
- `notification`: 실시간 이벤트, 읽음 처리, 재시도
- `ops`: 모니터링, 운영 배치, 장애 대응

## Architecture Principle

- domain은 framework, web, persistence 구현체에 의존하지 않습니다.
- global은 domain을 사용해 어댑터와 설정을 구성합니다.
- util에는 비즈니스 로직을 두지 않고, 공통 기술 보조 코드만 둡니다.
- 읽기 경로는 1억 건 조회와 `t3.micro` 운영 한계를 고려해 경량화와 분리를 우선합니다.
