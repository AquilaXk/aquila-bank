# Aquila Bank Frontend

Next.js 기반 웹뱅킹 프런트엔드 초기 골격입니다.

## Run

```bash
cp .env.example .env.local
yarn install
yarn dev
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

`NEXT_PUBLIC_FEATURE_FLAGS`에는 쉼표 구분 flag key를 넣습니다.

```bash
NEXT_PUBLIC_FEATURE_FLAGS=ops-console-preview,transfer-reversal
```

미완성 기능은 이 값에 flag가 없으면 기본 비노출 상태로 유지합니다.
