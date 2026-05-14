import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const root = new URL("..", import.meta.url).pathname;

function read(path) {
  const filePath = join(root, path);
  return existsSync(filePath) ? readFileSync(filePath, "utf8") : "";
}

const files = {
  packageJson: read("package.json"),
  layout: read("src/app/layout.tsx"),
  page: read("src/app/ops/page.tsx"),
  component: read("src/components/ops-console/ops-console.tsx"),
  client: read("src/lib/ops-console/client.ts"),
  types: read("src/lib/ops-console/types.ts"),
  styles: read("src/styles/ops-console.css"),
  mockWebhookDoc: read("../docs/customer-application-mock-webhook.md"),
};

const required = [
  ["package script", files.packageJson, "test:ops-console"],
  ["layout stylesheet", files.layout, "../styles/ops-console.css"],
  ["ops route", files.page, "OpsConsole"],
  ["feature flag", files.component, "NEXT_PUBLIC_OPS_CONSOLE_ENABLED"],
  ["token memory field", files.component, "internal service token"],
  ["outbox section", files.component, "Outbox"],
  ["DLQ section", files.component, "DLQ"],
  ["ledger section", files.component, "Ledger"],
  ["snapshot section", files.component, "Snapshot"],
  ["auth section", files.component, "Auth"],
  ["account section", files.component, "Account"],
  ["recovery actions section", files.component, "Recovery Actions"],
  ["outbox confirmation phrase", files.component, "RUN OUTBOX"],
  ["dlq confirmation phrase", files.component, "RUN DLQ"],
  ["channel confirmation phrase", files.component, "RUN CHANNEL"],
  ["idempotency confirmation phrase", files.component, "RUN IDEMPOTENCY"],
  ["snapshot confirmation phrase", files.component, "RUN SNAPSHOT"],
  ["dlq partition field", files.component, "DLQ partition"],
  ["dlq offset field", files.component, "DLQ offset"],
  ["channel event id field", files.component, "Channel quarantined event ID"],
  ["snapshot account field", files.component, "Snapshot accountId"],
  ["snapshot reason field", files.component, "Snapshot reason"],
  ["post action runner", files.component, "runAction"],
  ["outbox summary endpoint", files.client, "/internal/api/v1/outbox/summary"],
  ["outbox failed endpoint", files.client, "/internal/api/v1/outbox/failed-events"],
  ["notification summary endpoint", files.client, "/internal/api/v1/outbox/notification/summary"],
  ["notification dlq endpoint", files.client, "/internal/api/v1/outbox/notification/dlq-events"],
  ["channel quarantine endpoint", files.client, "/internal/api/v1/outbox/notification-channel/quarantined-events"],
  ["snapshot drift endpoint", files.client, "/internal/api/v1/ledger/snapshot-reconciliation/drifts"],
  ["command idempotency summary endpoint", files.client, "/internal/api/v1/ledger/command-idempotency/summary"],
  ["command idempotency stale endpoint", files.client, "/internal/api/v1/ledger/command-idempotency/stale-started"],
  ["auth audit endpoint", files.client, "/internal/api/v1/auth/status-change-audits"],
  ["account audit endpoint", files.client, "/internal/api/v1/accounts/status-change-audits/by-request-id"],
  ["GET-only request helper", files.client, 'method: "GET"'],
  ["POST request helper", files.client, "postOpsJson"],
  ["POST request method", files.client, 'method: "POST"'],
  ["PUT request helper", files.client, "putOpsJson"],
  ["PUT request method", files.client, 'method: "PUT"'],
  ["PUT request id header", files.client, '"X-Request-Id": requestId'],
  ["outbox stale recovery endpoint", files.client, "/internal/api/v1/outbox/recovery/stale-sending"],
  ["notification dlq redrive endpoint", files.client, "/internal/api/v1/outbox/notification/dlq-events/redrive"],
  ["channel redrive endpoint", files.client, "/internal/api/v1/outbox/notification-channel/quarantined-events/${id}/redrive"],
  ["command idempotency recovery endpoint", files.client, "/internal/api/v1/ledger/command-idempotency/recovery/stale-started"],
  ["snapshot recovery endpoint", files.client, "/internal/api/v1/ledger/snapshot-reconciliation/accounts/${accountId}/recovery"],
  ["status actions section", files.component, "AUTH_ADMIN / ACCOUNT_ADMIN status changes"],
  ["auth user status action", files.component, "Auth user status update"],
  ["membership status action", files.component, "Auth membership status update"],
  ["account status action", files.component, "Account status update"],
  ["status action runner", files.component, "runStatusAction"],
  ["customer application actions section", files.component, "Customer Application Actions"],
  ["customer application reference field", files.component, "Application reference"],
  ["customer application reason field", files.component, "Application reason"],
  ["customer application callback success field", files.component, "Callback success"],
  ["customer application callback payload field", files.component, "Callback payload JSON"],
  ["customer application review phrase", files.component, "REVIEW APPLICATION"],
  ["customer application approve phrase", files.component, "APPROVE APPLICATION"],
  ["customer application reject phrase", files.component, "REJECT APPLICATION"],
  ["customer application cancel phrase", files.component, "CANCEL APPLICATION"],
  ["customer application execute phrase", files.component, "EXECUTE APPLICATION"],
  ["customer application callback phrase", files.component, "CALLBACK APPLICATION"],
  ["customer application action runner", files.component, "runCustomerApplicationAction"],
  ["customer application callback runner", files.component, "runCustomerApplicationCallback"],
  ["customer application path helper", files.client, "customerApplicationOperationPath"],
  ["customer application callback path helper", files.client, "customerApplicationExternalCallbackPath"],
  ["customer application review endpoint", files.client, "/internal/api/v1/customer-service/applications/${encodeURIComponent(applicationReference)}/${action}"],
  ["customer application callback endpoint", files.client, "/external-callback"],
  ["user status confirmation phrase", files.component, "CHANGE USER"],
  ["membership confirmation phrase", files.component, "CHANGE MEMBERSHIP"],
  ["account confirmation phrase", files.component, "CHANGE ACCOUNT"],
  ["status reason code field", files.component, "Reason code"],
  ["status reason detail field", files.component, "Reason detail"],
  ["status request id field", files.component, "Request ID"],
  ["user status update path", files.client, "/internal/api/v1/auth/users/${encodeURIComponent(userId)}/status"],
  ["membership status update path", files.client, "}/memberships/${encodeURIComponent(accountId)}/status"],
  ["account status update path", files.client, "/internal/api/v1/accounts/${encodeURIComponent(accountId)}/status"],
  ["account audit linkage", files.component, "accountAuditByRequestIdPath(form.accountStatusRequestId)"],
  ["auth audit linkage", files.component, "authAuditByRequestIdPath(form.authStatusRequestId)"],
  ["ops styles", files.styles, ".ops-shell"],
  ["ops gothic page token", files.styles, "--ops-gothic-page"],
  ["ops gothic surface token", files.styles, "--ops-gothic-surface"],
  ["ops gothic brass token", files.styles, "--ops-gothic-brass"],
  ["ops read-only badge class", files.styles, ".ops-readonly-badge"],
  ["ops write badge class", files.styles, ".ops-write-badge"],
  ["ops actions class", files.styles, ".ops-actions"],
  ["ops status actions class", files.styles, ".ops-status-actions"],
  ["ops action card class", files.styles, ".ops-action-card"],
  ["ops status card class", files.styles, ".ops-status-card"],
  ["ops danger button class", files.styles, ".ops-danger-button"],
  ["ops risk note class", files.styles, ".ops-risk-note"],
  ["ops dark result surface", files.styles, ".ops-result"],
  ["ops control-room shell", files.styles, ".ops-control-room"],
  ["mock webhook doc title", files.mockWebhookDoc, "Customer Application Mock Webhook"],
  ["mock webhook callback endpoint", files.mockWebhookDoc, "/internal/api/v1/customer-service/applications/{applicationReference}/external-callback"],
  ["mock webhook ops phrase", files.mockWebhookDoc, "CALLBACK APPLICATION"],
  ["mock webhook no vendor", files.mockWebhookDoc, "vendor 연동을 구현하지 않습니다"],
];

const forbidden = [
  ["component localStorage", files.component, "localStorage"],
  ["component sessionStorage", files.component, "sessionStorage"],
  ["client localStorage", files.client, "localStorage"],
  ["client sessionStorage", files.client, "sessionStorage"],
  ["client DELETE", files.client, 'method: "DELETE"'],
  ["external identity write path", files.client, "external-identities"],
  ["verified contact write path", files.client, "verified-contacts"],
];

const missing = required.filter(([, content, expected]) => !content.includes(expected));
const present = forbidden.filter(([, content, value]) => content.includes(value));

if (missing.length > 0 || present.length > 0) {
  console.error("[ops-console-contract] contract violations:");
  for (const [name, , expected] of missing) {
    console.error(`- missing ${name}: ${expected}`);
  }
  for (const [name, , value] of present) {
    console.error(`- forbidden ${name}: ${value}`);
  }
  process.exit(1);
}

console.log("[ops-console-contract] passed");
