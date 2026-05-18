import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = new URL("..", import.meta.url).pathname;

function read(path) {
  return readFileSync(join(root, path), "utf8");
}

const files = {
  page: read("src/app/page.tsx"),
  layout: read("src/components/customer-banking/layout.tsx"),
  dashboard: read("src/components/customer-banking/sections/dashboard-section.tsx"),
  security: read("src/components/customer-banking/sections/security-section.tsx"),
  styles: read("src/styles/customer-banking.css"),
};

const combined = Object.values(files).join("\n");

const required = [
  ["guest auth state marker", files.page, "data-auth-state"],
  ["guest protected rail class", files.layout, "guest-protected"],
  ["guest lock state class", files.styles, ".guest-protected"],
  ["sensitive placeholder class", files.styles, ".sensitive-placeholder"],
  ["bank header guest protection", files.layout, "민감정보 보호"],
  ["guest service strip", files.layout, "보호모드"],
  ["guest work locked copy", files.layout, "로그인 후 업무 가능"],
  ["guest recent menu lock", files.layout, "최근 이용 메뉴 잠김"],
  ["guest dashboard locked summary", files.dashboard, "로그인 후 조회"],
  ["guest dashboard transfer lock", files.dashboard, "인증 후 이체"],
  ["guest dashboard notification lock", files.dashboard, "로그인 후 알림 확인"],
  ["guest dashboard protected table state", files.dashboard, "인증 후 가능"],
  ["guest security auth marker", files.security, "const isAuthenticated"],
  ["guest security renderer", files.security, "renderGuestSecurityCenter"],
  ["authenticated security renderer", files.security, "renderAuthenticatedSecurityCenter"],
  ["guest totp state gate", files.security, "activeTotpEnrollment"],
  ["guest backup code state gate", files.security, "activeBackupCodes"],
  ["guest security public heading", files.security, "로그인 및 인증"],
  ["guest recovery public scope", files.security, "비로그인 복구 가능"],
  ["authenticated security heading", files.security, "인증 후 보안관리"],
  ["authenticated-only form class", files.security, "authenticated-only-form"],
  ["masked form class", files.styles, ".authenticated-only-form"],
];

const forbiddenUnauthSignals = [
  ["layout guest work available", files.layout, "sessionRequired ? \"로그인 필요\" : \"업무 가능\""],
  ["dashboard unauth next lookup", files.dashboard, "<td>다음 조회 가능</td>"],
  ["dashboard unauth duplicate guard state", files.dashboard, "<td>중복 방지</td>"],
  ["dashboard unauth realtime state", files.dashboard, "<td>실시간 알림</td>"],
  ["security guest otp unregistered", files.security, "props.totpEnrollment ? props.totpEnrollment.status : \"미등록\""],
  ["security guest raw session count", files.security, "<strong>{props.sessions.length}건</strong>"],
  ["security guest empty session row", files.security, "조회된 세션이 없습니다."],
  ["security guest masked dashboard value", files.security, "OTP 상태 로그인 후 확인"],
  ["security guest session table placeholder", files.security, "로그인 후 세션 조회 가능"],
  ["security guest session detail placeholder", files.security, "인증 후 표시"],
  ["security guest locked management form", files.security, "data-locked={!isAuthenticated}"],
];

const missing = required.filter(([, content, expected]) => !content.includes(expected));
const present = forbiddenUnauthSignals.filter(([, content, expected]) =>
  content.includes(expected),
);

if (missing.length > 0 || present.length > 0) {
  console.error("[customer-banking-unauth-security] contract violations:");
  for (const [name, , expected] of missing) {
    console.error(`- missing ${name}: ${expected}`);
  }
  for (const [name, , expected] of present) {
    console.error(`- forbidden ${name}: ${expected}`);
  }
  process.exit(1);
}

console.log(
  `[customer-banking-unauth-security] passed: ${combined.length} checked bytes`,
);
