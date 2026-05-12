import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const root = new URL("..", import.meta.url).pathname;

function read(path) {
  const filePath = join(root, path);
  return existsSync(filePath) ? readFileSync(filePath, "utf8") : "";
}

const files = {
  packageJson: read("package.json"),
  enterprise: read("src/components/customer-banking/sections/enterprise-services-section.tsx"),
  styles: read("src/styles/customer-banking.css"),
};

const required = [
  ["package enterprise detail script", files.packageJson, "test:enterprise-detail-ux"],
  ["enterprise bank input usage", files.enterprise, "BankInput"],
  ["enterprise action bar usage", files.enterprise, "BankActionBar"],
  ["enterprise table primitive usage", files.enterprise, "BankTable"],
  ["bill application type", files.enterprise, "BILL_PAYMENT"],
  ["open banking application type", files.enterprise, "OPEN_BANKING_CONNECTION"],
  ["deposit application type", files.enterprise, "DEPOSIT_PRODUCT_APPLICATION"],
  ["loan application type", files.enterprise, "LOAN_APPLICATION"],
  ["fx application type", files.enterprise, "FOREIGN_EXCHANGE_APPLICATION"],
  ["enterprise payload binding", files.enterprise, "payload: Object.fromEntries"],
  ["enterprise totp binding", files.enterprise, "totpCode"],
  ["enterprise account binding", files.enterprise, "accountId"],
  ["enterprise submit result", files.enterprise, "최근 접수"],
  ["enterprise application form class", files.enterprise, "enterprise-application-form"],
  ["enterprise application form style", files.styles, ".enterprise-application-form"],
];

const missing = required.filter(([, content, expected]) => !content.includes(expected));
const forbidden = [
  ["unsupported bill payment reservation", files.enterprise, "납부 예약"],
  ["unsupported open banking query result", files.enterprise, "통합조회 결과"],
  ["unsupported deposit interest preview", files.enterprise, "예상 이자"],
  ["unsupported loan repayment schedule", files.enterprise, "상환 스케줄"],
  ["unsupported fx rate query result", files.enterprise, "환율 조회 결과"],
  ["unsupported loan inquiry flow", files.enterprise, "한도조회"],
  ["unsupported repayment inquiry flow", files.enterprise, "상환조회"],
  ["unsupported consultation reservation flow", files.enterprise, "상담 예약"],
  ["unsupported fx quoted rate display", files.enterprise, "고시환율"],
];
const present = forbidden.filter(([, content, expected]) => content.includes(expected));

if (missing.length > 0 || present.length > 0) {
  console.error("[customer-banking-enterprise-detail-ux] contract violations:");
  for (const [name, , expected] of missing) {
    console.error(`- missing ${name}: ${expected}`);
  }
  for (const [name, , expected] of present) {
    console.error(`- forbidden ${name}: ${expected}`);
  }
  process.exit(1);
}

console.log("[customer-banking-enterprise-detail-ux] passed");
