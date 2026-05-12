import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const root = new URL("..", import.meta.url).pathname;
const repoRoot = join(root, "..");

function read(path) {
  const filePath = path.startsWith(".github")
    ? join(repoRoot, path)
    : join(root, path);
  return existsSync(filePath) ? readFileSync(filePath, "utf8") : "";
}

const files = {
  packageJson: read("package.json"),
  common: read("src/components/customer-banking/common.tsx"),
  page: read("src/app/page.tsx"),
  layout: read("src/components/customer-banking/layout.tsx"),
  accounts: read("src/components/customer-banking/sections/accounts-section.tsx"),
  transfer: read("src/components/customer-banking/sections/transfer-section.tsx"),
  transactions: read("src/components/customer-banking/sections/transactions-section.tsx"),
  security: read("src/components/customer-banking/sections/security-section.tsx"),
  styles: read("src/styles/customer-banking.css"),
  workflow: read(".github/workflows/frontend-ci.yml"),
  liveArtifacts: read("e2e/customer-banking-live-artifacts.spec.ts"),
};

const required = [
  ["package density script", files.packageJson, "test:density-polish-ux"],
  ["bank input primitive", files.common, "export function BankInput"],
  ["bank date range primitive", files.common, "export function BankDateRange"],
  ["bank action bar primitive", files.common, "export function BankActionBar"],
  ["bank drawer primitive", files.common, "export function BankDrawer"],
  ["bank dialog primitive", files.common, "export function BankDialog"],
  ["bank toolbar primitive", files.common, "export function BankToolbar"],
  ["dense shell class", files.page, "bank-shell dense-banking-shell"],
  ["dense section style", files.styles, ".dense-banking-shell"],
  ["menu compact style", files.styles, ".side-menu.compact-menu"],
  ["rail equalized style", files.styles, ".right-rail.aligned-rail"],
  ["work title compact style", files.styles, ".task-section.compact-work-section"],
  ["account low balance", files.accounts, "잔액부족"],
  ["account selected badge", files.accounts, "선택계좌"],
  ["transfer limit exceeded state", files.transfer, "한도 초과"],
  ["transfer otp error state", files.transfer, "OTP 오류"],
  ["transfer recipient mismatch", files.transfer, "수취계좌 불일치"],
  ["transfer failed receipt", files.transfer, "실패 완료증"],
  ["transaction drawer hook", files.transactions, "BankDrawer"],
  ["transaction print view hook", files.transactions, "이체확인증 출력"],
  ["security expiring session", files.security, "만료 임박"],
  ["security current device", files.security, "현재 기기"],
  ["security revoke impossible", files.security, "해지 불가"],
  ["action bar usage", [files.accounts, files.transfer, files.transactions, files.security].join("\n"), "BankActionBar"],
  ["toolbar usage", [files.accounts, files.transactions].join("\n"), "BankToolbar"],
  ["ci playwright install", files.workflow, "playwright install --with-deps chromium"],
  ["ci live artifact test", files.workflow, "test:e2e:live-artifacts"],
  ["ci upload artifact", files.workflow, "actions/upload-artifact@v7"],
  ["artifact retention", files.workflow, "retention-days"],
  ["live artifact output path", files.liveArtifacts, "customer-banking-live-desktop.png"],
];

const missing = required.filter(([, content, expected]) => !content.includes(expected));

if (missing.length > 0) {
  console.error("[customer-banking-density-polish-ux] contract violations:");
  for (const [name, , expected] of missing) {
    console.error(`- missing ${name}: ${expected}`);
  }
  process.exit(1);
}

console.log("[customer-banking-density-polish-ux] passed");
