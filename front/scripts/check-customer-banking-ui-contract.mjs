import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const root = new URL("..", import.meta.url).pathname;

function read(path) {
  return readFileSync(join(root, path), "utf8");
}

function readOptional(path) {
  const filePath = join(root, path);
  return existsSync(filePath) ? readFileSync(filePath, "utf8") : "";
}

const files = {
  page: [
    read("src/app/page.tsx"),
    read("src/components/customer-banking/layout.tsx"),
  ].join("\n"),
  layout: read("src/app/layout.tsx"),
  manifest: readOptional("src/app/manifest.ts"),
  constants: read("src/lib/customer-banking/constants.ts"),
  dashboard: read("src/components/customer-banking/sections/dashboard-section.tsx"),
  accounts: read("src/components/customer-banking/sections/accounts-section.tsx"),
  transfer: read("src/components/customer-banking/sections/transfer-section.tsx"),
  transactions: read("src/components/customer-banking/sections/transactions-section.tsx"),
  notifications: read("src/components/customer-banking/sections/notifications-section.tsx"),
  security: read("src/components/customer-banking/sections/security-section.tsx"),
  securityHub: read("src/components/customer-banking/sections/security-hub-section.tsx"),
  support: read("src/components/customer-banking/sections/support-center-section.tsx"),
  enterprise: read("src/components/customer-banking/sections/enterprise-services-section.tsx"),
  styles: read("src/styles/customer-banking.css"),
};

const customerVisibleContent = [
  files.page,
  files.constants,
  files.dashboard,
  files.accounts,
  files.transfer,
  files.transactions,
  files.notifications,
  files.security,
  files.securityHub,
  files.support,
  files.enterprise,
].join("\n");

const checks = [
  ["top personal tab", files.page, "개인"],
  ["top business tab", files.page, "기업"],
  ["top certificate center", files.page, "인증센터"],
  ["top customer center", files.page, "고객센터"],
  ["integrated search label", files.page, "통합검색"],
  ["service map command", files.page, "전체서비스"],
  ["security status bar", files.page, "보안등급"],
  ["left business menu", files.page, "개인뱅킹 메뉴"],
  ["right login status", files.page, "로그인 상태"],
  ["right recent menu", files.page, "최근 이용 메뉴"],
  ["right notices", files.page, "공지사항"],
  ["account 업무 menu", files.constants, "계좌조회"],
  ["transfer 업무 menu", files.constants, "즉시이체"],
  ["transactions 업무 menu", files.constants, "거래내역 조회"],
  ["notifications 업무 menu", files.constants, "알림함"],
  ["responsive shell media query", files.styles, "@media (max-width: 760px)"],
  ["metadata public title", files.layout, "Aquila Bank 개인 인터넷뱅킹"],
  ["session expired notice", files.page, "권한 만료"],
  ["unauth work gate heading", files.page, "로그인이 필요한 업무"],
  ["unauth certificate login", files.page, "공동인증서 로그인"],
  ["unauth financial certificate login", files.page, "금융인증서 로그인"],
  ["unauth id login", files.page, "아이디 로그인"],
  ["unauth gate helper", files.page, "renderLoginRequiredWork"],
  ["session required class", files.styles, ".session-required"],
  ["session required methods style", files.styles, ".login-method-grid"],
  ["transfer confirm step", files.transfer, "이체 확인"],
  ["transfer complete state", files.transfer, "완료"],
  ["transfer failed state", files.transfer, "실패"],
  ["transfer stepper style", files.styles, ".stepper"],
  ["transaction current filters", files.transactions, "현재 조건"],
  ["transaction next lookup label", files.transactions, "다음 조회"],
  ["transaction next page label", files.transactions, "다음 페이지"],
  ["notification live connection label", files.notifications, "실시간 연결 상태"],
  ["notification bulk action label", files.notifications, "선택 일괄 처리"],
  ["filter summary style", files.styles, ".filter-summary"],
  ["skip link", files.page, "본문 바로가기"],
  ["work area id", files.page, "bank-work-area"],
  ["active menu aria current", files.page, "aria-current"],
  ["open graph metadata", files.layout, "openGraph"],
  ["viewport theme color", files.layout, "themeColor"],
  ["manifest file", files.manifest, "Aquila Bank 개인 인터넷뱅킹"],
  ["manifest public url", files.manifest, "https://bank.aquilaxk.site"],
  ["mobile small breakpoint", files.styles, "@media (max-width: 480px)"],
  ["button overflow guard", files.styles, "overflow-wrap: anywhere"],
  ["bank shell status class", files.styles, ".bank-service-strip"],
  ["bank work tabs class", files.styles, ".work-tabs"],
  ["bank notice strip class", files.styles, ".bank-notice-strip"],
  ["bank dense table class", files.styles, ".bank-table"],
  ["bank right rail security notice", files.page, "보안알림"],
  ["bank service map data", files.constants, "serviceMapGroups"],
  ["bank recommended keywords data", files.constants, "recommendedKeywords"],
  ["bank dashboard favorite services data", files.constants, "favoriteServiceItems"],
  ["bank dashboard service hours data", files.constants, "serviceHourItems"],
  ["bank dashboard news data", files.constants, "bankingNewsItems"],
  ["bank service map panel", files.page, "전체서비스 메뉴"],
  ["bank service map expanded state", files.page, "aria-expanded"],
  ["bank service map style hook", files.page, "service-map-panel"],
  ["bank recommended keyword label", files.page, "추천검색어"],
  ["bank recommended keyword state", files.page, "setSearchQuery"],
  ["bank dashboard favorite services", files.dashboard, "자주찾는서비스"],
  ["bank dashboard service hours", files.dashboard, "서비스 이용시간"],
  ["bank dashboard news", files.dashboard, "새소식"],
  ["bank dashboard service list style", files.styles, ".bank-service-list"],
  ["bank service map panel style", files.styles, ".service-map-panel"],
  ["bank keyword list style", files.styles, ".keyword-list"],
  ["bank news list style", files.styles, ".bank-news-list"],
  ["bank service hours table style", files.styles, ".service-hours-table"],
  ["dashboard operating hours", files.dashboard, "이용시간"],
  ["dashboard security level", files.dashboard, "보안등급"],
  ["account available amount label", files.accounts, "출금가능금액"],
  ["account visual summary heading", files.accounts, "계좌 업무 요약"],
  ["account inquiry command panel", files.accounts, "조회 업무"],
  ["account account status panel", files.accounts, "계좌 상태"],
  ["account visual summary style", files.styles, ".account-summary-panel"],
  ["account command style", files.styles, ".work-command-panel"],
  ["transfer receiver label", files.transfer, "받는 분"],
  ["transfer visual process heading", files.transfer, "이체 절차"],
  ["transfer confirmation heading", files.transfer, "이체정보 확인"],
  ["transfer receipt heading", files.transfer, "이체 완료증"],
  ["transfer process style", files.styles, ".transfer-process-grid"],
  ["transfer receipt style", files.styles, ".transfer-receipt-panel"],
  ["transfer security verification label", files.transfer, "보안 확인"],
  ["transaction condition panel", files.transactions, "거래 조건"],
  ["transaction ledger panel", files.transactions, "입출금 내역"],
  ["transaction detail summary", files.transactions, "거래 상세정보"],
  ["transaction visual style", files.styles, ".transaction-work-grid"],
  ["auth center eyebrow", files.security, "인증센터"],
  ["auth center heading", files.security, "로그인 및 보안관리"],
  ["auth method heading", files.security, "로그인 방식"],
  ["auth security summary", files.security, "보안관리"],
  ["auth session dashboard", files.security, "접속관리"],
  ["auth method style", files.styles, ".auth-method-grid"],
  ["auth security dashboard style", files.styles, ".security-dashboard"],
  ["security center login status", files.security, "로그인 상태"],
  ["security hub certificate tab", files.securityHub, "공동인증서"],
  ["support incident report label", files.support, "사고신고 접수"],
  ["support certificate issue label", files.support, "증명서 발급"],
  ["support transfer confirmation label", files.support, "이체확인증"],
  ["enterprise bill payment application label", files.enterprise, "공과금 납부"],
  ["enterprise open banking application label", files.enterprise, "오픈뱅킹 연결"],
  ["enterprise foreign exchange application label", files.enterprise, "외환 신청"],
  ["productized work status label", customerVisibleContent, "업무현황"],
  ["productized inquiry result label", customerVisibleContent, "조회 결과"],
  ["productized processing result label", customerVisibleContent, "처리 결과"],
  ["productized certificate management label", customerVisibleContent, "인증서 관리"],
  ["productized receive settings label", customerVisibleContent, "수신 설정"],
  ["productized final confirmation label", customerVisibleContent, "최종 확인"],
  ["productized work summary style", files.styles, ".work-summary-strip"],
  ["productized process panel style", files.styles, ".process-panel"],
  ["gothic page token", files.styles, "--gothic-page"],
  ["gothic surface token", files.styles, "--gothic-surface"],
  ["gothic brass token", files.styles, "--gothic-brass"],
  ["gothic parchment token", files.styles, "--gothic-parchment"],
  ["gothic danger token", files.styles, "--gothic-danger"],
  ["gothic shell class", files.styles, ".gothic-banking-shell"],
  ["gothic work panel class", files.styles, ".gothic-work-panel"],
  ["gothic state strip class", files.styles, ".gothic-state-strip"],
  ["gothic bank table class", files.styles, ".gothic-bank-table"],
];

const forbidden = [
  ["customer visible read-only", customerVisibleContent, "read-only"],
  ["customer visible backend preview", customerVisibleContent, "backend preview"],
  ["customer visible backend", customerVisibleContent, "backend"],
  ["customer visible bounded query", customerVisibleContent, "bounded query"],
  ["customer visible idempotency key", customerVisibleContent, "Idempotency-Key"],
  ["customer visible cursor pagination", customerVisibleContent, "cursor pagination"],
  ["customer visible response shape", customerVisibleContent, "응답형태"],
  ["customer visible transfer minor label", customerVisibleContent, "금액 minor"],
  ["customer visible reversal minor label", customerVisibleContent, "취소금액 minor"],
  ["customer visible min minor label", customerVisibleContent, "최소금액 minor"],
  ["customer visible max minor label", customerVisibleContent, "최대금액 minor"],
  ["customer visible daily remaining field", customerVisibleContent, "dailyRemainingMinor /"],
  ["customer visible explanatory provide", customerVisibleContent, "제공"],
  ["customer visible explanatory screen structure", customerVisibleContent, "화면 구조"],
  ["customer visible explanatory flow", customerVisibleContent, "흐름"],
  ["customer visible bank-like explanation", customerVisibleContent, "처럼"],
  ["customer visible actual implementation explanation", customerVisibleContent, "실제"],
  ["customer visible customer explanation", customerVisibleContent, "고객이"],
];

const missing = checks.filter(([, content, expected]) => !content.includes(expected));
const present = forbidden.filter(([, content, expected]) =>
  content.toLowerCase().includes(expected.toLowerCase()),
);

if (missing.length > 0 || present.length > 0) {
  console.error("[customer-banking-ui-contract] bank UX contract violations:");
  for (const [name, , expected] of missing) {
    console.error(`- missing ${name}: ${expected}`);
  }
  for (const [name, , expected] of present) {
    console.error(`- forbidden ${name}: ${expected}`);
  }
  process.exit(1);
}

console.log("[customer-banking-ui-contract] passed");
