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
