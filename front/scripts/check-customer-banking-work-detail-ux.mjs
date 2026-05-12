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
  accounts: read("src/components/customer-banking/sections/accounts-section.tsx"),
  transfer: read("src/components/customer-banking/sections/transfer-section.tsx"),
  transactions: read("src/components/customer-banking/sections/transactions-section.tsx"),
  security: read("src/components/customer-banking/sections/security-section.tsx"),
  styles: read("src/styles/customer-banking.css"),
  liveArtifacts: read("e2e/customer-banking-live-artifacts.spec.ts"),
  authenticatedE2e: read("e2e/customer-banking-authenticated-workflow.spec.ts"),
};

const required = [
  ["package work detail script", files.packageJson, "test:work-detail-ux"],
  ["package live artifact script", files.packageJson, "test:e2e:live-artifacts"],
  ["bank form component", files.common, "export function BankForm"],
  ["bank select component", files.common, "export function BankSelect"],
  ["status badge component", files.common, "export function StatusBadge"],
  ["receipt panel component", files.common, "export function ReceiptPanel"],
  ["pagination bar component", files.common, "export function PaginationBar"],
  ["field error component", files.common, "export function FieldError"],
  ["account selected state", files.accounts, "선택됨"],
  ["account suspended label", files.accounts, "지급정지"],
  ["account restricted label", files.accounts, "거래제한"],
  ["account status grid", files.accounts, "account-status-grid"],
  ["transfer receiver verification detail", files.transfer, "받는 사람 검증 결과"],
  ["transfer otp verification detail", files.transfer, "OTP 검증 상태"],
  ["transfer mobile sticky action", files.transfer, "mobile-sticky-actions"],
  ["transfer field error usage", files.transfer, "<FieldError"],
  ["transaction keyset pagination", files.transactions, "Keyset 기준"],
  ["transaction transfer receipt link", files.transactions, "이체확인증"],
  ["transaction scroll hint", files.transactions, "좌우로 스크롤"],
  ["transaction mobile filter drawer", files.transactions, "mobile-filter-drawer"],
  ["security device/session panel", files.security, "세션/기기 목록"],
  ["security revoke state", files.security, "세션 해지 상태"],
  ["security media enrollment state", files.security, "보안매체 등록"],
  ["mobile sticky style", files.styles, ".mobile-sticky-actions"],
  ["mobile filter drawer style", files.styles, ".mobile-filter-drawer"],
  ["table scroll hint style", files.styles, ".table-scroll-hint"],
  ["account status grid style", files.styles, ".account-status-grid"],
  ["status badge variants", files.styles, ".status-badge.warn"],
  ["live artifact screenshot", files.liveArtifacts, "customer-banking-live-desktop.png"],
  ["live artifact mobile screenshot", files.liveArtifacts, "customer-banking-live-mobile.png"],
  ["authenticated e2e restricted account", files.authenticatedE2e, "거래제한"],
  ["authenticated e2e transfer receipt", files.authenticatedE2e, "이체확인증"],
  ["authenticated e2e security media", files.authenticatedE2e, "보안매체 등록"],
];

const missing = required.filter(([, content, expected]) => !content.includes(expected));

if (missing.length > 0) {
  console.error("[customer-banking-work-detail-ux] contract violations:");
  for (const [name, , expected] of missing) {
    console.error(`- missing ${name}: ${expected}`);
  }
  process.exit(1);
}

console.log("[customer-banking-work-detail-ux] passed");
