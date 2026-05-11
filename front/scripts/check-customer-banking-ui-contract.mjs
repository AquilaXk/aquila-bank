import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = new URL("..", import.meta.url).pathname;

function read(path) {
  return readFileSync(join(root, path), "utf8");
}

const files = {
  page: read("src/app/page.tsx"),
  layout: read("src/app/layout.tsx"),
  constants: read("src/lib/customer-banking/constants.ts"),
  styles: read("src/styles/customer-banking.css"),
};

const checks = [
  ["top personal tab", files.page, "개인"],
  ["top business tab", files.page, "기업"],
  ["top certificate center", files.page, "인증센터"],
  ["top customer center", files.page, "고객센터"],
  ["integrated search label", files.page, "통합검색"],
  ["service map command", files.page, "전체서비스"],
  ["security status bar", files.page, "보안등급"],
  ["left business menu", files.page, "개인뱅킹 메뉴"],
  ["right login status", files.page, "로그인 상태"],
  ["right recent menu", files.page, "최근 이용 메뉴"],
  ["right notices", files.page, "공지사항"],
  ["account 업무 menu", files.constants, "계좌조회"],
  ["transfer 업무 menu", files.constants, "즉시이체"],
  ["transactions 업무 menu", files.constants, "거래내역 조회"],
  ["notifications 업무 menu", files.constants, "알림함"],
  ["responsive shell media query", files.styles, "@media (max-width: 760px)"],
  ["metadata public title", files.layout, "Aquila Bank 개인 인터넷뱅킹"],
];

const missing = checks.filter(([, content, expected]) => !content.includes(expected));

if (missing.length > 0) {
  console.error("[customer-banking-ui-contract] missing required UI contract:");
  for (const [name, , expected] of missing) {
    console.error(`- ${name}: ${expected}`);
  }
  process.exit(1);
}

console.log("[customer-banking-ui-contract] passed");
