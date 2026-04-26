import http from "k6/http";
import {check, fail} from "k6";
import {Rate, Trend} from "k6/metrics";

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
const reportName = __ENV.K6_REPORT_NAME || "transaction-100m-weighted";

const weights = [
  ["hot_first", Number(__ENV.K6_WEIGHT_HOT_FIRST || "45")],
  ["hot_cursor", Number(__ENV.K6_WEIGHT_HOT_CURSOR || "25")],
  ["cold_first", Number(__ENV.K6_WEIGHT_COLD_FIRST || "15")],
  ["cold_cursor", Number(__ENV.K6_WEIGHT_COLD_CURSOR || "10")],
  ["detail", Number(__ENV.K6_WEIGHT_DETAIL || "0")],
];
const totalWeight = weights.reduce((sum, [, value]) => sum + Math.max(0, value), 0);
const detailWeight = Math.max(0, weights.find(([shape]) => shape === "detail")[1]);

const hotFirst = new Trend("aquila_transaction_weighted_hot_first_ms", true);
const hotCursor = new Trend("aquila_transaction_weighted_hot_cursor_ms", true);
const coldFirst = new Trend("aquila_transaction_weighted_cold_first_ms", true);
const coldCursor = new Trend("aquila_transaction_weighted_cold_cursor_ms", true);
const detailTrend = new Trend("aquila_transaction_detail_ms", true);
const transaction429Rate = new Rate("aquila_transaction_weighted_429_rate");

let hotCursorValue = "";
let coldCursorValue = "";
let lastHotReference = "";

function thresholds() {
  const result = {
    http_req_failed: ["rate<0.01"],
    checks: ["rate>0.99"],
    aquila_transaction_weighted_hot_first_ms: ["p(95)<350"],
    aquila_transaction_weighted_hot_cursor_ms: ["p(95)<350"],
    aquila_transaction_weighted_cold_first_ms: ["p(95)<750"],
    aquila_transaction_weighted_cold_cursor_ms: ["p(95)<750"],
  };
  if (detailWeight > 0) {
    result.aquila_transaction_detail_ms = ["p(95)<350"];
  }
  return result;
}

export const options = {
  scenarios: {
    transaction_read_100m_weighted: {
      executor: "constant-vus",
      vus,
      duration,
    },
  },
  thresholds: thresholds(),
  tags: {
    service: "aquila-bank",
    workload: "transaction-read-100m-weighted",
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
    "X-Subject": "k6-transaction-100m-weighted",
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

function randomWeightedShape() {
  if (totalWeight <= 0) {
    fail("weighted workload total weight must be positive");
  }
  let cursor = Math.random() * totalWeight;
  for (const [shape, weight] of weights) {
    cursor -= Math.max(0, weight);
    if (cursor < 0) {
      return shape;
    }
  }
  return "hot_first";
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
  } else if (shape === "detail") {
    detailTrend.add(durationMs);
  }
}

function requestPage(shape, path, accountId, from, to, cursor) {
  const query = queryString({accountId, from, to, limit, cursor});
  const response = http.get(`${baseUrl}${path}?${query}`, {
    headers: headers(accountId),
    tags: {name: shape, query_shape: shape},
  });
  transaction429Rate.add(response.status === 429);
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
  if (shape === "hot_first" && body.items[0].transactionReference) {
    lastHotReference = body.items[0].transactionReference;
  }
  return body;
}

function requestDetail() {
  if (!lastHotReference) {
    const body = requestPage("hot_first", "/api/v1/transactions", hotAccountId, hotFrom, hotTo, "");
    lastHotReference = body.items[0].transactionReference;
  }
  const query = queryString({accountId: hotAccountId});
  const response = http.get(
    `${baseUrl}/api/v1/transactions/${encodeURIComponent(lastHotReference)}?${query}`,
    {headers: headers(hotAccountId), tags: {name: "detail", query_shape: "detail"}},
  );
  transaction429Rate.add(response.status === 429);
  record("detail", response.timings.duration);
  const ok = check(response, {
    "detail status is 2xx": (item) => item.status >= 200 && item.status < 300,
  });
  if (!ok) {
    fail(`detail returned HTTP ${response.status}`);
  }
}

export default function () {
  requireEnv("K6_HOT_ACCOUNT_ID", hotAccountId);
  requireEnv("K6_HOT_FROM", hotFrom);
  requireEnv("K6_HOT_TO", hotTo);
  requireEnv("K6_COLD_ACCOUNT_ID", coldAccountId);
  requireEnv("K6_COLD_FROM", coldFrom);
  requireEnv("K6_COLD_TO", coldTo);

  const shape = randomWeightedShape();
  if (shape === "hot_first") {
    const body = requestPage("hot_first", "/api/v1/transactions", hotAccountId, hotFrom, hotTo, "");
    hotCursorValue = body.nextCursor || hotCursorValue;
  } else if (shape === "hot_cursor") {
    if (!hotCursorValue) {
      hotCursorValue =
        requestPage("hot_first", "/api/v1/transactions", hotAccountId, hotFrom, hotTo, "").nextCursor || "";
    }
    requestPage("hot_cursor", "/api/v1/transactions", hotAccountId, hotFrom, hotTo, hotCursorValue);
  } else if (shape === "cold_first") {
    const body = requestPage("cold_first", "/api/v1/transactions/archive", coldAccountId, coldFrom, coldTo, "");
    coldCursorValue = body.nextCursor || coldCursorValue;
  } else if (shape === "cold_cursor") {
    if (!coldCursorValue) {
      coldCursorValue =
        requestPage("cold_first", "/api/v1/transactions/archive", coldAccountId, coldFrom, coldTo, "")
          .nextCursor || "";
    }
    requestPage(
      "cold_cursor",
      "/api/v1/transactions/archive",
      coldAccountId,
      coldFrom,
      coldTo,
      coldCursorValue,
    );
  } else {
    requestDetail();
  }
}

function metric(data, name, valueName) {
  const item = data.metrics[name];
  if (!item || !item.values || item.values[valueName] === undefined) {
    return "n/a";
  }
  return item.values[valueName];
}

function markdownSummary(data) {
  return `# k6 Transaction 100m Weighted Load Test

## Environment

- baseUrl: ${baseUrl}
- vus: ${vus}
- duration: ${duration}
- limit: ${limit}
- weights hot_first/hot_cursor/cold_first/cold_cursor/detail: ${weights.map(([, value]) => value).join("/")}

## Results

- http_req_failed rate: ${metric(data, "http_req_failed", "rate")}
- checks rate: ${metric(data, "checks", "rate")}
- weighted 429 rate: ${metric(data, "aquila_transaction_weighted_429_rate", "rate")}
- hot first p95 ms: ${metric(data, "aquila_transaction_weighted_hot_first_ms", "p(95)")}
- hot cursor p95 ms: ${metric(data, "aquila_transaction_weighted_hot_cursor_ms", "p(95)")}
- cold first p95 ms: ${metric(data, "aquila_transaction_weighted_cold_first_ms", "p(95)")}
- cold cursor p95 ms: ${metric(data, "aquila_transaction_weighted_cold_cursor_ms", "p(95)")}
- detail p95 ms: ${metric(data, "aquila_transaction_detail_ms", "p(95)")}
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
