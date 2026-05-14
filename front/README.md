# Aquila Bank Frontend

Next.js 기반 개인 프로젝트 웹뱅킹 프런트엔드입니다.

고객 화면은 로그인/계좌/거래/내부 이체/알림/고객 신청 접수 흐름을 보여주는 MVP입니다. 공과금, 오픈뱅킹, 인증서, 보안매체 화면은 실제 기관 또는 vendor 연동이 아니라 신청 접수와 mock/webhook boundary를 설명하는 데모 범위로 둡니다.

운영자 신청 처리와 mock callback 샘플은 [`../docs/customer-application-mock-webhook.md`](../docs/customer-application-mock-webhook.md)를 기준으로 확인합니다.

## Run

백엔드가 먼저 `http://localhost:8080`에서 실행되어야 로그인과 계좌/이체/신청 API가 동작합니다.

```bash
cp .env.example .env.local
yarn install
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 yarn dev
```

기본 개발 서버는 `http://localhost:3000`에서 실행됩니다.

여러 스레드가 동시에 프런트 서버를 띄워야 하면 포트를 분리하세요.

```bash
PORT=3001 yarn dev
```

```bash
PORT=3002 yarn dev
```

향후 E2E를 붙일 때도 같은 방식으로 스레드별 `PLAYWRIGHT_BASE_URL`을 서로 다른 포트에 맞춰 분리하는 것을 기본값으로 삼습니다.

## Environment

`NEXT_PUBLIC_API_BASE_URL`로 백엔드 API 주소를 주입합니다.

```bash
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

로그인 시 `Failed to fetch` 또는 백엔드 연결 실패 안내가 나오면 다음을 확인합니다.

1. 루트에서 `docker compose up -d postgres kafka`를 실행했는지 확인합니다.
2. 루트에서 `./back/gradlew -p back bootRun`을 실행했는지 확인합니다.
3. 프런트 dev shell 또는 `.env.local`에 `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`을 넣었는지 확인합니다.

프런트만 실행하면 화면 렌더링과 정적 contract 검증은 가능하지만 로그인 API 호출은 실패합니다.

`NEXT_PUBLIC_FEATURE_FLAGS`에는 쉼표 구분 flag key를 넣습니다.

```bash
NEXT_PUBLIC_FEATURE_FLAGS=ops-console-preview,transfer-reversal
```

미완성 기능은 이 값에 flag가 없으면 기본 비노출 상태로 유지합니다.
