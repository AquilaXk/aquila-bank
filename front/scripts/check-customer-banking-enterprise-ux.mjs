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
  constants: read("src/lib/customer-banking/constants.ts"),
  types: read("src/lib/customer-banking/types.ts"),
  transfer: read("src/components/customer-banking/sections/transfer-section.tsx"),
  enterprise: read("src/components/customer-banking/sections/enterprise-services-section.tsx"),
  support: read("src/components/customer-banking/sections/support-center-section.tsx"),
  securityHub: read("src/components/customer-banking/sections/security-hub-section.tsx"),
  styles: read("src/styles/customer-banking.css"),
};

const required = [
  ["package enterprise script", files.packageJson, "test:enterprise-ux"],
  ["menu customer center", files.constants, "고객센터"],
  ["menu security center", files.constants, "보안센터"],
  ["menu incident report", files.constants, "사고신고"],
  ["menu transfer limit", files.constants, "이체한도"],
  ["menu OTP", files.constants, "OTP"],
  ["menu bill payment", files.constants, "공과금"],
  ["menu open banking", files.constants, "오픈뱅킹"],
  ["menu deposit products", files.constants, "예금상품"],
  ["menu loan", files.constants, "대출"],
  ["menu fx", files.constants, "외환"],
  ["menu security hub section", files.types, "securityHub"],
  ["menu enterprise services section", files.types, "enterpriseServices"],
  ["menu support center section", files.types, "supportCenter"],
  ["page enterprise services component", files.page, "EnterpriseServicesSection"],
  ["page support center component", files.page, "SupportCenterSection"],
  ["page security hub component", files.page, "SecurityHubSection"],
  ["enterprise bill payment panel", files.enterprise, "공과금"],
  ["enterprise open banking panel", files.enterprise, "오픈뱅킹"],
  ["enterprise deposit panel", files.enterprise, "예금상품"],
  ["enterprise loan panel", files.enterprise, "대출"],
  ["enterprise fx panel", files.enterprise, "외환"],
  ["support customer center panel", files.support, "고객센터"],
  ["support incident report panel", files.support, "사고신고"],
  ["support limit panel", files.support, "이체한도"],
  ["security certificate panel", files.securityHub, "공동인증서"],
  ["security financial certificate panel", files.securityHub, "금융인증서"],
  ["security OTP panel", files.securityHub, "OTP"],
  ["security media panel", files.securityHub, "보안매체"],
  ["transfer receiver validation", files.transfer, "받는 분 확인"],
  ["transfer fee estimate", files.transfer, "수수료"],
  ["transfer limit check", files.transfer, "이체한도"],
  ["transfer OTP confirmation", files.transfer, "OTP 확인"],
  ["transfer receipt", files.transfer, "이체 완료증"],
  ["enterprise grid styles", files.styles, ".enterprise-service-grid"],
  ["security hub styles", files.styles, ".security-hub-grid"],
  ["receipt styles", files.styles, ".transfer-receipt"],
];

const forbidden = [
  ["enterprise write submit", files.enterprise, "onSubmit"],
  ["enterprise write fetch", files.enterprise, "fetch("],
  ["support write fetch", files.support, "fetch("],
  ["security storage", files.securityHub, "localStorage"],
  ["security session storage", files.securityHub, "sessionStorage"],
];

const missing = required.filter(([, content, expected]) => !content.includes(expected));
const present = forbidden.filter(([, content, value]) => content.includes(value));

if (missing.length > 0 || present.length > 0) {
  console.error("[customer-banking-enterprise-ux] contract violations:");
  for (const [name, , expected] of missing) {
    console.error(`- missing ${name}: ${expected}`);
  }
  for (const [name, , value] of present) {
    console.error(`- forbidden ${name}: ${value}`);
  }
  process.exit(1);
}

console.log("[customer-banking-enterprise-ux] passed");
