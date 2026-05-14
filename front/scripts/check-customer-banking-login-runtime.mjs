import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = new URL("..", import.meta.url).pathname;

function read(path) {
  return readFileSync(join(root, path), "utf8");
}

const files = {
  packageJson: read("package.json"),
  apiClient: read("src/lib/api/client.ts"),
  format: read("src/lib/customer-banking/format.ts"),
};

const required = [
  ["package script", files.packageJson, "test:login-runtime"],
  ["network error type", files.apiClient, "ApiNetworkError"],
  ["network fetch cause check", files.apiClient, "Failed to fetch"],
  ["network fetch cause check", files.apiClient, "fetch failed"],
  ["network guidance message", files.format, "백엔드 서버에 연결하지 못했습니다."],
  ["api url guidance", files.format, "NEXT_PUBLIC_API_BASE_URL"],
  ["backend run guidance", files.format, "백엔드 실행 상태와 API 주소를 확인하세요."],
  ["auth error keeps status", files.format, "[${error.status}]"],
];

const missing = required.filter(([, content, expected]) => !content.includes(expected));

if (missing.length > 0) {
  console.error("[customer-banking-login-runtime] contract violations:");
  for (const [name, , expected] of missing) {
    console.error(`- missing ${name}: ${expected}`);
  }
  process.exit(1);
}

console.log("[customer-banking-login-runtime] passed");
