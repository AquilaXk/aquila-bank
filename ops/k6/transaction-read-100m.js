import http from "k6/http";
import {check, fail} from "k6";
import {Trend} from "k6/metrics";

const baseUrl = (__ENV.BASE_URL || "http://aquila-bank-backend:8080").replace(/\/$/, "");
const hotAccountId = __ENV.K6_HOT_ACCOUNT_ID || "";
const hotFrom = __ENV.K6_HOT_FROM || "";
const hotTo = __ENV.K6_HOT_TO || "";
const coldAccountId = __ENV.K6_COLD_ACCOUNT_ID || "";
const coldFrom = __ENV.K6_COLD_FROM || "";
const coldTo = __ENV.K6_COLD_TO || "";
const authToken = __ENV.K6_AUTH_TOKEN || "";
const limit = Number(__ENV.K6_LIMIT || "50");
const vus = Number(__ENV.K6_VUS || "8");
const duration = __ENV.K6_DURATION || "1m";
const hotP95ThresholdMs = Number(__ENV.K6_HOT_P95_THRESHOLD_MS || "350");
const coldP95ThresholdMs = Number(__ENV.K6_COLD_P95_THRESHOLD_MS || "750");
const failedRate = Number(__ENV.K6_HTTP_FAILED_RATE || "0.01");
const reportName = __ENV.K6_REPORT_NAME || "transaction-100m";

const hotFirst = new Trend("aquila_transaction_hot_first_ms", true);
const hotCursor = new Trend("aquila_transaction_hot_cursor_ms", true);
const coldFirst = new Trend("aquila_transaction_cold_first_ms", true);
const coldCursor = new Trend("aquila_transaction_cold_cursor_ms", true);

export const options = {
  scenarios: {
    transaction_read_100m: {
      executor: "constant-vus",
      vus,
      duration,
    },
  },
  thresholds: {
    http_req_failed: [`rate<${failedRate}`],
    checks: ["rate>0.99"],
    aquila_transaction_hot_first_ms: [`p(95)<${hotP95ThresholdMs}`],
    aquila_transaction_hot_cursor_ms: [`p(95)<${hotP95ThresholdMs}`],
    aquila_transaction_cold_first_ms: [`p(95)<${coldP95ThresholdMs}`],
    aquila_transaction_cold_cursor_ms: [`p(95)<${coldP95ThresholdMs}`],
  },
  tags: {
    service: "aquila-bank",
    workload: "transaction-read-100m",
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
  if (shape === "hot_first") {
    hotFirst.add(durationMs);
  } else if (shape === "hot_cursor") {
    hotCursor.add(durationMs);
  } else if (shape === "cold_first") {
    coldFirst.add(durationMs);
  } else if (shape === "cold_cursor") {
    coldCursor.add(durationMs);
  }
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
  if (!hotFirstBody.nextCursor) {
    fail("hot_first did not return nextCursor");
  }
  requestPage("hot_cursor", "/api/v1/transactions", hotAccountId, hotFrom, hotTo, hotFirstBody.nextCursor);

  const coldFirstBody = requestPage(
    "cold_first",
    "/api/v1/transactions/archive",
    coldAccountId,
    coldFrom,
    coldTo,
    "",
  );
  if (!coldFirstBody.nextCursor) {
    fail("cold_first did not return nextCursor");
  }
  requestPage(
    "cold_cursor",
    "/api/v1/transactions/archive",
    coldAccountId,
    coldFrom,
    coldTo,
    coldFirstBody.nextCursor,
  );
}

function metric(data, name, valueName) {
  const item = data.metrics[name];
  if (!item || !item.values || item.values[valueName] === undefined) {
    return "n/a";
  }
  return item.values[valueName];
}

function markdownSummary(data) {
  return `# k6 Transaction 100m Load Test

## Environment

- baseUrl: ${baseUrl}
- vus: ${vus}
- duration: ${duration}
- limit: ${limit}
- hot account id: ${hotAccountId}
- cold account id: ${coldAccountId}
- hot p95 threshold ms: ${hotP95ThresholdMs}
- cold p95 threshold ms: ${coldP95ThresholdMs}
- http failed rate threshold: ${failedRate}

## Results

- http_req_failed rate: ${metric(data, "http_req_failed", "rate")}
- checks rate: ${metric(data, "checks", "rate")}
- hot first p95 ms: ${metric(data, "aquila_transaction_hot_first_ms", "p(95)")}
- hot cursor p95 ms: ${metric(data, "aquila_transaction_hot_cursor_ms", "p(95)")}
- cold first p95 ms: ${metric(data, "aquila_transaction_cold_first_ms", "p(95)")}
- cold cursor p95 ms: ${metric(data, "aquila_transaction_cold_cursor_ms", "p(95)")}

## Notes

- 이 결과는 k6 HTTP replay 기준입니다.
- 1억 건 분포는 실행 전 DB에 준비되어 있어야 합니다.
- Prometheus remote write 대상은 \`K6_PROMETHEUS_RW_SERVER_URL\`입니다.
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
