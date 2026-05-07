import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const baseUrl = (__ENV.BASE_URL || "http://aquila-bank-backend:8080").replace(/\/$/, "");
const reportName = __ENV.K6_REPORT_NAME || "transaction-read-mixed-workload-100m";
const runId = __ENV.K6_RUN_ID || reportName;
const duration = __ENV.K6_MIXED_DURATION || __ENV.K6_DURATION || "30m";
const authToken = __ENV.K6_AUTH_TOKEN || "";
const hotAccountId = __ENV.K6_HOT_ACCOUNT_ID || "";
const coldAccountId = __ENV.K6_COLD_ACCOUNT_ID || "";
const hotFrom = __ENV.K6_HOT_FROM || "";
const hotTo = __ENV.K6_HOT_TO || "";
const coldFrom = __ENV.K6_COLD_FROM || "";
const coldTo = __ENV.K6_COLD_TO || "";
const writeSourceAccountId = __ENV.K6_WRITE_SOURCE_ACCOUNT_ID || "";
const writeTargetAccountId = __ENV.K6_WRITE_TARGET_ACCOUNT_ID || "";
const writeAmountMinor = Number(__ENV.K6_WRITE_AMOUNT_MINOR || "1");
const writeCurrencyCode = __ENV.K6_WRITE_CURRENCY_CODE || "KRW";
const readRate = Number(__ENV.K6_MIXED_READ_RATE || "12");
const writeVus = Number(__ENV.K6_MIXED_WRITE_VUS || "2");
const authRate = Number(__ENV.K6_MIXED_AUTH_RATE || "1");
const notificationRate = Number(__ENV.K6_MIXED_NOTIFICATION_RATE || "1");
const limit = Number(__ENV.K6_LIMIT || "50");
const sseTimeout = __ENV.K6_MIXED_SSE_TIMEOUT || "5s";
const maxRetryAfterSleepSeconds = Number(__ENV.K6_MAX_RETRY_AFTER_SLEEP_SECONDS || "1");

export const mixedReadCount = new Counter("aquila_mixed_read_count");
export const mixedReadDurationMs = new Trend("aquila_mixed_read_duration_ms", true);
export const mixedReadEdge429Rate = new Rate("aquila_mixed_read_edge_429_rate");
export const mixedReadBackend429Count = new Counter("aquila_mixed_read_backend_429_count");
export const mixedReadUnknown429Count = new Counter("aquila_mixed_read_unknown_429_count");
export const mixedWriteCount = new Counter("aquila_mixed_write_count");
export const mixedWriteDurationMs = new Trend("aquila_mixed_write_duration_ms", true);
export const mixedWrite429Rate = new Rate("aquila_mixed_write_429_rate");
export const mixedAuthCount = new Counter("aquila_mixed_auth_count");
export const mixedAuthDurationMs = new Trend("aquila_mixed_auth_duration_ms", true);
export const mixedNotificationCount = new Counter("aquila_mixed_notification_count");
export const mixedNotificationDurationMs = new Trend("aquila_mixed_notification_duration_ms", true);
export const mixedSseConnectCount = new Counter("aquila_mixed_sse_connect_count");
export const mixedFiveXxCount = new Counter("aquila_mixed_5xx_count");

export const options = {
  scenarios: {
    transaction_read: {
      executor: "constant-arrival-rate",
      rate: readRate,
      timeUnit: "1s",
      preAllocatedVUs: Number(__ENV.K6_MIXED_READ_PRE_ALLOCATED_VUS || "8"),
      maxVUs: Number(__ENV.K6_MIXED_READ_MAX_VUS || "32"),
      duration,
      exec: "transactionRead",
    },
    transfer_write: {
      executor: "constant-vus",
      vus: writeVus,
      duration,
      exec: "transferWrite",
    },
    auth_session: {
      executor: "constant-arrival-rate",
      rate: authRate,
      timeUnit: "1s",
      preAllocatedVUs: Number(__ENV.K6_MIXED_AUTH_PRE_ALLOCATED_VUS || "1"),
      maxVUs: Number(__ENV.K6_MIXED_AUTH_MAX_VUS || "4"),
      duration,
      exec: "authSession",
    },
    notification_poll: {
      executor: "constant-arrival-rate",
      rate: notificationRate,
      timeUnit: "1s",
      preAllocatedVUs: Number(__ENV.K6_MIXED_NOTIFICATION_PRE_ALLOCATED_VUS || "1"),
      maxVUs: Number(__ENV.K6_MIXED_NOTIFICATION_MAX_VUS || "4"),
      duration,
      exec: "notificationPoll",
    },
    sse_probe: {
      executor: "shared-iterations",
      vus: 1,
      iterations: Number(__ENV.K6_MIXED_SSE_ITERATIONS || "1"),
      startTime: __ENV.K6_MIXED_SSE_START_TIME || "5s",
      exec: "sseProbe",
    },
  },
  thresholds: {
    aquila_mixed_read_count: ["count>0"],
    aquila_mixed_write_count: ["count>0"],
    aquila_mixed_auth_count: ["count>0"],
    aquila_mixed_notification_count: ["count>0"],
    aquila_mixed_sse_connect_count: ["count>0"],
    checks: ["rate==1"],
    aquila_mixed_5xx_count: ["count==0"],
  },
};

function headers(accountId, subject) {
  const result = {
    "X-Run-Id": runId,
    "X-Subject": subject,
  };
  if (accountId) {
    result["X-Account-Id"] = String(accountId);
  }
  if (authToken) {
    result.Authorization = `Bearer ${authToken}`;
  }
  return result;
}

function requireInput(name, value) {
  if (!value) {
    throw new Error(`${name} is required`);
  }
}

function retryAfterSeconds(response) {
  const value = response.headers["Retry-After"];
  if (!value) {
    return 0;
  }
  const seconds = Number(value);
  if (!Number.isFinite(seconds) || seconds <= 0) {
    return 0;
  }
  return Math.min(seconds, maxRetryAfterSleepSeconds);
}

function recordFiveXx(response) {
  if (response.status >= 500 && response.status < 600) {
    mixedFiveXxCount.add(1);
  }
}

export function transactionRead() {
  const accountId = __ITER % 2 === 0 ? hotAccountId : coldAccountId;
  const from = __ITER % 2 === 0 ? hotFrom : coldFrom;
  const to = __ITER % 2 === 0 ? hotTo : coldTo;
  requireInput("K6_HOT_ACCOUNT_ID", hotAccountId);
  requireInput("K6_COLD_ACCOUNT_ID", coldAccountId);
  requireInput("K6_HOT_FROM", hotFrom);
  requireInput("K6_HOT_TO", hotTo);
  requireInput("K6_COLD_FROM", coldFrom);
  requireInput("K6_COLD_TO", coldTo);

  const response = http.get(
    `${baseUrl}/api/v1/transactions?accountId=${accountId}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&limit=${limit}`,
    { headers: headers(accountId, "k6-mixed-read") },
  );
  const edgeRejected = response.status === 429 && !response.headers["X-Aquila-429-Source"];
  const backendRejected = response.status === 429 && !!response.headers["X-Aquila-429-Source"];

  mixedReadCount.add(1);
  mixedReadDurationMs.add(response.timings.duration);
  mixedReadEdge429Rate.add(edgeRejected);
  if (backendRejected) {
    mixedReadBackend429Count.add(1);
  }
  if (response.status === 429 && !edgeRejected && !backendRejected) {
    mixedReadUnknown429Count.add(1);
  }
  recordFiveXx(response);

  check(response, {
    "transaction read status is 2xx or bounded 429": (r) =>
      (r.status >= 200 && r.status < 300) || r.status === 429,
  });

  const backoff = response.status === 429 ? retryAfterSeconds(response) : 0;
  if (backoff > 0) {
    sleep(backoff);
  }
}

export function transferWrite() {
  requireInput("K6_WRITE_SOURCE_ACCOUNT_ID", writeSourceAccountId);
  requireInput("K6_WRITE_TARGET_ACCOUNT_ID", writeTargetAccountId);

  const body = JSON.stringify({
    sourceAccountId: Number(writeSourceAccountId),
    targetAccountId: Number(writeTargetAccountId),
    amountMinor: writeAmountMinor,
    currencyCode: writeCurrencyCode,
    summary: "mixed workload write pressure",
  });
  const response = http.post(`${baseUrl}/api/v1/transfers`, body, {
    headers: {
      ...headers(writeSourceAccountId, "k6-mixed-write"),
      "Content-Type": "application/json",
      "Idempotency-Key": `mixed-${runId}-${__VU}-${__ITER}-${Date.now()}`,
    },
  });

  mixedWriteCount.add(1);
  mixedWriteDurationMs.add(response.timings.duration);
  mixedWrite429Rate.add(response.status === 429);
  recordFiveXx(response);

  check(response, {
    "transfer write status is 2xx or bounded 429": (r) =>
      (r.status >= 200 && r.status < 300) || r.status === 429,
  });

  const backoff = response.status === 429 ? retryAfterSeconds(response) : 0;
  if (backoff > 0) {
    sleep(backoff);
  }
}

export function authSession() {
  requireInput("K6_AUTH_TOKEN", authToken);
  const response = http.get(`${baseUrl}/api/v1/auth/sessions`, {
    headers: headers(hotAccountId, "k6-mixed-auth"),
  });
  mixedAuthCount.add(1);
  mixedAuthDurationMs.add(response.timings.duration);
  recordFiveXx(response);
  check(response, {
    "auth sessions status is 2xx or bounded 429": (r) =>
      (r.status >= 200 && r.status < 300) || r.status === 429,
  });
}

export function notificationPoll() {
  const response = http.get(`${baseUrl}/api/v1/notifications?limit=20`, {
    headers: headers(hotAccountId, "k6-mixed-notification"),
  });
  mixedNotificationCount.add(1);
  mixedNotificationDurationMs.add(response.timings.duration);
  recordFiveXx(response);
  check(response, {
    "notification list status is 2xx or bounded 429": (r) =>
      (r.status >= 200 && r.status < 300) || r.status === 429,
  });

  http.get(`${baseUrl}/api/v1/notifications/unread-count`, {
    headers: headers(hotAccountId, "k6-mixed-notification"),
  });
}

export function sseProbe() {
  const response = http.get(`${baseUrl}/api/v1/notifications/stream`, {
    headers: {
      ...headers(hotAccountId, "k6-mixed-sse"),
      Accept: "text/event-stream",
    },
    timeout: sseTimeout,
    responseType: "none",
  });
  mixedSseConnectCount.add(1);
  recordFiveXx(response);
  check(response, {
    "sse stream connect is accepted, overloaded, or timed probe": (r) =>
      r.status === 200 || r.status === 429 || r.status === 0,
  });
}

export function handleSummary(data) {
  const jsonPath = `/reports/${reportName}-summary.json`;
  const markdownPath = `/reports/${reportName}-summary.md`;
  return {
    [jsonPath]: JSON.stringify(data, null, 2),
    [markdownPath]: markdownSummary(data),
  };
}

function metricValue(data, metric, valueName) {
  return data.metrics?.[metric]?.values?.[valueName] ?? "n/a";
}

function markdownSummary(data) {
  return `# Transaction Read Mixed Workload 100M

- report: ${reportName}
- runId: ${runId}
- duration: ${duration}
- components: read,write,auth,notification,sse

| metric | value |
| --- | ---: |
| read count | ${metricValue(data, "aquila_mixed_read_count", "count")} |
| write count | ${metricValue(data, "aquila_mixed_write_count", "count")} |
| auth count | ${metricValue(data, "aquila_mixed_auth_count", "count")} |
| notification count | ${metricValue(data, "aquila_mixed_notification_count", "count")} |
| sse connect count | ${metricValue(data, "aquila_mixed_sse_connect_count", "count")} |
| read p95 ms | ${metricValue(data, "aquila_mixed_read_duration_ms", "p(95)")} |
| read p99.9 ms | ${metricValue(data, "aquila_mixed_read_duration_ms", "p(99.9)")} |
| read edge 429 rate | ${metricValue(data, "aquila_mixed_read_edge_429_rate", "rate")} |
| read backend 429 count | ${metricValue(data, "aquila_mixed_read_backend_429_count", "count")} |
| read unknown 429 count | ${metricValue(data, "aquila_mixed_read_unknown_429_count", "count")} |
| 5xx count | ${metricValue(data, "aquila_mixed_5xx_count", "count")} |
`;
}
