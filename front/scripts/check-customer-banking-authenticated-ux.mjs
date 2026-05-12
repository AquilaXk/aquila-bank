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
  layout: read("src/components/customer-banking/layout.tsx"),
  common: read("src/components/customer-banking/common.tsx"),
  transfer: read("src/components/customer-banking/sections/transfer-section.tsx"),
  transactions: read("src/components/customer-banking/sections/transactions-section.tsx"),
  styles: read("src/styles/customer-banking.css"),
  e2e: read("e2e/customer-banking-authenticated-workflow.spec.ts"),
};

const transferReceiptContent = [files.transfer, files.common].join("\n");

const required = [
  ["package authenticated ux script", files.packageJson, "test:e2e:authenticated"],
  ["layout file", files.layout, "BankHeader"],
  ["header component", files.layout, "export function BankHeader"],
  ["side menu component", files.layout, "export function SideMenu"],
  ["right rail component", files.layout, "export function RightRail"],
  ["page uses bank header", files.page, "<BankHeader"],
  ["page uses side menu", files.page, "<SideMenu"],
  ["page uses right rail", files.page, "<RightRail"],
  ["work panel component", files.common, "export function WorkPanel"],
  ["bank table component", files.common, "export function BankTable"],
  ["form field component", files.common, "export function FormField"],
  ["transfer print receipt aria", transferReceiptContent, 'aria-label={label}'],
  ["transfer print receipt class", transferReceiptContent, "transfer-print-receipt"],
  ["transfer print button", files.transfer, "print-receipt-button"],
  ["transaction quick range toolbar", files.transactions, "quick-range-toolbar"],
  ["transaction quick today", files.transactions, "오늘"],
  ["transaction quick one month", files.transactions, "1개월"],
  ["transaction quick reset", files.transactions, "초기화"],
  ["transaction detail toggle", files.transactions, "상세조건 접기"],
  ["mobile search collapse", files.layout, "mobile-search-toggle"],
  ["mobile nav scroll style", files.styles, ".mobile-search-toggle"],
  ["touch action strip style", files.styles, ".touch-action-strip"],
  ["print media receipt style", files.styles, "@media print"],
  ["authenticated e2e login route", files.e2e, "/api/v1/auth/login"],
  ["authenticated e2e transfer route", files.e2e, "/api/v1/transfers"],
  ["authenticated e2e transaction route", files.e2e, "/api/v1/transactions"],
];

const missing = required.filter(([, content, expected]) => !content.includes(expected));

if (missing.length > 0) {
  console.error("[customer-banking-authenticated-ux] contract violations:");
  for (const [name, , expected] of missing) {
    console.error(`- missing ${name}: ${expected}`);
  }
  process.exit(1);
}

console.log("[customer-banking-authenticated-ux] passed");
