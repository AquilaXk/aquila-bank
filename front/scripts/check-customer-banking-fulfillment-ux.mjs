import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const root = new URL("..", import.meta.url).pathname;

function read(path) {
  const filePath = join(root, path);
  return existsSync(filePath) ? readFileSync(filePath, "utf8") : "";
}

const files = {
  packageJson: read("package.json"),
  apiTypes: read("src/lib/api/types.ts"),
  apiClient: read("src/lib/api/client.ts"),
  hook: read("src/hooks/use-customer-banking.ts"),
  page: read("src/app/page.tsx"),
  transfer: read("src/components/customer-banking/sections/transfer-section.tsx"),
  enterprise: read("src/components/customer-banking/sections/enterprise-services-section.tsx"),
  securityHub: read("src/components/customer-banking/sections/security-hub-section.tsx"),
  support: read("src/components/customer-banking/sections/support-center-section.tsx"),
  styles: read("src/styles/customer-banking.css"),
};

const required = [
  ["package script", files.packageJson, "test:fulfillment-ux"],
  ["preview request type", files.apiTypes, "TransferPreviewRequest"],
  ["preview response type", files.apiTypes, "TransferPreviewResponse"],
  ["preview blocked reason", files.apiTypes, "blockedReason"],
  ["preview api method", files.apiClient, "previewTransfer"],
  ["preview api path", files.apiClient, "/api/v1/transfers/preview"],
  ["preview hook state", files.hook, "transferPreview"],
  ["preview hook handler", files.hook, "handlePreviewTransfer"],
  ["preview page binding", files.page, "onPreviewTransfer"],
  ["preview section prop", files.transfer, "onPreviewTransfer"],
  ["backend preview label", files.transfer, "backend preview"],
  ["receiver validation display", files.transfer, "받는 분 검증"],
  ["fee policy display", files.transfer, "feePolicy"],
  ["limit remaining display", files.transfer, "dailyRemainingMinor"],
  ["otp required display", files.transfer, "otpRequired"],
  ["bill payment detail", files.enterprise, "공과금 상세"],
  ["open banking detail", files.enterprise, "오픈뱅킹 상세"],
  ["deposit product detail", files.enterprise, "예금상품 상세"],
  ["loan detail", files.enterprise, "대출 상세"],
  ["fx detail", files.enterprise, "외환 상세"],
  ["common certificate registration", files.securityHub, "공동인증서 등록"],
  ["financial certificate registration", files.securityHub, "금융인증서 등록"],
  ["security media registration", files.securityHub, "보안매체 등록"],
  ["otp registration", files.securityHub, "OTP 등록"],
  ["transfer receipt certificate", files.support, "이체확인증"],
  ["balance certificate", files.support, "잔액증명서"],
  ["transaction certificate", files.support, "거래내역확인서"],
  ["support FAQ", files.support, "FAQ"],
  ["incident intake", files.support, "사고신고 접수"],
  ["fulfillment detail styles", files.styles, ".fulfillment-detail-grid"],
  ["registration flow styles", files.styles, ".registration-flow-grid"],
  ["incident form styles", files.styles, ".incident-form"],
  ["certificate list styles", files.styles, ".certificate-list"],
];

const missing = required.filter(([, content, expected]) => !content.includes(expected));

if (missing.length > 0) {
  console.error("[customer-banking-fulfillment-ux] contract violations:");
  for (const [name, , expected] of missing) {
    console.error(`- missing ${name}: ${expected}`);
  }
  process.exit(1);
}

console.log("[customer-banking-fulfillment-ux] passed");
