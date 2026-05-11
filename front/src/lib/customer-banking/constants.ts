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

export const recommendedKeywords = [
  "인증서",
  "OTP",
  "이체한도",
  "공과금",
  "환율",
];

export const serviceMapGroups: Array<{
  title: string;
  items: Array<{ label: string; section: MenuSection }>;
}> = [
  {
    title: "조회",
    items: [
      { label: "계좌조회", section: "accounts" },
      { label: "거래내역 조회", section: "transactions" },
      { label: "출금가능금액", section: "accounts" },
      { label: "오픈뱅킹 조회", section: "enterpriseServices" },
    ],
  },
  {
    title: "이체",
    items: [
      { label: "즉시이체", section: "transfer" },
      { label: "이체결과 조회", section: "transactions" },
      { label: "자동이체", section: "transfer" },
      { label: "이체한도 조회", section: "supportCenter" },
    ],
  },
  {
    title: "공과금",
    items: [
      { label: "공과금 납부", section: "enterpriseServices" },
      { label: "지방세", section: "enterpriseServices" },
      { label: "생활요금", section: "enterpriseServices" },
      { label: "납부내역", section: "enterpriseServices" },
    ],
  },
  {
    title: "금융상품",
    items: [
      { label: "예금상품", section: "enterpriseServices" },
      { label: "대출", section: "enterpriseServices" },
      { label: "외환", section: "enterpriseServices" },
      { label: "오픈뱅킹", section: "enterpriseServices" },
    ],
  },
  {
    title: "인증/보안",
    items: [
      { label: "인증센터", section: "security" },
      { label: "공동인증서", section: "securityHub" },
      { label: "금융인증서", section: "securityHub" },
      { label: "OTP", section: "securityHub" },
    ],
  },
  {
    title: "고객센터",
    items: [
      { label: "고객센터", section: "supportCenter" },
      { label: "사고신고", section: "supportCenter" },
      { label: "증명서 발급", section: "supportCenter" },
      { label: "공지사항", section: "dashboard" },
    ],
  },
];

export const favoriteServiceItems: Array<{
  label: string;
  section: MenuSection;
  group: string;
}> = [
  { label: "계좌조회", section: "accounts", group: "조회" },
  { label: "즉시이체", section: "transfer", group: "이체" },
  { label: "거래내역 조회", section: "transactions", group: "조회" },
  { label: "공과금 납부", section: "enterpriseServices", group: "공과금" },
  { label: "인증서 관리", section: "security", group: "인증" },
  { label: "사고신고", section: "supportCenter", group: "고객센터" },
];

export const serviceHourItems = [
  { task: "조회", time: "00:30~23:30", status: "정상" },
  { task: "이체", time: "00:30~23:30", status: "정상" },
  { task: "공과금", time: "07:00~23:30", status: "정상" },
  { task: "고객센터", time: "09:00~18:00", status: "상담" },
];

export const bankingNewsItems = [
  "전자금융 이용시간 일부 변경 안내",
  "비대면 계좌개설 안심차단 서비스 안내",
  "OTP 오류횟수 초기화 업무 안내",
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
    description: "상담, 자주 찾는 질문, 증명서 발급, 서비스 이용시간",
    action: "상담/FAQ",
  },
  {
    title: "사고신고",
    description: "통장, 카드, 보안매체, 인증서 분실 신고",
    action: "긴급 신고",
  },
  {
    title: "이체한도",
    description: "1회/1일 이체한도와 보안매체별 한도",
    action: "한도 조회",
  },
  {
    title: "이용안내",
    description: "점검 시간, 수수료, 전자금융 약관, 장애 공지",
    action: "안내",
  },
];

export const securityHubItems = [
  {
    title: "공동인증서",
    description: "인증서 발급, 갱신, 타기관 등록, 폐기",
    status: "인증서 관리",
  },
  {
    title: "금융인증서",
    description: "클라우드 인증서 로그인, 발급, 재등록",
    status: "금융인증",
  },
  {
    title: "OTP",
    description: "OTP 등록, 오류횟수 초기화, 보안매체 교체",
    status: "보안매체",
  },
  {
    title: "보안매체",
    description: "보안카드, 모바일 OTP, 생체 인증 적용 업무",
    status: "등급 안내",
  },
];
