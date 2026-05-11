import type { MenuSection } from "./types";

export const mainMenus: Array<{ id: MenuSection; label: string; group: string }> = [
  { id: "dashboard", label: "뱅킹홈", group: "개인뱅킹" },
  { id: "accounts", label: "조회", group: "계좌" },
  { id: "transfer", label: "이체", group: "이체" },
  { id: "transactions", label: "거래내역 조회", group: "조회" },
  { id: "notifications", label: "알림", group: "고객센터" },
  { id: "security", label: "인증센터", group: "인증" },
  { id: "securityHub", label: "보안센터/OTP", group: "보안센터" },
  { id: "supportCenter", label: "고객센터/사고신고", group: "고객센터" },
  { id: "enterpriseServices", label: "공과금/금융상품", group: "부가업무" },
];

export const quickMenus: Array<{ label: string; section: MenuSection }> = [
  { label: "계좌조회", section: "accounts" },
  { label: "즉시이체", section: "transfer" },
  { label: "거래내역", section: "transactions" },
  { label: "인증센터", section: "security" },
  { label: "보안센터", section: "securityHub" },
  { label: "사고신고", section: "supportCenter" },
  { label: "공과금", section: "enterpriseServices" },
  { label: "오픈뱅킹", section: "enterpriseServices" },
  { label: "알림함", section: "notifications" },
];

export const recentMenus: Array<{ label: string; section: MenuSection }> = [
  { label: "최근 거래내역 조회", section: "transactions" },
  { label: "출금가능금액 확인", section: "accounts" },
  { label: "이체한도 조회", section: "supportCenter" },
  { label: "OTP 이용관리", section: "securityHub" },
  { label: "예금상품 안내", section: "enterpriseServices" },
  { label: "알림 수신 설정", section: "notifications" },
];

export const noticeItems = [
  "OTP/보안카드 전체 번호 입력 요구 시 거래를 중단하세요.",
  "거래내역은 계좌와 기간 조건으로 조회합니다.",
  "공동인증서와 OTP 재발급은 인증센터에서 처리합니다.",
];

export const enterpriseServiceItems = [
  {
    title: "공과금",
    category: "납부",
    description: "지로, 지방세, 아파트관리비, 전기/통신요금",
    status: "접수 가능",
  },
  {
    title: "오픈뱅킹",
    category: "통합조회",
    description: "타행 계좌 연결, 통합조회, 연결 해지",
    status: "접수 가능",
  },
  {
    title: "예금상품",
    category: "상품",
    description: "입출금, 예금, 적금 상품 신청",
    status: "접수 가능",
  },
  {
    title: "대출",
    category: "여신",
    description: "한도조회, 상담 신청, 상환 조회",
    status: "접수 가능",
  },
  {
    title: "외환",
    category: "FX",
    description: "환율 조회, 외화예금, 해외송금 신청",
    status: "접수 가능",
  },
];

export const supportCenterItems = [
  {
    title: "고객센터",
    description: "상담, 자주 찾는 질문, 증명서 발급, 서비스 이용시간 메뉴를 제공합니다.",
    action: "상담/FAQ",
  },
  {
    title: "사고신고",
    description: "통장, 카드, 보안매체, 인증서 분실 신고 진입점을 한 화면에 모읍니다.",
    action: "긴급 신고",
  },
  {
    title: "이체한도",
    description: "1회/1일 이체한도와 보안매체별 한도 기준을 조회형으로 보여줍니다.",
    action: "한도 조회",
  },
  {
    title: "이용안내",
    description: "점검 시간, 수수료, 전자금융 약관, 장애 공지 링크를 정리합니다.",
    action: "안내",
  },
];

export const securityHubItems = [
  {
    title: "공동인증서",
    description: "인증서 발급, 갱신, 타기관 등록, 폐기 메뉴를 분리합니다.",
    status: "인증서 관리",
  },
  {
    title: "금융인증서",
    description: "클라우드 인증서 로그인, 발급, 재등록 흐름을 안내합니다.",
    status: "금융인증",
  },
  {
    title: "OTP",
    description: "OTP 등록, 오류횟수 초기화, 보안매체 교체 기준을 표시합니다.",
    status: "보안매체",
  },
  {
    title: "보안매체",
    description: "보안카드, 모바일 OTP, 생체 인증의 사용 가능 업무를 비교합니다.",
    status: "등급 안내",
  },
];
