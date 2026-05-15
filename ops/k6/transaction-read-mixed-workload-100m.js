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
const archiveAccountId = __ENV.K6_ARCHIVE_ACCOUNT_ID || coldAccountId;
const hotFrom = __ENV.K6_HOT_FROM || "";
const hotTo = __ENV.K6_HOT_TO || "";
const coldFrom = __ENV.K6_COLD_FROM || "";
const coldTo = __ENV.K6_COLD_TO || "";
const archiveFrom = __ENV.K6_ARCHIVE_FROM || coldFrom;
const archiveTo = __ENV.K6_ARCHIVE_TO || coldTo;
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
const writeAcceptedRatioThreshold = __ENV.K6_MIXED_WRITE_ACCEPTED_RATIO_THRESHOLD || "0.80";
const IDEMPOTENCY_KEY_MAX_LENGTH = 80;

export const mixedReadCount = new Counter("aquila_mixed_read_count");
export const mixedReadDurationMs = new Trend("aquila_mixed_read_duration_ms", true);
export const mixedReadEdge429Rate = new Rate("aquila_mixed_read_edge_429_rate");
export const mixedReadBackend429Count = new Counter("aquila_mixed_read_backend_429_count");
export const mixedReadUnknown429Count = new Counter("aquila_mixed_read_unknown_429_count");
export const mixedReadHotCount = new Counter("aquila_mixed_read_hot_count");
export const mixedReadHotDurationMs = new Trend("aquila_mixed_read_hot_duration_ms", true);
export const mixedReadHotEdge429Rate = new Rate("aquila_mixed_read_hot_edge_429_rate");
export const mixedReadHotBackend429Count = new Counter("aquila_mixed_read_hot_backend_429_count");
export const mixedReadHotUnknown429Count = new Counter("aquila_mixed_read_hot_unknown_429_count");
export const mixedReadColdCount = new Counter("aquila_mixed_read_cold_count");
export const mixedReadColdDurationMs = new Trend("aquila_mixed_read_cold_duration_ms", true);
export const mixedReadColdEdge429Rate = new Rate("aquila_mixed_read_cold_edge_429_rate");
export const mixedReadColdBackend429Count = new Counter("aquila_mixed_read_cold_backend_429_count");
export const mixedReadColdUnknown429Count = new Counter("aquila_mixed_read_cold_unknown_429_count");
export const mixedReadArchiveCount = new Counter("aquila_mixed_read_archive_count");
export const mixedReadArchiveDurationMs = new Trend("aquila_mixed_read_archive_duration_ms", true);
export const mixedReadArchiveEdge429Rate = new Rate("aquila_mixed_read_archive_edge_429_rate");
export const mixedReadArchiveBackend429Count = new Counter("aquila_mixed_read_archive_backend_429_count");
export const mixedReadArchiveUnknown429Count = new Counter("aquila_mixed_read_archive_unknown_429_count");
export const mixedWriteCount = new Counter("aquila_mixed_write_count");
export const mixedWriteDurationMs = new Trend("aquila_mixed_write_duration_ms", true);
export const mixedWrite2xxCount = new Counter("aquila_mixed_write_2xx_count");
export const mixedWrite429Count = new Counter("aquila_mixed_write_429_count");
export const mixedWriteEdge429Count = new Counter("aquila_mixed_write_edge_429_count");
export const mixedWriteBackend429Count = new Counter("aquila_mixed_write_backend_429_count");
export const mixedWriteUnknown429Count = new Counter("aquila_mixed_write_unknown_429_count");
export const mixedWriteUnexpectedStatusCount = new Counter("aquila_mixed_write_unexpected_status_count");
export const mixedWrite401Count = new Counter("aquila_mixed_write_401_count");
export const mixedWrite403Count = new Counter("aquila_mixed_write_403_count");
export const mixedWrite409Count = new Counter("aquila_mixed_write_409_count");
export const mixedWrite422Count = new Counter("aquila_mixed_write_422_count");
export const mixedWriteOtherUnexpectedCount = new Counter("aquila_mixed_write_other_unexpected_count");
export const mixedWrite429Rate = new Rate("aquila_mixed_write_429_rate");
export const mixedWriteAcceptedRatio = new Rate("aquila_mixed_write_accepted_ratio");
export const mixedWriteIdempotencyReplayCount = new Counter(
  "aquila_mixed_write_idempotency_replay_count",
);
export const mixedWriteIdempotencyConflictCount = new Counter(
  "aquila_mixed_write_idempotency_conflict_count",
);
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
    aquila_mixed_read_hot_count: ["count>0"],
    aquila_mixed_read_cold_count: ["count>0"],
    aquila_mixed_read_archive_count: ["count>0"],
    aquila_mixed_write_count: ["count>0"],
    aquila_mixed_auth_count: ["count>0"],
    aquila_mixed_notification_count: ["count>0"],
    aquila_mixed_sse_connect_count: ["count>0"],
    checks: ["rate==1"],
    aquila_mixed_5xx_count: ["count==0"],
    aquila_mixed_write_accepted_ratio: [`rate>=${writeAcceptedRatioThreshold}`],
    aquila_mixed_write_idempotency_replay_count: ["count>0"],
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

// API의 idempotency key 80자 계약을 넘기면 write 2xx가 0으로 왜곡됩니다.
function stableHashSegment(value) {
  let hash = 2166136261;
  const source = String(value || "");
  for (let index = 0; index < source.length; index += 1) {
    hash ^= source.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(36);
}

function idempotencySegment(value) {
  const segment = String(value || "run").replace(/[^A-Za-z0-9_-]/g, "-");
  return segment || "run";
}

function mixedWriteIdempotencyKey(vu, iteration) {
  const suffix = [
    stableHashSegment(runId),
    Number(vu || 0).toString(36),
    Number(iteration || 0).toString(36),
    Date.now().toString(36),
  ].join("-");
  const prefixBudget = Math.max(1, IDEMPOTENCY_KEY_MAX_LENGTH - "mixed--".length - suffix.length);
  const prefix = idempotencySegment(runId).slice(0, prefixBudget);
  const key = `mixed-${prefix}-${suffix}`;
  return key.length <= IDEMPOTENCY_KEY_MAX_LENGTH
    ? key
    : key.slice(0, IDEMPOTENCY_KEY_MAX_LENGTH);
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

function responseHeader(response, name) {
  return response.headers[name] || response.headers[name.toLowerCase()] || "";
}

function rejectedSource(response) {
  if (response.status !== 429) {
    return "none";
  }
  if (responseHeader(response, "X-Aquila-429-Source")) {
    return "backend";
  }
  const edgeSource = responseHeader(response, "X-Aquila-Reject-Source");
  const rateLimitScope = responseHeader(response, "X-RateLimit-Scope");
  if (edgeSource === "nginx-edge" || rateLimitScope === "nginx-edge") {
    return "edge";
  }
  return "unknown";
}

function recordFiveXx(response) {
  if (response.status >= 500 && response.status < 600) {
    mixedFiveXxCount.add(1);
  }
}

function readTarget(iteration) {
  const bucketIndex = iteration % 3;
  if (bucketIndex === 0) {
    return {
      bucket: "hot",
      accountId: hotAccountId,
      from: hotFrom,
      to: hotTo,
      path: "/api/v1/transactions",
    };
  }
  if (bucketIndex === 1) {
    return {
      bucket: "cold",
      accountId: coldAccountId,
      from: coldFrom,
      to: coldTo,
      path: "/api/v1/transactions",
    };
  }
  return {
    bucket: "archive",
    accountId: archiveAccountId,
    from: archiveFrom,
    to: archiveTo,
    path: "/api/v1/transactions/archive",
  };
}

function recordBucketRead(bucket, response, edgeRejected, backendRejected) {
  const unknownRejected = response.status === 429 && !edgeRejected && !backendRejected;
  if (bucket === "hot") {
    mixedReadHotCount.add(1);
    mixedReadHotDurationMs.add(response.timings.duration);
    mixedReadHotEdge429Rate.add(edgeRejected);
    if (backendRejected) {
      mixedReadHotBackend429Count.add(1);
    }
    if (unknownRejected) {
      mixedReadHotUnknown429Count.add(1);
    }
    return;
  }
  if (bucket === "cold") {
    mixedReadColdCount.add(1);
    mixedReadColdDurationMs.add(response.timings.duration);
    mixedReadColdEdge429Rate.add(edgeRejected);
    if (backendRejected) {
      mixedReadColdBackend429Count.add(1);
    }
    if (unknownRejected) {
      mixedReadColdUnknown429Count.add(1);
    }
    return;
  }
  mixedReadArchiveCount.add(1);
  mixedReadArchiveDurationMs.add(response.timings.duration);
  mixedReadArchiveEdge429Rate.add(edgeRejected);
  if (backendRejected) {
    mixedReadArchiveBackend429Count.add(1);
  }
  if (unknownRejected) {
    mixedReadArchiveUnknown429Count.add(1);
  }
}

export function transactionRead() {
  const target = readTarget(__ITER);
  requireInput("K6_HOT_ACCOUNT_ID", hotAccountId);
  requireInput("K6_COLD_ACCOUNT_ID", coldAccountId);
  requireInput("K6_ARCHIVE_ACCOUNT_ID", archiveAccountId);
  requireInput("K6_HOT_FROM", hotFrom);
  requireInput("K6_HOT_TO", hotTo);
  requireInput("K6_COLD_FROM", coldFrom);
  requireInput("K6_COLD_TO", coldTo);
  requireInput("K6_ARCHIVE_FROM", archiveFrom);
  requireInput("K6_ARCHIVE_TO", archiveTo);

  const response = http.get(
    `${baseUrl}${target.path}?accountId=${target.accountId}&from=${encodeURIComponent(target.from)}&to=${encodeURIComponent(target.to)}&limit=${limit}`,
    { headers: headers(target.accountId, `k6-mixed-read-${target.bucket}`) },
  );
  const edgeRejected = response.status === 429 && !response.headers["X-Aquila-429-Source"];
  const backendRejected = response.status === 429 && !!response.headers["X-Aquila-429-Source"];

  mixedReadCount.add(1);
  mixedReadDurationMs.add(response.timings.duration);
  mixedReadEdge429Rate.add(edgeRejected);
  recordBucketRead(target.bucket, response, edgeRejected, backendRejected);
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
  const idempotencyKey = mixedWriteIdempotencyKey(__VU, __ITER);
  const writeHeaders = {
    ...headers(writeSourceAccountId, "k6-mixed-write"),
    "Content-Type": "application/json",
    "Idempotency-Key": idempotencyKey,
  };
  const response = http.post(`${baseUrl}/api/v1/transfers`, body, {
    headers: writeHeaders,
  });

  const accepted = response.status >= 200 && response.status < 300;
  const boundedRejected = response.status === 429;
  const source = rejectedSource(response);
  mixedWriteCount.add(1);
  mixedWriteDurationMs.add(response.timings.duration);
  mixedWriteAcceptedRatio.add(accepted);
  if (accepted) {
    mixedWrite2xxCount.add(1);
  }
  if (boundedRejected) {
    mixedWrite429Count.add(1);
    if (source === "edge") {
      mixedWriteEdge429Count.add(1);
    } else if (source === "backend") {
      mixedWriteBackend429Count.add(1);
    } else {
      mixedWriteUnknown429Count.add(1);
    }
  }
  if (!accepted && !boundedRejected) {
    mixedWriteUnexpectedStatusCount.add(1);
    if (response.status === 401) {
      mixedWrite401Count.add(1);
    } else if (response.status === 403) {
      mixedWrite403Count.add(1);
    } else if (response.status === 409) {
      mixedWrite409Count.add(1);
    } else if (response.status === 422) {
      mixedWrite422Count.add(1);
    } else {
      mixedWriteOtherUnexpectedCount.add(1);
    }
  }
  mixedWrite429Rate.add(boundedRejected);
  recordFiveXx(response);

  if (accepted) {
    const replayResponse = http.post(`${baseUrl}/api/v1/transfers`, body, {
      headers: writeHeaders,
    });
    if (replayResponse.status >= 200 && replayResponse.status < 300) {
      mixedWriteIdempotencyReplayCount.add(1);
    }
    if (replayResponse.status === 409) {
      mixedWriteIdempotencyConflictCount.add(1);
    }
    recordFiveXx(replayResponse);
  }

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
- write accepted ratio threshold: ${writeAcceptedRatioThreshold}

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
| hot read p95 ms | ${metricValue(data, "aquila_mixed_read_hot_duration_ms", "p(95)")} |
| cold read p95 ms | ${metricValue(data, "aquila_mixed_read_cold_duration_ms", "p(95)")} |
| archive read p95 ms | ${metricValue(data, "aquila_mixed_read_archive_duration_ms", "p(95)")} |
| write 2xx count | ${metricValue(data, "aquila_mixed_write_2xx_count", "count")} |
| write 429 count | ${metricValue(data, "aquila_mixed_write_429_count", "count")} |
| write accepted ratio | ${metricValue(data, "aquila_mixed_write_accepted_ratio", "rate")} |
| write idempotency replay count | ${metricValue(data, "aquila_mixed_write_idempotency_replay_count", "count")} |
| write idempotency conflict count | ${metricValue(data, "aquila_mixed_write_idempotency_conflict_count", "count")} |
| write edge 429 count | ${metricValue(data, "aquila_mixed_write_edge_429_count", "count")} |
| write backend 429 count | ${metricValue(data, "aquila_mixed_write_backend_429_count", "count")} |
| write unknown 429 count | ${metricValue(data, "aquila_mixed_write_unknown_429_count", "count")} |
| write unexpected status count | ${metricValue(data, "aquila_mixed_write_unexpected_status_count", "count")} |
| write 401 count | ${metricValue(data, "aquila_mixed_write_401_count", "count")} |
| write 403 count | ${metricValue(data, "aquila_mixed_write_403_count", "count")} |
| write 409 count | ${metricValue(data, "aquila_mixed_write_409_count", "count")} |
| write 422 count | ${metricValue(data, "aquila_mixed_write_422_count", "count")} |
| write other unexpected count | ${metricValue(data, "aquila_mixed_write_other_unexpected_count", "count")} |
| 5xx count | ${metricValue(data, "aquila_mixed_5xx_count", "count")} |
`;
}
