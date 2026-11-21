import { createHash } from "node:crypto";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import ts from "typescript";

const frontRoot = new URL("..", import.meta.url).pathname;
const repoRoot = join(frontRoot, "..");

function walkFiles(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      walkFiles(full, out);
    } else {
      out.push(full);
    }
  }
  return out;
}

const forbiddenDirectory = join(frontRoot, "src/components/oss-fintech");
if (existsSync(forbiddenDirectory)) {
  console.error("[clean-room] forbidden third-party directory still exists:", forbiddenDirectory);
  process.exit(1);
}

const requiredCleanRoomFiles = [
  "src/components/fintech-ui/icons.tsx",
  "src/components/fintech-ui/account-card.tsx",
  "src/components/fintech-ui/metric-primitives.tsx",
  "src/components/fintech-ui/analytics-panels.tsx",
  "src/components/fintech-ui/category-badge.tsx",
  "src/lib/customer-banking/qrcode.ts",
];

for (const rel of requiredCleanRoomFiles) {
  if (!existsSync(join(frontRoot, rel))) {
    console.error("[clean-room] missing clean-room module:", rel);
    process.exit(1);
  }
}

const forbiddenTokenPatterns = [
  /oss-fintech/i,
  /(?:^|[^a-z0-9_-])oss-[a-z0-9_-]+/i,
  /nayuki/i,
  /qrcodegen/i,
  /lucide/i,
  /tremor/i,
  /shadcn/i,
  /satnaing/i,
  /adrianhajdin/i,
  /arhamkhnz/i,
  /abderrahimghazali/i,
  /horizonbankcard/i,
  /horizonaccountbanner/i,
  /horizoncategorybadge/i,
  /createlucidebaseprops/i,
];

const scanTargets = [
  ...walkFiles(join(frontRoot, "src")),
  join(repoRoot, "THIRD_PARTY_NOTICES.md"),
];

const tokenHits = [];
for (const filePath of scanTargets) {
  const content = readFileSync(filePath, "utf8");
  for (const pattern of forbiddenTokenPatterns) {
    const m = content.match(pattern);
    if (m) {
      tokenHits.push(`${filePath.replace(`${repoRoot}/`, "")}: ${m[0]}`);
    }
  }
}

if (tokenHits.length > 0) {
  console.error("[clean-room] forbidden OSS attribution or symbol references remain:");
  for (const hit of tokenHits) {
    console.error(`- ${hit}`);
  }
  process.exit(1);
}

// Compile TypeScript QR encoder and verify bit-exact ISO/IEC 18004 matrices + SVG paths + bounds
const qrSource = readFileSync(join(frontRoot, "src/lib/customer-banking/qrcode.ts"), "utf8");
const compiled = ts.transpileModule(qrSource, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText;

const qrModule = { exports: {} };
new Function("exports", "module", compiled)(qrModule.exports, qrModule);
const { encodeQrCodeMatrix, encodeQrCodeSvgPaths } = qrModule.exports;

const isoVectors = [
  [
    "v1-low",
    "A",
    "LOW",
    "22ab3bf2cd0ed98fdb03284c7f9e5765fa3a9c5c588d6406092873e9187d2e35",
  ],
  [
    "v2-med",
    "0123456789ABCDEF",
    "MEDIUM",
    "168198e726298db4df4e8427a248e914804fc78301c80088f058477da9d2693c",
  ],
  [
    "totp-med",
    "otpauth://totp/AquilaBank:customer01?secret=JBSWY3DPEHPK3PXP&issuer=AquilaBank&algorithm=SHA1&digits=6&period=30",
    "MEDIUM",
    "ad6cad4d8e5b2d38484a8bc0e47dfb771f7723be2e4d4774b9df280ebe360e60",
  ],
  [
    "ko-quar",
    "Aquila Bank 계좌이체 보안인증 QR 테스트 2026",
    "QUARTILE",
    "f46f9ff65eec5e7131296feb062d45f9b567eafd9c0abbb03c57eca4436f9a0d",
  ],
  [
    "v7plus-high",
    `https://bank.aquilaxk.site/verify?token=${"x".repeat(96)}`,
    "HIGH",
    "bb933c4b8c7ccd846eaf0e2f6e58d49527de437076ad7cb2bc5f966edfb55e38",
  ],
  [
    "multi-med",
    "Q".repeat(250),
    "MEDIUM",
    "31686e4bac2bdbf27af39d28e280816df0328c75eded95cdbd569648978ab47a",
  ],
  [
    "max512-low",
    "Z".repeat(512),
    "LOW",
    "a817c30b8ed049cfbe188a130a25b06c73d8353c80752e0205bdcc806f969622",
  ],
  [
    "max512-high",
    "Z".repeat(512),
    "HIGH",
    "8dabbc5dc664f546b0362141a976f5275b71d039181119805b2b4cb83a9eb638",
  ],
];

for (const [id, payload, ecc, expectedSha256] of isoVectors) {
  const mat = encodeQrCodeMatrix(payload, ecc);
  const svg = encodeQrCodeSvgPaths(payload, { ecc, border: 3 });

  if (svg.moduleCount !== mat.size || svg.size !== mat.size + 6 || svg.viewBox !== `0 0 ${svg.size} ${svg.size}`) {
    console.error(`[clean-room] SVG dimensions mismatch for ${id}`);
    process.exit(1);
  }

  const parsedSvgGrid = Array.from({ length: mat.size }, () => new Uint8Array(mat.size));
  const rectRegex = /M(\d+) (\d+)h(\d+)v1h-(\d+)z/g;
  let match;
  while ((match = rectRegex.exec(svg.pathData)) !== null) {
    const xStart = Number(match[1]) - 3;
    const y = Number(match[2]) - 3;
    const w = Number(match[3]);
    const wBack = Number(match[4]);
    if (w !== wBack || xStart < 0 || xStart + w > mat.size || y < 0 || y >= mat.size) {
      console.error(`[clean-room] invalid SVG run geometry in ${id}:`, match[0]);
      process.exit(1);
    }
    for (let x = xStart; x < xStart + w; x += 1) {
      parsedSvgGrid[y][x] = 1;
    }
  }

  const hash = createHash("sha256");
  for (let y = 0; y < mat.size; y += 1) {
    const row = new Uint8Array(mat.size);
    for (let x = 0; x < mat.size; x += 1) {
      const bit = mat.isDark(x, y) ? 1 : 0;
      if (parsedSvgGrid[y][x] !== bit) {
        console.error(`[clean-room] SVG path pixel mismatch at (${x},${y}) in ${id}`);
        process.exit(1);
      }
      row[x] = bit;
    }
    hash.update(row);
  }

  const digest = hash.digest("hex");
  if (digest !== expectedSha256) {
    console.error(`[clean-room] ISO matrix SHA-256 mismatch for ${id}: expected=${expectedSha256}, got=${digest}`);
    process.exit(1);
  }
}

// Fail-Closed input bounds check
const malformedCases = [
  () => encodeQrCodeSvgPaths(""),
  () => encodeQrCodeSvgPaths(null),
  () => encodeQrCodeSvgPaths(123),
  () => encodeQrCodeSvgPaths("X".repeat(513)),
  () => encodeQrCodeSvgPaths("가".repeat(172)),
  () => encodeQrCodeSvgPaths("OK", { border: -1 }),
  () => encodeQrCodeSvgPaths("OK", { border: 11 }),
  () => encodeQrCodeSvgPaths("OK", { border: 1.5 }),
];

for (let i = 0; i < malformedCases.length; i += 1) {
  let rejected = false;
  try {
    malformedCases[i]();
  } catch (err) {
    if (err instanceof TypeError || err instanceof RangeError) {
      rejected = true;
    }
  }
  if (!rejected) {
    console.error(`[clean-room] fail-closed rejection failed on malformed case #${i}`);
    process.exit(1);
  }
}

// Throughput gate (500 TOTP QR encodings)
const sampleTotp = isoVectors[2][1];
for (let i = 0; i < 50; i += 1) {
  encodeQrCodeSvgPaths(sampleTotp, { ecc: "MEDIUM", border: 3 });
}
const startMs = performance.now();
for (let i = 0; i < 500; i += 1) {
  encodeQrCodeSvgPaths(sampleTotp, { ecc: "MEDIUM", border: 3 });
}
const elapsedMs = performance.now() - startMs;
if (elapsedMs > 1500) {
  console.error(`[clean-room] QR throughput regression: 500 ops took ${elapsedMs.toFixed(2)} ms (> 1500 ms)`);
  process.exit(1);
}

const usPerOp = (elapsedMs * 1000) / 500;
const opsPerSec = Math.round(500 / (elapsedMs / 1000));
console.log(
  `[clean-room] passed (8/8 ISO SHA-256 vectors verified, 8 fail-closed bounds verified, ${usPerOp.toFixed(2)} us/op / ${opsPerSec} ops/s)`,
);
