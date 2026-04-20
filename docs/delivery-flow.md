# Delivery Flow

## Branch Strategy

- feature, fix, ci 작업은 `main`에서 짧게 분기한 `feature/*`, `fix/*`, `ci/*` 브랜치에서 진행합니다.
- 모든 변경은 GitHub issue를 먼저 만들고 `main` 대상 PR로만 병합합니다.
- 보호 브랜치 직접 push는 금지하고, 리뷰 가능한 한 가지 목적 단위로 PR을 유지합니다.

## GitHub Connector Fallback

- GitHub issue/PR write에서 `403 Resource not accessible by integration`가 나오면 flaky 재시도보다 connector 권한 차이를 먼저 의심합니다.
- 이 저장소에서는 같은 세션에서 write 성공이 확인된 connector를 표준 경로로 계속 사용합니다.
- 표준 경로가 아닌 connector에서 403이 나면 blind retry 없이 즉시 표준 write 경로로 fallback 합니다.
- 표준 write 경로도 실패하면 작업을 멈추고 권한 또는 사용할 connector를 확인합니다.

## CI Gate

- backend/frontend CI는 `main` 대상 PR에서 실행합니다.
- workflow 수정만 있는 PR도 gate가 돌도록 `.github/workflows/**` 변경을 CI path에 포함합니다.

## Main CI

- `main`에 merge된 SHA는 `Main CI` workflow가 backend/frontend check를 다시 실행합니다.
- 아직 자동 배포는 연결하지 않습니다.
- 배포가 필요해지면 별도 workflow 또는 외부 CD 연동을 추가합니다.

## Feature Flag

- 미완성 기능은 장기 `develop` 브랜치 대신 feature flag로 숨깁니다.
- 프런트 기준선은 `NEXT_PUBLIC_FEATURE_FLAGS` 환경변수로 관리합니다.
- 값은 쉼표 구분 flag key 목록입니다.
- 예시: `NEXT_PUBLIC_FEATURE_FLAGS=ops-console-preview,transfer-reversal`
