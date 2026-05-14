import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = new URL("..", import.meta.url).pathname;

function read(path) {
  return readFileSync(join(root, path), "utf8");
}

const files = {
  apiClient: read("src/lib/api/client.ts"),
  apiTypes: read("src/lib/api/types.ts"),
  hook: read("src/hooks/use-customer-banking.ts"),
  page: read("src/app/page.tsx"),
  support: read("src/components/customer-banking/sections/support-center-section.tsx"),
  styles: read("src/styles/customer-banking.css"),
  authenticatedE2e: read("e2e/customer-banking-authenticated-workflow.spec.ts"),
};

const required = [
  ["application list api", files.apiClient, "listCustomerApplications"],
  ["application detail api", files.apiClient, "getCustomerApplication"],
  ["application cancel api", files.apiClient, "cancelCustomerApplication"],
  ["application details type", files.apiTypes, "CustomerApplicationDetailsResponse"],
  ["hook list state", files.hook, "customerApplications"],
  ["hook selected state", files.hook, "selectedCustomerApplication"],
  ["hook load handler", files.hook, "handleLoadCustomerApplications"],
  ["hook select handler", files.hook, "handleSelectCustomerApplication"],
  ["hook cancel handler", files.hook, "handleCancelCustomerApplication"],
  ["submit refreshes list", files.hook, "await refreshCustomerApplications()"],
  ["page list binding", files.page, "customerApplications={customerApplications}"],
  ["page selected binding", files.page, "selectedCustomerApplication={selectedCustomerApplication}"],
  ["support status panel", files.support, "신청 현황"],
  ["support refresh button", files.support, "신청 목록 새로고침"],
  ["support detail heading", files.support, "신청 상세"],
  ["support cancel button", files.support, "취소 요청"],
  ["support processing mode", files.support, "처리 모드"],
  ["support execution result", files.support, "실행 결과"],
  ["support mock boundary", files.support, "mock/webhook 경계"],
  ["status panel style", files.styles, ".application-status-panel"],
  ["status list style", files.styles, ".application-status-list"],
  ["authenticated e2e application route", files.authenticatedE2e, "/api/v1/customer-service/applications"],
  ["authenticated e2e application status", files.authenticatedE2e, "신청 현황"],
  ["authenticated e2e cancel", files.authenticatedE2e, "취소 요청"],
];

const missing = required.filter(([, content, expected]) => !content.includes(expected));

if (missing.length > 0) {
  console.error("[customer-application-self-service-ux] contract violations:");
  for (const [name, , expected] of missing) {
    console.error(`- missing ${name}: ${expected}`);
  }
  process.exit(1);
}

console.log("[customer-application-self-service-ux] passed");
