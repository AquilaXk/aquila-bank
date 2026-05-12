import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const root = new URL("..", import.meta.url).pathname;

function read(path) {
  const filePath = join(root, path);
  return existsSync(filePath) ? readFileSync(filePath, "utf8") : "";
}

const files = {
  packageJson: read("package.json"),
  common: read("src/components/customer-banking/common.tsx"),
  page: read("src/app/page.tsx"),
  playwright: read("playwright.config.ts"),
  styles: read("src/styles/customer-banking.css"),
  accounts: read("src/components/customer-banking/sections/accounts-section.tsx"),
  dashboard: read("src/components/customer-banking/sections/dashboard-section.tsx"),
  transactions: read("src/components/customer-banking/sections/transactions-section.tsx"),
  security: read("src/components/customer-banking/sections/security-section.tsx"),
  transfer: read("src/components/customer-banking/sections/transfer-section.tsx"),
};

const required = [
  ["package a11y script", files.packageJson, "test:e2e:a11y"],
  ["package visual script", files.packageJson, "test:e2e:visual"],
  ["live base url mode", files.playwright, "const hasExplicitBaseURL"],
  ["live base url skips local webserver", files.playwright, "hasExplicitBaseURL ? undefined"],
  ["skip link target", files.page, 'href="#bank-work-area"'],
  ["main work area id", files.page, 'id="bank-work-area"'],
  ["main work area focus target", files.page, 'tabIndex={-1}'],
  ["layout auth state", files.page, "data-auth-state"],
  ["layout active section", files.page, "data-active-section"],
  ["top nav aria label", files.page, 'aria-label="주요 메뉴"'],
  ["side menu aria label", files.page, 'aria-label="개인뱅킹 메뉴"'],
  ["right rail aria label", files.page, 'aria-label="빠른 업무"'],
  ["login required accessible label", files.page, 'aria-label="로그인 필요 안내"'],
  ["login required action row", files.page, "login-required-actions"],
  ["right rail auth actions", files.page, "rail-auth-actions"],
  ["work state grid component", files.common, "WorkStateGrid"],
  ["empty state component", files.common, "EmptyState"],
  ["accounts work state grid", files.accounts, "조회 업무 상태"],
  ["transfer work state grid", files.transfer, "이체 진행 상태"],
  ["transactions work state grid", files.transactions, "거래 조회 상태"],
  ["security work state grid", files.security, "인증 업무 상태"],
  ["desktop breakpoint", files.styles, "@media (max-width: 1180px)"],
  ["tablet breakpoint", files.styles, "@media (max-width: 760px)"],
  ["mobile breakpoint", files.styles, "@media (max-width: 480px)"],
  ["layout auth state style", files.styles, ".bank-layout[data-auth-state"],
  ["desktop density style", files.styles, ".layout-health-strip"],
  ["mobile single flow style", files.styles, ".right-rail"],
  ["work state grid style", files.styles, ".work-state-grid"],
  ["empty state panel style", files.styles, ".empty-state-panel"],
  ["login required actions style", files.styles, ".login-required-actions"],
  ["focus visible style", files.styles, ":focus-visible"],
  ["touch target minimum", files.styles, "min-height: 44px"],
  ["mobile overflow guard", files.styles, "overflow-wrap: anywhere"],
  ["transfer step aria", files.transfer, 'aria-label="이체 진행 단계"'],
  ["otp input numeric", files.transfer, 'inputMode="numeric"'],
  ["bank service strip", files.styles, ".bank-service-strip"],
  ["bank notice strip", files.styles, ".bank-notice-strip"],
  ["bank work tabs", files.styles, ".work-tabs"],
  ["bank page title compact", files.styles, ".section-title h1"],
  ["right rail security notice", files.page, "보안알림"],
  ["service map panel", files.page, "전체서비스 메뉴"],
  ["service map expanded state", files.page, "aria-expanded"],
  ["recommended keyword label", files.page, "추천검색어"],
  ["service map panel style", files.styles, ".service-map-panel"],
  ["keyword list style", files.styles, ".keyword-list"],
  ["bank service list style", files.styles, ".bank-service-list"],
  ["bank news list style", files.styles, ".bank-news-list"],
  ["service hours table style", files.styles, ".service-hours-table"],
  ["unauth work gate heading", files.page, "로그인이 필요한 업무"],
  ["unauth login method grid", files.styles, ".login-method-grid"],
  ["bank shell security level", files.page, "보안등급"],
  ["bank work operating hours", files.dashboard, "이용시간"],
  ["account visual summary style", files.styles, ".account-summary-panel"],
  ["work command panel style", files.styles, ".work-command-panel"],
  ["transfer process grid style", files.styles, ".transfer-process-grid"],
  ["transfer receipt panel style", files.styles, ".transfer-receipt-panel"],
  ["transaction work grid style", files.styles, ".transaction-work-grid"],
  ["auth method grid style", files.styles, ".auth-method-grid"],
  ["security dashboard style", files.styles, ".security-dashboard"],
  ["work summary strip", files.styles, ".work-summary-strip"],
  ["process panel", files.styles, ".process-panel"],
];

const missing = required.filter(([, content, expected]) => !content.includes(expected));

if (missing.length > 0) {
  console.error("[customer-banking-visual-a11y] contract violations:");
  for (const [name, , expected] of missing) {
    console.error(`- missing ${name}: ${expected}`);
  }
  process.exit(1);
}

console.log("[customer-banking-visual-a11y] passed");
