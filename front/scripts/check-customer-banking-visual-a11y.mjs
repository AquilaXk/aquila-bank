import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const root = new URL("..", import.meta.url).pathname;

function read(path) {
  const filePath = join(root, path);
  return existsSync(filePath) ? readFileSync(filePath, "utf8") : "";
}

const files = {
  packageJson: read("package.json"),
  page: read("src/app/page.tsx"),
  styles: read("src/styles/customer-banking.css"),
  dashboard: read("src/components/customer-banking/sections/dashboard-section.tsx"),
  transfer: read("src/components/customer-banking/sections/transfer-section.tsx"),
};

const required = [
  ["package a11y script", files.packageJson, "test:e2e:a11y"],
  ["skip link target", files.page, 'href="#bank-work-area"'],
  ["main work area id", files.page, 'id="bank-work-area"'],
  ["top nav aria label", files.page, 'aria-label="주요 메뉴"'],
  ["side menu aria label", files.page, 'aria-label="개인뱅킹 메뉴"'],
  ["right rail aria label", files.page, 'aria-label="빠른 업무"'],
  ["desktop breakpoint", files.styles, "@media (max-width: 1180px)"],
  ["tablet breakpoint", files.styles, "@media (max-width: 760px)"],
  ["mobile breakpoint", files.styles, "@media (max-width: 480px)"],
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
  ["bank shell security level", files.page, "보안등급"],
  ["bank work operating hours", files.dashboard, "이용시간"],
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
