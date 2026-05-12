import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const root = new URL("..", import.meta.url).pathname;

function read(path) {
  const filePath = join(root, path);
  return existsSync(filePath) ? readFileSync(filePath, "utf8") : "";
}

const files = {
  packageJson: read("package.json"),
  page: read("src/app/page.tsx"),
  layout: read("src/components/customer-banking/layout.tsx"),
  styles: read("src/styles/customer-banking.css"),
  visualE2e: read("e2e/customer-banking-visual-layout.spec.ts"),
};

const required = [
  ["package mobile work script", files.packageJson, "test:mobile-work-polish"],
  ["mobile priority layout class", files.page, "mobile-priority-work"],
  ["mobile compact primary nav class", files.layout, "mobile-compact-primary-nav"],
  ["mobile compact side menu class", files.layout, "mobile-compact-side-menu"],
  ["mobile section jump label", files.layout, "모바일 업무 바로가기"],
  ["mobile compact shell style", files.styles, ".mobile-priority-work"],
  ["mobile work first order", files.styles, ".bank-layout.mobile-priority-work .work-area"],
  ["mobile side menu compact style", files.styles, ".side-menu.mobile-compact-side-menu"],
  ["mobile primary nav compact style", files.styles, ".primary-nav.mobile-compact-primary-nav"],
  ["mobile utility compact style", files.styles, ".utility-bar.mobile-compact-utility"],
  ["mobile e2e work before side", files.visualE2e, "expect(workBox?.y ?? 0).toBeLessThan(sideBox?.y ?? 0)"],
  ["mobile e2e compact jump", files.visualE2e, "모바일 업무 바로가기"],
];

const missing = required.filter(([, content, expected]) => !content.includes(expected));

if (missing.length > 0) {
  console.error("[customer-banking-mobile-work-polish] contract violations:");
  for (const [name, , expected] of missing) {
    console.error(`- missing ${name}: ${expected}`);
  }
  process.exit(1);
}

console.log("[customer-banking-mobile-work-polish] passed");
