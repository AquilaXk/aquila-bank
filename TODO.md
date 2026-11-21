# TODO

## 이체·보안 즉시 수정 (P0)
- [ ] 이체 사전 검증 수신자 표시에서 고객 응답 `displayName` 마스킹(`null`) 시 `undefined` 문자열이 출력되던 오류 수정(`transfer-section.tsx`, `use-customer-banking.ts`)
- [ ] TOTP 미등록(`otpRequired === false`) 계정의 즉시이체 진행이 6자리 OTP 검사에서 막히던 단계 전이 수정 및 이체 취소(`reversal`) 폼에 선택 TOTP 입력 연동
- [ ] 이체 사전 검증 차단 사유(`INSUFFICIENT_BALANCE`, `CURRENCY_MISMATCH`, `TARGET_ACCOUNT_NOT_ACTIVE`)의 한국어 안내 문구 매핑 정정
- [ ] 백엔드에 구현된 이체한도 변경 신청(`TRANSFER_LIMIT_CHANGE`) 전용 제출 폼을 고객센터 화면에 연결(`support-center-section.tsx`)
- [ ] TOTP 등록 화면에 순수 SVG 기반 QR 코드 렌더링 추가(외부 네트워크 전송 없이 클라이언트 생성)

## UI·UX 디자인 및 상태 아키텍처 개선 (P1)
- [ ] `globals.css`와 `customer-banking.css`에 중복 선언된 `:root` 컬러·시맨틱 토큰을 단일 정본으로 통합
- [ ] 9개 고객 뱅킹 섹션을 URL 경로/쿼리 상태와 동기화해 새로고침·뒤로가기·딥링크를 지원하고 `use-customer-banking.ts`(1,709줄) 도메인 훅 분리
- [ ] 대시보드에 통화별 분리 집계 순자산 요약, 월간 입출금 현금흐름 시계열 차트, 최근 거래 내역 피드 추가 및 정적 최근 이용 메뉴를 실제 이용 이력과 연결
- [ ] 전역 헤더 알림 벨·미확인 배지·플로팅 토스트 추가 및 로그인 후 SSE 자동 연결·지수 백오프 재연결 복구 적용
- [ ] 거래내역 목록에 상대방 표시·적요 컬럼, 이전 페이지 커서 스택 탐색, 조건별 CSV/인쇄용 명세서 내보내기 추가
- [ ] 모달 커맨드 팔레트(`⌘K`), 모바일 하단 내비게이션 탭 바, 스켈레톤 로딩 및 Next.js `error.tsx`/`not-found.tsx` 경계 보강

## 핵심 뱅킹 도메인 확장 (P2)
- [ ] 자주 쓰는/최근 입금계좌(수취인 주소록) 저장 및 빠른 재이체 도메인·화면 추가
- [ ] 예약이체·정기 자동이체(2단계 보류금 홀드/실행/취소) 및 목표 저축 금고(계좌 내 목적별 분리 적립) 도메인 검토
- [ ] 거래별 사용자 메모·카테고리·자동 분류 규칙 엔진 및 WebAuthn 패스키 인증 도입 검토

## 인프라·CI 운영 (Ops)
- [ ] OCI A1 self-hosted 러너 오프라인 상태에서 `Staging Deploy` 워크플로가 24시간 큐 대기 후 취소되는 문제를 러너 가용성 선점검 또는 수동 승인 게이트로 개선할지 결정(QA 승인 필요)
