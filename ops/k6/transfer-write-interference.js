import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const baseUrl = (__ENV.BASE_URL || "http://aquila-bank-backend:8080").replace(/\/$/, "");
const reportName = __ENV.K6_REPORT_NAME || "transfer-write-interference";
const sourceAccountId = __ENV.K6_WRITE_SOURCE_ACCOUNT_ID || "";
const targetAccountId = __ENV.K6_WRITE_TARGET_ACCOUNT_ID || "";
const amountMinor = Number(__ENV.K6_WRITE_AMOUNT_MINOR || "1");
const currencyCode = __ENV.K6_WRITE_CURRENCY_CODE || "KRW";
const vus = Number(__ENV.K6_WRITE_VUS || "2");
const duration = __ENV.K6_WRITE_DURATION || __ENV.K6_DURATION || "1m";
const subject = __ENV.K6_WRITE_SUBJECT || "transaction-interference";
const write429RateThreshold = __ENV.K6_WRITE_429_RATE_THRESHOLD || "0.05";
const writeSuccessRateThreshold = __ENV.K6_WRITE_SUCCESS_RATE_THRESHOLD || "0.95";
const maxRetryAfterSleepSeconds = Number(__ENV.K6_MAX_RETRY_AFTER_SLEEP_SECONDS || "1");

export const transferWrite429Rate = new Rate("aquila_transfer_write_429_rate");
export const transferWriteSuccessRate = new Rate("aquila_transfer_write_success_rate");
export const transferWriteDurationMs = new Trend("aquila_transfer_write_duration_ms", true);
export const transferWriteCount = new Counter("aquila_transfer_write_count");

export const options = {
  scenarios: {
    transfer_write: {
      executor: "constant-vus",
      vus,
      duration,
    },
  },
  thresholds: {
    aquila_transfer_write_429_rate: [`rate<${write429RateThreshold}`],
    aquila_transfer_write_success_rate: [`rate>=${writeSuccessRateThreshold}`],
  },
};

function requireInput(name, value) {
  if (!value) {
    throw new Error(`${name} is required`);
  }
}

function idempotencyKey() {
  return `interference-${sourceAccountId}-${__VU}-${__ITER}-${Date.now()}`;
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

export default function () {
  requireInput("K6_WRITE_SOURCE_ACCOUNT_ID", sourceAccountId);
  requireInput("K6_WRITE_TARGET_ACCOUNT_ID", targetAccountId);

  const body = JSON.stringify({
    sourceAccountId: Number(sourceAccountId),
    targetAccountId: Number(targetAccountId),
    amountMinor,
    currencyCode,
    summary: "read-write interference",
  });
  const response = http.post(`${baseUrl}/api/v1/transfers`, body, {
    headers: {
      "Content-Type": "application/json",
      "X-Account-Id": sourceAccountId,
      "X-Subject": subject,
      "Idempotency-Key": idempotencyKey(),
    },
  });

  const rejected = response.status === 429;
  const succeeded = response.status >= 200 && response.status < 300;
  transferWrite429Rate.add(rejected);
  transferWriteSuccessRate.add(succeeded);
  transferWriteDurationMs.add(response.timings.duration);
  transferWriteCount.add(1);

  check(response, {
    "transfer write status is 2xx or bounded 429": (r) =>
      (r.status >= 200 && r.status < 300) || r.status === 429,
  });

  const backoff = rejected ? retryAfterSeconds(response) : 0;
  if (backoff > 0) {
    sleep(backoff);
  }
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
  return `# Transfer Write Interference

- report: ${reportName}
- vus: ${vus}
- duration: ${duration}
- sourceAccountId: ${sourceAccountId}
- targetAccountId: ${targetAccountId}

| metric | value |
| --- | ---: |
| http requests | ${metricValue(data, "http_reqs", "count")} |
| http failed rate | ${metricValue(data, "http_req_failed", "rate")} |
| write count | ${metricValue(data, "aquila_transfer_write_count", "count")} |
| write success rate | ${metricValue(data, "aquila_transfer_write_success_rate", "rate")} |
| write 429 rate | ${metricValue(data, "aquila_transfer_write_429_rate", "rate")} |
| write duration p95 | ${metricValue(data, "aquila_transfer_write_duration_ms", "p(95)")} |
`;
}
