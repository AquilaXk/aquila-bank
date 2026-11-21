# Security Policy

## Supported Versions

보안 패치는 `main` 브랜치의 최신 릴리스에 반영하며 공식 GitHub Releases를 통해 배포합니다.

| Version | Supported |
| :--- | :--- |
| `1.1.x` (latest `main`) | Yes |

## Security Architecture & Invariants

Aquila Bank는 인증, 원장, 대량 조회, 실시간 알림 전 구간에서 **Fail-Closed** 보안 불변식을 강제합니다.

1. **HttpOnly 세션 및 다중 인증(MFA)**:
   - 인증 세션은 `HttpOnly` 쿠키로만 관리하며 브라우저 `localStorage`·`sessionStorage` 토큰 저장을 금지합니다.
   - TOTP 2단계 인증, 일회용 복구코드, 세션 단건·전체 강제 해지, 상수 시간 토큰·해시 검증을 적용합니다.
2. **복식 원장 및 트랜잭션 무결성**:
   - 잔액 스냅샷, 복식 원장 엔트리, 감사 추적을 단일 트랜잭션 경계로 묶고 `account_id ASC` 오름차순 비관적 락과 `Math.addExact`로 데드락·초과 출금·오버플로를 차단합니다.
3. **경계 제한 대량 조회 (Bounded Reads)**:
   - 거래 조회 커서는 HMAC 서명을 상수 시간으로 검증하며, 페이지 상한·명시적 쿼리 타임아웃(`statement_timeout`)·Admission Control을 초과하는 요청을 즉시 거부합니다.
4. **무침묵 폴백 (Zero Silent Fallbacks)**:
   - 인가 실패, 미검증 MFA 상태, 스로틀 저장소 장애, 유효하지 않은 입력은 추정치나 임시 폴백 없이 즉시 거부합니다.

## Reporting a Vulnerability

보안 취약점은 공개 이슈(Public Issue)로 등록하지 말고 GitHub Security Advisories 비공개 신고 양식을 이용해 주세요:

- Private report URL: https://github.com/AquilaXk/aquila-bank/security/advisories/new
- 공개 이슈에 취약점 상세 내용, 재현 페이로드, 인증 쿠키/토큰, 내부 환경 정보를 게시하지 마세요.
