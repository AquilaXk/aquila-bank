import http from "k6/http";
import encoding from "k6/encoding";
import {check, fail, sleep} from "k6";
import {Counter, Rate, Trend} from "k6/metrics";
import exec from "k6/execution";

function booleanEnv(value) {
  return ["1", "true", "yes", "on"].includes(String(value || "").toLowerCase());
}

function nonNegativeNumberEnv(value, fallback) {
  const result = Number(value || fallback);
  if (!Number.isFinite(result) || result < 0) {
    return fallback;
  }
  return result;
}

const baseUrl = (__ENV.BASE_URL || "http://aquila-bank-backend:8080").replace(/\/$/, "");
const hotAccountId = __ENV.K6_HOT_ACCOUNT_ID || "";
const hotFrom = __ENV.K6_HOT_FROM || "";
const hotTo = __ENV.K6_HOT_TO || "";
const coldAccountId = __ENV.K6_COLD_ACCOUNT_ID || "";
const coldFrom = __ENV.K6_COLD_FROM || "";
const coldTo = __ENV.K6_COLD_TO || "";
const hotDeepCursorBookedAt = __ENV.K6_HOT_DEEP_CURSOR_BOOKED_AT || "2026-04-15T00:00:00Z";
const hotDeepCursorId = __ENV.K6_HOT_DEEP_CURSOR_ID || "9223372036854775807";
const coldDeepCursorBookedAt = __ENV.K6_COLD_DEEP_CURSOR_BOOKED_AT || "2026-01-15T00:00:00Z";
const coldDeepCursorId = __ENV.K6_COLD_DEEP_CURSOR_ID || "9223372036854775807";
const authToken = __ENV.K6_AUTH_TOKEN || "";
const limit = Number(__ENV.K6_LIMIT || "50");
const vus = Number(__ENV.AQUILA_K6_VUS || "8");
const duration = __ENV.AQUILA_K6_DURATION || "1m";
const scenarioMode = __ENV.AQUILA_K6_SCENARIO_MODE || "constant-vus";
const rate = Number(__ENV.AQUILA_K6_RATE || "8");
const timeUnit = __ENV.AQUILA_K6_TIME_UNIT || "1s";
const preAllocatedVUs = Number(__ENV.AQUILA_K6_PRE_ALLOCATED_VUS || String(vus));
const maxVUs = Number(__ENV.AQUILA_K6_MAX_VUS || String(preAllocatedVUs));
const burstRate = Number(__ENV.AQUILA_K6_BURST_RATE || "16");
const burstDuration = __ENV.AQUILA_K6_BURST_DURATION || "20s";
const warmupDuration = __ENV.AQUILA_K6_WARMUP_DURATION || "10s";
const hotP95ThresholdMs = Number(__ENV.K6_HOT_P95_THRESHOLD_MS || "350");
const coldP95ThresholdMs = Number(__ENV.K6_COLD_P95_THRESHOLD_MS || "750");
const hotP99ThresholdMs = Number(__ENV.K6_HOT_P99_THRESHOLD_MS || "750");
const coldP99ThresholdMs = Number(__ENV.K6_COLD_P99_THRESHOLD_MS || "1500");
const hotP999ThresholdMs = Number(__ENV.K6_HOT_P999_THRESHOLD_MS || "1200");
const coldP999ThresholdMs = Number(__ENV.K6_COLD_P999_THRESHOLD_MS || "2500");
const hotMaxThresholdMs = Number(__ENV.K6_HOT_MAX_THRESHOLD_MS || "3000");
const coldMaxThresholdMs = Number(__ENV.K6_COLD_MAX_THRESHOLD_MS || "5000");
const hotDeepP95ThresholdMs = Number(__ENV.K6_HOT_DEEP_P95_THRESHOLD_MS || String(hotP95ThresholdMs));
const coldDeepP95ThresholdMs = Number(__ENV.K6_COLD_DEEP_P95_THRESHOLD_MS || String(coldP95ThresholdMs));
const hotDeepP99ThresholdMs = Number(__ENV.K6_HOT_DEEP_P99_THRESHOLD_MS || String(hotP99ThresholdMs));
const coldDeepP99ThresholdMs = Number(__ENV.K6_COLD_DEEP_P99_THRESHOLD_MS || String(coldP99ThresholdMs));
const hotDeepP999ThresholdMs = Number(__ENV.K6_HOT_DEEP_P999_THRESHOLD_MS || String(hotP999ThresholdMs));
const coldDeepP999ThresholdMs = Number(__ENV.K6_COLD_DEEP_P999_THRESHOLD_MS || String(coldP999ThresholdMs));
const hotDeepMaxThresholdMs = Number(__ENV.K6_HOT_DEEP_MAX_THRESHOLD_MS || String(hotMaxThresholdMs));
const coldDeepMaxThresholdMs = Number(__ENV.K6_COLD_DEEP_MAX_THRESHOLD_MS || String(coldMaxThresholdMs));
const failedRate = Number(__ENV.K6_HTTP_FAILED_RATE || "0.01");
const reportName = __ENV.K6_REPORT_NAME || "transaction-100m";
const runId = __ENV.K6_RUN_ID || reportName;
const observabilityMode = __ENV.K6_OBSERVABILITY_MODE || "prometheus";
const overloadMode = booleanEnv(__ENV.K6_OVERLOAD_MODE);
const overload429RateThreshold = nonNegativeNumberEnv(__ENV.K6_OVERLOAD_429_RATE_THRESHOLD, 0.015);
const burst429RateThreshold = nonNegativeNumberEnv(__ENV.K6_BURST_429_RATE_THRESHOLD, 0.10);
const overload503RateThreshold = nonNegativeNumberEnv(__ENV.K6_OVERLOAD_503_RATE_THRESHOLD, 0);
const maxRetryAfterSleepSeconds = nonNegativeNumberEnv(__ENV.K6_MAX_RETRY_AFTER_SLEEP_SECONDS, 1);
const maxRetryAfterSleepMs = nonNegativeNumberEnv(
  __ENV.K6_MAX_RETRY_AFTER_SLEEP_MS,
  maxRetryAfterSleepSeconds * 1000,
);
const effectiveOverload429RateThreshold =
  scenarioMode === "burst" ? burst429RateThreshold : overload429RateThreshold;
const httpFailedRateThreshold = overloadMode ? "disabled in overload mode" : failedRate;
const overload429RateThresholdText = overloadMode
  ? overload429RateThreshold
  : "disabled outside overload mode";
const burst429RateThresholdText = overloadMode
  ? burst429RateThreshold
  : "disabled outside overload mode";
const overload503RateThresholdText = overloadMode
  ? overload503RateThreshold
  : "disabled outside overload mode";

const hotFirst = new Trend("aquila_transaction_hot_first_ms", true);
const hotCursor = new Trend("aquila_transaction_hot_cursor_ms", true);
const hotDeepCursor = new Trend("aquila_transaction_hot_deep_cursor_ms", true);
const coldFirst = new Trend("aquila_transaction_cold_first_ms", true);
const coldCursor = new Trend("aquila_transaction_cold_cursor_ms", true);
const coldDeepCursor = new Trend("aquila_transaction_cold_deep_cursor_ms", true);
const transaction429Rate = new Rate("aquila_transaction_429_rate");
const transaction503Rate = new Rate("aquila_transaction_503_rate");
const transaction503Count = new Counter("aquila_transaction_503_count");
const retryAfterSleep = new Trend("aquila_transaction_retry_after_sleep_ms", true);
const retryAfterCount = new Counter("aquila_transaction_retry_after_count");

function thresholds() {
  const result = {
    checks: ["rate>0.99"],
    aquila_transaction_hot_first_ms: [
      `p(95)<${hotP95ThresholdMs}`,
      `p(99)<${hotP99ThresholdMs}`,
      `p(99.9)<${hotP999ThresholdMs}`,
      `max<${hotMaxThresholdMs}`,
    ],
    aquila_transaction_hot_cursor_ms: [
      `p(95)<${hotP95ThresholdMs}`,
      `p(99)<${hotP99ThresholdMs}`,
      `p(99.9)<${hotP999ThresholdMs}`,
      `max<${hotMaxThresholdMs}`,
    ],
    aquila_transaction_hot_deep_cursor_ms: [
      `p(95)<${hotDeepP95ThresholdMs}`,
      `p(99)<${hotDeepP99ThresholdMs}`,
      `p(99.9)<${hotDeepP999ThresholdMs}`,
      `max<${hotDeepMaxThresholdMs}`,
    ],
    aquila_transaction_cold_first_ms: [
      `p(95)<${coldP95ThresholdMs}`,
      `p(99)<${coldP99ThresholdMs}`,
      `p(99.9)<${coldP999ThresholdMs}`,
      `max<${coldMaxThresholdMs}`,
    ],
    aquila_transaction_cold_cursor_ms: [
      `p(95)<${coldP95ThresholdMs}`,
      `p(99)<${coldP99ThresholdMs}`,
      `p(99.9)<${coldP999ThresholdMs}`,
      `max<${coldMaxThresholdMs}`,
    ],
    aquila_transaction_cold_deep_cursor_ms: [
      `p(95)<${coldDeepP95ThresholdMs}`,
      `p(99)<${coldDeepP99ThresholdMs}`,
      `p(99.9)<${coldDeepP999ThresholdMs}`,
      `max<${coldDeepMaxThresholdMs}`,
    ],
  };
  if (!overloadMode) {
    result.http_req_failed = [`rate<${failedRate}`];
  } else {
    result.aquila_transaction_429_rate = [`rate<${effectiveOverload429RateThreshold}`];
    result.aquila_transaction_503_rate = [`rate<=${overload503RateThreshold}`];
    result.aquila_transaction_503_count = ["count<1"];
  }
  return result;
}

function warmupEnabled() {
  return !["0s", "0m", "0h"].includes(warmupDuration);
}

function withMeasuredStart(scenario) {
  if (!warmupEnabled()) {
    return scenario;
  }
  return {
    ...scenario,
    startTime: warmupDuration,
  };
}

function warmupScenario() {
  if (!warmupEnabled()) {
    return {};
  }
  return {
    transaction_read_100m_warmup: {
      executor: "constant-vus",
      vus: 1,
      duration: warmupDuration,
      gracefulStop: "0s",
      tags: {
        phase: "warmup",
      },
    },
  };
}

function measuredScenarioTags() {
  return {
    phase: "measured",
  };
}

function scenarios() {
  const result = warmupScenario();
  if (scenarioMode === "constant-arrival-rate") {
    result.transaction_read_100m_arrival = withMeasuredStart({
      executor: "constant-arrival-rate",
      rate,
      timeUnit,
      duration,
      preAllocatedVUs,
      maxVUs,
      tags: measuredScenarioTags(),
    });
    return result;
  }
  if (scenarioMode === "burst") {
    result.burst_admission = withMeasuredStart({
      executor: "constant-arrival-rate",
      rate: burstRate,
      timeUnit: "1s",
      duration: burstDuration,
      preAllocatedVUs,
      maxVUs,
      tags: measuredScenarioTags(),
    });
    return result;
  }
  result.transaction_read_100m = withMeasuredStart({
    executor: "constant-vus",
    vus,
    duration,
    tags: measuredScenarioTags(),
  });
  return result;
}

export const options = {
  scenarios: scenarios(),
  thresholds: thresholds(),
  summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)", "p(99.9)"],
  tags: {
    service: "aquila-bank",
    workload: "transaction-read-100m",
    run_id: runId,
  },
};

function requireEnv(name, value) {
  if (!value) {
    fail(`${name} is required`);
  }
}

function headers(accountId) {
  const result = {
    "X-Account-Id": String(accountId),
    "X-Subject": "k6-transaction-100m",
    "X-K6-Run-Id": runId,
  };
  if (authToken) {
    result.Authorization = `Bearer ${authToken}`;
  }
  return result;
}

function queryString(params) {
  return Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && value !== "")
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join("&");
}

function parseJson(response, shape) {
  try {
    return response.json();
  } catch (error) {
    fail(`${shape} returned non-json response: ${error}`);
  }
}

function record(shape, durationMs) {
  if (exec.scenario.name.endsWith("_warmup")) {
    return;
  }
  if (shape === "hot_first") {
    hotFirst.add(durationMs);
  } else if (shape === "hot_cursor") {
    hotCursor.add(durationMs);
  } else if (shape === "hot_deep_cursor") {
    hotDeepCursor.add(durationMs);
  } else if (shape === "cold_first") {
    coldFirst.add(durationMs);
  } else if (shape === "cold_cursor") {
    coldCursor.add(durationMs);
  } else if (shape === "cold_deep_cursor") {
    coldDeepCursor.add(durationMs);
  }
}

function header(response, name) {
  const target = name.toLowerCase();
  for (const [key, value] of Object.entries(response.headers || {})) {
    if (key.toLowerCase() === target) {
      return value;
    }
  }
  return "";
}

function numericHeader(response, name) {
  const value = Number(header(response, name));
  return Number.isFinite(value) && value >= 0 ? value : null;
}

function sleepAfter429(response) {
  if (maxRetryAfterSleepMs <= 0) {
    return;
  }
  const retryAfterMillis = numericHeader(response, "X-RateLimit-Retry-After-Millis");
  const retryAfterSeconds = numericHeader(response, "Retry-After");
  const retryJitterMillis = numericHeader(response, "X-RateLimit-Retry-Jitter-Millis") || 0;
  const baseSleepMs =
    retryAfterMillis !== null
      ? retryAfterMillis
      : retryAfterSeconds !== null
        ? retryAfterSeconds * 1000
        : 0;
  const jitterMs = retryJitterMillis > 0 ? Math.random() * retryJitterMillis : 0;
  const sleepMs = Math.min(baseSleepMs + jitterMs, maxRetryAfterSleepMs);
  if (sleepMs <= 0) {
    return;
  }
  retryAfterCount.add(1);
  retryAfterSleep.add(sleepMs);
  sleep(sleepMs / 1000);
}

function encodeCursor(bookedAt, id) {
  const payload = `${bookedAt}|${id}`;
  return encoding.b64encode(payload, "rawurl");
}

function requestPage(shape, path, accountId, from, to, cursor) {
  const query = queryString({
    accountId,
    from,
    to,
    limit,
    cursor,
  });
  const response = http.get(`${baseUrl}${path}?${query}`, {
    headers: headers(accountId),
    tags: {
      name: shape,
      query_shape: shape,
    },
  });

  const is429 = response.status === 429;
  const is503 = response.status === 503;
  const measured = !exec.scenario.name.endsWith("_warmup");
  if (measured) {
    transaction429Rate.add(is429);
    transaction503Rate.add(is503);
  }
  if (is503 && measured) {
    transaction503Count.add(1);
  }
  if (is429 && overloadMode) {
    // 429는 admission guard의 정상 보호 신호라 overload mode에서만 예외 없이 집계합니다.
    check(response, {
      [`${shape} overload returned 429`]: (item) => item.status === 429,
    });
    sleepAfter429(response);
    return null;
  }

  record(shape, response.timings.duration);

  const ok = check(response, {
    [`${shape} status is 2xx`]: (item) => item.status >= 200 && item.status < 300,
  });
  if (!ok) {
    fail(`${shape} returned HTTP ${response.status}`);
  }

  const body = parseJson(response, shape);
  const hasItems = check(body, {
    [`${shape} has items`]: (item) => Array.isArray(item.items) && item.items.length > 0,
  });
  if (!hasItems) {
    fail(`${shape} returned no items`);
  }
  return body;
}

export default function () {
  requireEnv("K6_HOT_ACCOUNT_ID", hotAccountId);
  requireEnv("K6_HOT_FROM", hotFrom);
  requireEnv("K6_HOT_TO", hotTo);
  requireEnv("K6_COLD_ACCOUNT_ID", coldAccountId);
  requireEnv("K6_COLD_FROM", coldFrom);
  requireEnv("K6_COLD_TO", coldTo);

  const hotFirstBody = requestPage(
    "hot_first",
    "/api/v1/transactions",
    hotAccountId,
    hotFrom,
    hotTo,
    "",
  );
  if (!hotFirstBody) {
    return;
  }
  if (!hotFirstBody.nextCursor) {
    fail("hot_first did not return nextCursor");
  }
  const hotCursorBody = requestPage(
    "hot_cursor",
    "/api/v1/transactions",
    hotAccountId,
    hotFrom,
    hotTo,
    hotFirstBody.nextCursor,
  );
  if (!hotCursorBody) {
    return;
  }
  const hotDeepCursorBody = requestPage(
    "hot_deep_cursor",
    "/api/v1/transactions",
    hotAccountId,
    hotFrom,
    hotTo,
    encodeCursor(hotDeepCursorBookedAt, hotDeepCursorId),
  );
  if (!hotDeepCursorBody) {
    return;
  }

  const coldFirstBody = requestPage(
    "cold_first",
    "/api/v1/transactions/archive",
    coldAccountId,
    coldFrom,
    coldTo,
    "",
  );
  if (!coldFirstBody) {
    return;
  }
  if (!coldFirstBody.nextCursor) {
    fail("cold_first did not return nextCursor");
  }
  const coldCursorBody = requestPage(
    "cold_cursor",
    "/api/v1/transactions/archive",
    coldAccountId,
    coldFrom,
    coldTo,
    coldFirstBody.nextCursor,
  );
  if (!coldCursorBody) {
    return;
  }
  const coldDeepCursorBody = requestPage(
    "cold_deep_cursor",
    "/api/v1/transactions/archive",
    coldAccountId,
    coldFrom,
    coldTo,
    encodeCursor(coldDeepCursorBookedAt, coldDeepCursorId),
  );
  if (!coldDeepCursorBody) {
    return;
  }
}

function normalizedMetricKey(value) {
  return String(value).replace(/p\((\d+)\.0+\)/, "p($1)");
}

function metric(data, name, valueName) {
  const item = data.metrics[name];
  if (!item || !item.values || item.values[valueName] === undefined) {
    const expectedKey = normalizedMetricKey(valueName);
    const fallbackKey = Object.keys((item && item.values) || {})
      .find((key) => normalizedMetricKey(key) === expectedKey);
    return fallbackKey ? item.values[fallbackKey] : "n/a";
  }
  return item.values[valueName];
}

function observabilityNote() {
  if (observabilityMode === "prometheus") {
    return "- observability mode가 `prometheus`이면 Prometheus remote write와 summary 파일을 함께 남깁니다.";
  }
  return "- observability mode가 `summary-only`이면 Prometheus remote write 없이 summary 파일만 남깁니다.";
}

function markdownSummary(data) {
  return `# k6 Transaction 100m Load Test

## Environment

- baseUrl: ${baseUrl}
- run id: ${runId}
- vus: ${vus}
- duration: ${duration}
- warmup duration: ${warmupDuration}
- scenario mode: ${scenarioMode}
- arrival rate: ${rate}/${timeUnit}
- burst rate: ${burstRate}/1s
- burst duration: ${burstDuration}
- pre allocated VUs: ${preAllocatedVUs}
- max VUs: ${maxVUs}
- limit: ${limit}
- observability mode: ${observabilityMode}
- overload mode: ${overloadMode}
- max retry-after sleep seconds: ${maxRetryAfterSleepSeconds}
- max retry-after sleep ms: ${maxRetryAfterSleepMs}
- hot account id: ${hotAccountId}
- cold account id: ${coldAccountId}
- hot deep cursor: ${hotDeepCursorBookedAt}|${hotDeepCursorId}
- cold deep cursor: ${coldDeepCursorBookedAt}|${coldDeepCursorId}
- hot p95 threshold ms: ${hotP95ThresholdMs}
- cold p95 threshold ms: ${coldP95ThresholdMs}
- hot p99 threshold ms: ${hotP99ThresholdMs}
- cold p99 threshold ms: ${coldP99ThresholdMs}
- hot p99.9 threshold ms: ${hotP999ThresholdMs}
- cold p99.9 threshold ms: ${coldP999ThresholdMs}
- hot max threshold ms: ${hotMaxThresholdMs}
- cold max threshold ms: ${coldMaxThresholdMs}
- hot deep p95 threshold ms: ${hotDeepP95ThresholdMs}
- cold deep p95 threshold ms: ${coldDeepP95ThresholdMs}
- hot deep p99 threshold ms: ${hotDeepP99ThresholdMs}
- cold deep p99 threshold ms: ${coldDeepP99ThresholdMs}
- hot deep p99.9 threshold ms: ${hotDeepP999ThresholdMs}
- cold deep p99.9 threshold ms: ${coldDeepP999ThresholdMs}
- hot deep max threshold ms: ${hotDeepMaxThresholdMs}
- cold deep max threshold ms: ${coldDeepMaxThresholdMs}
- http failed rate threshold: ${httpFailedRateThreshold}
- overload 429 rate threshold: ${overload429RateThresholdText}
- burst 429 rate threshold: ${burst429RateThresholdText}
- effective overload 429 rate threshold: ${overloadMode ? effectiveOverload429RateThreshold : "disabled outside overload mode"}
- overload 503 rate threshold: ${overload503RateThresholdText}

## Results

- http_req_failed rate: ${metric(data, "http_req_failed", "rate")}
- checks rate: ${metric(data, "checks", "rate")}
- transaction 429 rate: ${metric(data, "aquila_transaction_429_rate", "rate")}
- transaction 503 rate: ${metric(data, "aquila_transaction_503_rate", "rate")}
- transaction 503 count: ${metric(data, "aquila_transaction_503_count", "count")}
- retry-after sleep count: ${metric(data, "aquila_transaction_retry_after_count", "count")}
- retry-after sleep avg ms: ${metric(data, "aquila_transaction_retry_after_sleep_ms", "avg")}
- retry-after sleep p95 ms: ${metric(data, "aquila_transaction_retry_after_sleep_ms", "p(95)")}
- retry-after sleep max ms: ${metric(data, "aquila_transaction_retry_after_sleep_ms", "max")}
- hot first p95 ms: ${metric(data, "aquila_transaction_hot_first_ms", "p(95)")}
- hot first p99 ms: ${metric(data, "aquila_transaction_hot_first_ms", "p(99)")}
- hot first p99.9 ms: ${metric(data, "aquila_transaction_hot_first_ms", "p(99.9)")}
- hot first max ms: ${metric(data, "aquila_transaction_hot_first_ms", "max")}
- hot cursor p95 ms: ${metric(data, "aquila_transaction_hot_cursor_ms", "p(95)")}
- hot cursor p99 ms: ${metric(data, "aquila_transaction_hot_cursor_ms", "p(99)")}
- hot cursor p99.9 ms: ${metric(data, "aquila_transaction_hot_cursor_ms", "p(99.9)")}
- hot cursor max ms: ${metric(data, "aquila_transaction_hot_cursor_ms", "max")}
- hot deep cursor p95 ms: ${metric(data, "aquila_transaction_hot_deep_cursor_ms", "p(95)")}
- hot deep cursor p99 ms: ${metric(data, "aquila_transaction_hot_deep_cursor_ms", "p(99)")}
- hot deep cursor p99.9 ms: ${metric(data, "aquila_transaction_hot_deep_cursor_ms", "p(99.9)")}
- hot deep cursor max ms: ${metric(data, "aquila_transaction_hot_deep_cursor_ms", "max")}
- cold first p95 ms: ${metric(data, "aquila_transaction_cold_first_ms", "p(95)")}
- cold first p99 ms: ${metric(data, "aquila_transaction_cold_first_ms", "p(99)")}
- cold first p99.9 ms: ${metric(data, "aquila_transaction_cold_first_ms", "p(99.9)")}
- cold first max ms: ${metric(data, "aquila_transaction_cold_first_ms", "max")}
- cold cursor p95 ms: ${metric(data, "aquila_transaction_cold_cursor_ms", "p(95)")}
- cold cursor p99 ms: ${metric(data, "aquila_transaction_cold_cursor_ms", "p(99)")}
- cold cursor p99.9 ms: ${metric(data, "aquila_transaction_cold_cursor_ms", "p(99.9)")}
- cold cursor max ms: ${metric(data, "aquila_transaction_cold_cursor_ms", "max")}
- cold deep cursor p95 ms: ${metric(data, "aquila_transaction_cold_deep_cursor_ms", "p(95)")}
- cold deep cursor p99 ms: ${metric(data, "aquila_transaction_cold_deep_cursor_ms", "p(99)")}
- cold deep cursor p99.9 ms: ${metric(data, "aquila_transaction_cold_deep_cursor_ms", "p(99.9)")}
- cold deep cursor max ms: ${metric(data, "aquila_transaction_cold_deep_cursor_ms", "max")}

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- overload mode에서는 admission guard 429를 rejected sample로 집계합니다.
- overload mode에서도 503은 app/backend failure 신호라 hard fail로 분리합니다.
- warmup phase는 endpoint/JVM/cache/pool 초기화를 분리하고, custom latency Trend는 measured phase만 기록합니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
${observabilityNote()}
`;
}

export function handleSummary(data) {
  const jsonPath = `/reports/${reportName}-summary.json`;
  const markdownPath = `/reports/${reportName}-summary.md`;
  return {
    [jsonPath]: JSON.stringify(data, null, 2),
    [markdownPath]: markdownSummary(data),
    stdout: markdownSummary(data),
  };
}
