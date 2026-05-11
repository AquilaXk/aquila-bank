import type { MenuSection } from "./types";

export const mainMenus: Array<{ id: MenuSection; label: string; group: string }> = [
  { id: "dashboard", label: "뱅킹홈", group: "개인뱅킹" },
  { id: "accounts", label: "조회", group: "계좌" },
  { id: "transfer", label: "이체", group: "이체" },
  { id: "transactions", label: "거래내역 조회", group: "조회" },
  { id: "notifications", label: "알림", group: "고객센터" },
  { id: "security", label: "인증/세션 관리", group: "뱅킹관리" },
];

export const quickMenus: Array<{ label: string; section: MenuSection }> = [
  { label: "계좌조회", section: "accounts" },
  { label: "즉시이체", section: "transfer" },
  { label: "거래내역", section: "transactions" },
  { label: "인증센터", section: "security" },
  { label: "알림함", section: "notifications" },
];

export const recentMenus: Array<{ label: string; section: MenuSection }> = [
  { label: "최근 거래내역 조회", section: "transactions" },
  { label: "출금가능금액 확인", section: "accounts" },
  { label: "알림 수신 설정", section: "notifications" },
];

export const noticeItems = [
  "보안카드 전체 번호 입력 요구 시 즉시 거래를 중단하세요.",
  "대량 거래 조회는 계좌, 기간, cursor 조건으로 제한됩니다.",
  "공동인증서 및 OTP 재발급은 인증센터에서 진행합니다.",
];
