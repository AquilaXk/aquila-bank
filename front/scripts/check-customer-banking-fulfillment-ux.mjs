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
  security: read("src/components/customer-banking/sections/security-section.tsx"),
  playwrightConfig: read("playwright.config.ts"),
  clickFlow: read("e2e/customer-banking-click-flow.spec.ts"),
  styles: read("src/styles/customer-banking.css"),
  backendController: read("../back/src/main/java/com/aquilabank/global/web/customerapplication/CustomerApplicationController.java"),
  backendService: read("../back/src/main/java/com/aquilabank/domain/customerapplication/usecase/CustomerApplicationService.java"),
  backendTotpService: read("../back/src/main/java/com/aquilabank/domain/auth/usecase/TotpOperationVerifyService.java"),
  backendMigration: read("../back/src/main/resources/db/migration/V54__add_customer_service_application.sql"),
  backendControllerTest: read("../back/src/test/java/com/aquilabank/global/web/customerapplication/CustomerApplicationControllerTest.java"),
  backendTotpTest: read("../back/src/test/java/com/aquilabank/domain/auth/usecase/TotpOperationVerifyServiceTest.java"),
};

const required = [
  ["package script", files.packageJson, "test:fulfillment-ux"],
  ["package click flow script", files.packageJson, "test:e2e:click-flow"],
  ["preview request type", files.apiTypes, "TransferPreviewRequest"],
  ["preview response type", files.apiTypes, "TransferPreviewResponse"],
  ["preview blocked reason", files.apiTypes, "blockedReason"],
  ["preview api method", files.apiClient, "previewTransfer"],
  ["preview api path", files.apiClient, "/api/v1/transfers/preview"],
  ["customer application request type", files.apiTypes, "CustomerApplicationRequest"],
  ["customer application response type", files.apiTypes, "CustomerApplicationResponse"],
  ["customer application api method", files.apiClient, "submitCustomerApplication"],
  ["customer application api path", files.apiClient, "/api/v1/customer-service/applications"],
  ["customer application idempotency", files.apiClient, "Idempotency-Key"],
  ["preview hook state", files.hook, "transferPreview"],
  ["preview hook handler", files.hook, "handlePreviewTransfer"],
  ["customer application hook state", files.hook, "customerApplicationResult"],
  ["customer application hook handler", files.hook, "handleSubmitCustomerApplication"],
  ["preview page binding", files.page, "onPreviewTransfer"],
  ["customer application page binding", files.page, "onSubmitCustomerApplication"],
  ["preview section prop", files.transfer, "onPreviewTransfer"],
  ["transfer security verification label", files.transfer, "보안 확인"],
  ["receiver validation display", files.transfer, "받는 분"],
  ["fee display", files.transfer, "수수료"],
  ["limit remaining display", files.transfer, "잔여한도"],
  ["transfer final confirmation", files.transfer, "최종 확인"],
  ["transfer processing result", files.transfer, "처리 결과"],
  ["bill payment detail", files.enterprise, "공과금 납부"],
  ["bill payment write type", files.enterprise, "BILL_PAYMENT"],
  ["open banking detail", files.enterprise, "오픈뱅킹 연결"],
  ["open banking write type", files.enterprise, "OPEN_BANKING_CONNECTION"],
  ["deposit product detail", files.enterprise, "예금 가입"],
  ["deposit product write type", files.enterprise, "DEPOSIT_PRODUCT_APPLICATION"],
  ["loan detail", files.enterprise, "대출 신청"],
  ["loan write type", files.enterprise, "LOAN_APPLICATION"],
  ["fx detail", files.enterprise, "외환 신청"],
  ["fx write type", files.enterprise, "FOREIGN_EXCHANGE_APPLICATION"],
  ["enterprise application submit panel", files.enterprise, "application-submit-panel"],
  ["common certificate registration", files.securityHub, "공동인증서 등록"],
  ["financial certificate registration", files.securityHub, "금융인증서 등록"],
  ["security media registration", files.securityHub, "보안매체 등록"],
  ["otp registration", files.securityHub, "OTP 등록"],
  ["security certificate management", files.securityHub, "인증서 관리"],
  ["certificate registration write type", files.securityHub, "CERTIFICATE_REGISTRATION"],
  ["security media write type", files.securityHub, "SECURITY_MEDIA_APPLICATION"],
  ["transfer receipt certificate", files.support, "이체확인증"],
  ["balance certificate", files.support, "잔액증명서"],
  ["transaction certificate", files.support, "거래내역확인서"],
  ["support FAQ", files.support, "FAQ"],
  ["incident intake", files.support, "사고신고 접수"],
  ["incident write type", files.support, "INCIDENT_REPORT"],
  ["certificate issuance write type", files.support, "CERTIFICATE_ISSUANCE"],
  ["fulfillment detail styles", files.styles, ".fulfillment-detail-grid"],
  ["registration flow styles", files.styles, ".registration-flow-grid"],
  ["incident form styles", files.styles, ".incident-form"],
  ["certificate list styles", files.styles, ".certificate-list"],
  ["application submit styles", files.styles, ".application-submit-panel"],
  ["application result styles", files.styles, ".application-result-line"],
  ["process panel styles", files.styles, ".process-panel"],
  ["playwright config", files.playwrightConfig, "defineConfig"],
  ["click flow playwright test", files.clickFlow, "@playwright/test"],
  ["click flow transfer guard", files.clickFlow, "권한 만료 또는 미로그인"],
  ["click flow public service", files.clickFlow, "신청 접수"],
  ["click flow application submit buttons", files.clickFlow, "신청 접수"],
  ["backend customer application controller", files.backendController, "/api/v1/customer-service/applications"],
  ["backend idempotency header", files.backendController, "Idempotency-Key"],
  ["backend customer application service", files.backendService, "CustomerApplicationSecurityVerificationPort"],
  ["backend TOTP operation verification", files.backendTotpService, "TotpOperationVerifyCommand"],
  ["backend application table", files.backendMigration, "customer_service_application"],
  ["backend controller coverage", files.backendControllerTest, "CustomerApplicationControllerTest"],
  ["backend TOTP coverage", files.backendTotpTest, "TotpOperationVerifyServiceTest"],
];

const customerSections = [
  files.transfer,
  files.enterprise,
  files.security,
  files.securityHub,
  files.support,
].join("\n");

const forbidden = [
  ["no-op work tabs", customerSections, "onClick: () => undefined"],
  ["raw challenge type fallback", files.security, 'props.challenge.challengeType ?? "TOTP"'],
  ["raw blocked reason render", files.transfer, "{blockedReason}"],
  ["raw transfer status render", files.transfer, '["상태", transferResult.status]'],
  ["customer visible explanatory provide", customerSections, "제공"],
  ["customer visible explanatory flow", customerSections, "흐름"],
  ["customer visible bank-like explanation", customerSections, "처럼"],
  ["customer visible actual implementation explanation", customerSections, "실제"],
];

const missing = required.filter(([, content, expected]) => !content.includes(expected));
const present = forbidden.filter(([, content, value]) => content.includes(value));

if (missing.length > 0 || present.length > 0) {
  console.error("[customer-banking-fulfillment-ux] contract violations:");
  for (const [name, , expected] of missing) {
    console.error(`- missing ${name}: ${expected}`);
  }
  for (const [name, , value] of present) {
    console.error(`- forbidden ${name}: ${value}`);
  }
  process.exit(1);
}

console.log("[customer-banking-fulfillment-ux] passed");
