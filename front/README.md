# Aquila Bank Frontend

Next.js 14와 React 18 기반의 웹뱅킹 프런트엔드입니다.

고객 뱅킹 화면은 로그인, 전계좌 조회, 대량 거래 내역 조회, 내부 이체, 실시간 알림, 고객 신청 접수 흐름을 다루며, 운영 콘솔은 Outbox/DLQ, 멱등성 락, 원장 스냅샷 드리프트, 감사 로그 API를 실시간 조회하고 수동 복구를 실행합니다. 공과금, 오픈뱅킹, 인증서, 보안매체 메뉴는 신청 접수부터 운영자 검토·승인·콜백 실행까지의 상태 전이 경계를 다룹니다. 상세 규격은 [`../docs/customer-application-mock-webhook.md`](../docs/customer-application-mock-webhook.md)에서 확인할 수 있습니다.

## Design System

시스템 라이트/다크 환경에 자동 대응하는 적응형 컬러 스킴(`color-scheme: light dark`, `light-dark()`), 슬레이트 뉴트럴 서피스(`#F8F9FC` / `#121317`), 시그니처 블루 포인트(`#3279F9`), `1px` 헤어라인 보더, 고정폭 숫자(`tabular-nums`)를 기본 디자인 토큰으로 사용합니다.

## Run

백엔드가 먼저 `http://localhost:8080`에서 실행되어야 로그인과 계좌/이체/신청 API가 동작합니다.

```bash
cp .env.example .env.local
yarn install
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 yarn dev
```

기본 개발 서버는 `http://localhost:3000`에서 실행됩니다. 여러 프로세스가 동시에 프런트 서버를 띄울 때는 포트를 분리합니다.

```bash
PORT=3001 yarn dev
```

```bash
PORT=3002 yarn dev
```

Playwright E2E 실행 시에도 프로세스별 `PLAYWRIGHT_BASE_URL`을 서로 다른 포트에 맞춰 분리합니다.

## Verification

```bash
yarn lint
yarn test:api-parity
yarn test:ui-contract
yarn build
```

## Environment

`NEXT_PUBLIC_API_BASE_URL`로 백엔드 API 주소를 주입합니다.

```bash
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

로그인 시 `Failed to fetch` 또는 백엔드 연결 실패 안내가 나오면 다음을 확인합니다.

1. 루트에서 `docker compose up -d postgres kafka`를 실행했는지 확인합니다.
2. 루트에서 `./back/gradlew -p back bootRun`을 실행했는지 확인합니다.
3. 프런트 dev shell 또는 `.env.local`에 `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`을 설정했는지 확인합니다.

백엔드 없이 프런트만 실행하면 정적 화면 렌더링과 UI 계약 검증은 가능하며, 로그인 및 데이터 조회 API 호출 시 연결 실패 안내가 표시됩니다.

`NEXT_PUBLIC_FEATURE_FLAGS`에는 쉼표로 구분된 플래그 키를 지정합니다.

```bash
NEXT_PUBLIC_FEATURE_FLAGS=ops-console-preview,transfer-reversal
```

미완성 기능은 해당 플래그가 지정되지 않으면 기본 비노출 상태를 유지합니다.
