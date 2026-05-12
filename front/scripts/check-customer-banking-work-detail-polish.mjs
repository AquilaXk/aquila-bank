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
};

const required = [
  ["package work detail script", files.packageJson, "test:work-detail-polish"],
  ["bank input error support", files.common, "error?: string"],
  ["bank table dense support", files.common, "dense-bank-table"],
  ["receipt print view primitive", files.common, "receipt-print-view"],
  ["account dense table", files.accounts, "dense-bank-table"],
  ["account detail work panel", files.accounts, "account-detail-work-panel"],
  ["transfer bank input usage", files.transfer, "BankInput"],
  ["transfer tight form", files.transfer, "tight-work-form"],
  ["transfer receipt print copy", files.transfer, "이체 완료증 출력 전용"],
  ["transaction dense table", files.transactions, "dense-bank-table"],
  ["transaction print detail copy", files.transactions, "거래 상세정보 출력 전용"],
  ["security bank input usage", files.security, "BankInput"],
  ["security action bar label", files.security, "인증 업무 action bar"],
  ["tight form style", files.styles, ".bank-form.tight-work-form"],
  ["dense table style", files.styles, ".bank-table.dense-bank-table"],
  ["account detail panel style", files.styles, ".account-detail-work-panel"],
  ["receipt print style", files.styles, ".receipt-print-view"],
];

const missing = required.filter(([, content, expected]) => !content.includes(expected));

if (missing.length > 0) {
  console.error("[customer-banking-work-detail-polish] contract violations:");
  for (const [name, , expected] of missing) {
    console.error(`- missing ${name}: ${expected}`);
  }
  process.exit(1);
}

console.log("[customer-banking-work-detail-polish] passed");
