import type { MenuSection } from "./types";

export const mainMenus: Array<{ id: MenuSection; label: string; group: string }> = [
  { id: "dashboard", label: "뱅킹홈", group: "개인뱅킹" },
  { id: "accounts", label: "조회", group: "계좌" },
  { id: "transfer", label: "이체", group: "이체" },
  { id: "transactions", label: "거래내역 조회", group: "조회" },
  { id: "notifications", label: "알림", group: "고객센터" },
  { id: "security", label: "인증/세션 관리", group: "뱅킹관리" },
];

export const quickMenus = ["계좌조회", "즉시이체", "거래내역", "인증센터", "알림함"];
