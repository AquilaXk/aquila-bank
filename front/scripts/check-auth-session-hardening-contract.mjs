import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = new URL("..", import.meta.url).pathname;

function read(path) {
  return readFileSync(join(root, path), "utf8");
}

const files = {
  client: read("src/lib/api/client.ts"),
  session: read("src/lib/api/session.ts"),
  hook: read("src/hooks/use-customer-banking.ts"),
};

const required = [
  ["fetch cookie credentials", files.client, 'credentials: "include"'],
  ["session load stays memory only", files.session, "return null;"],
  ["session save does not persist", files.session, "void session;"],
  ["SSE uses cookie credentials", files.hook, "withCredentials: true"],
  ["SSE stream path", files.hook, 'api.streamUrl("/api/v1/notifications/stream")'],
];

const forbidden = [
  ["client localStorage", files.client, "localStorage"],
  ["client sessionStorage", files.client, "sessionStorage"],
  ["session localStorage", files.session, "localStorage"],
  ["session sessionStorage", files.session, "sessionStorage"],
  ["hook localStorage", files.hook, "localStorage"],
  ["hook sessionStorage", files.hook, "sessionStorage"],
  ["client Authorization fallback", files.client, "Authorization"],
  ["client bearer fallback", files.client, "Bearer "],
  ["client refreshToken body field", files.client, "refreshToken:"],
  ["client accessToken body field", files.client, "accessToken:"],
  ["SSE token query", files.hook, "token="],
  ["SSE access token query", files.hook, "accessToken"],
  ["SSE refresh token query", files.hook, "refreshToken"],
];

const missing = required.filter(([, content, expected]) => !content.includes(expected));
const present = forbidden.filter(([, content, value]) => content.includes(value));

if (missing.length > 0 || present.length > 0) {
  console.error("[auth-session-hardening-contract] contract violations:");
  for (const [name, , expected] of missing) {
    console.error(`- missing ${name}: ${expected}`);
  }
  for (const [name, , value] of present) {
    console.error(`- forbidden ${name}: ${value}`);
  }
  process.exit(1);
}

console.log("[auth-session-hardening-contract] passed");
