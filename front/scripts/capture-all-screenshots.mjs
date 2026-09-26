import { chromium } from "playwright";
import { join } from "node:path";
import { spawn } from "node:child_process";

const rootDir = new URL("../..", import.meta.url).pathname;
const port = 3456;
const baseURL = `http://127.0.0.1:${port}`;

async function waitForServer(url, timeoutMs = 30000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      const res = await fetch(url);
      if (res.ok) return;
    } catch {
      // wait
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`Server failed to start at ${url} within ${timeoutMs}ms`);
}

async function setupMockRoutes(page) {
  await page.route("**/api/v1/auth/login", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      json: {
        status: "SUCCESS",
        accessToken: "browser-cookie-session",
        refreshToken: null,
        tokenType: "Cookie",
        expiresAt: "2026-05-12T03:30:00Z",
        refreshExpiresAt: "2026-05-12T04:30:00Z",
        userId: 1001,
        challengeId: null,
        challengeType: null,
        challengeExpiresAt: null,
      },
    });
  });

  await page.route("**/api/v1/auth/sessions**", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      json: {
        items: [
          {
            sessionId: 77,
            sessionStatus: "ACTIVE",
            expiresAt: "2026-05-12T04:30:00Z",
            lastUsedAt: "2026-05-12T02:20:00Z",
            createdAt: "2026-05-12T01:30:00Z",
            deviceName: "Chrome Desktop",
            ipAddress: "203.0.113.10",
            currentSession: true,
          },
        ],
      },
    });
  });

  await page.route("**/api/v1/accounts?**", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      json: {
        items: [
          {
            accountId: 101,
            accountNumber: "110-123-456789",
            displayName: "Aquila 주거래통장",
            accountStatus: "ACTIVE",
            currencyCode: "KRW",
            availableBalanceMinor: 350000000,
            pendingBalanceMinor: 0,
            createdAt: "2026-05-01T00:00:00Z",
            balanceUpdatedAt: "2026-05-12T02:20:00Z",
          },
          {
            accountId: 202,
            accountNumber: "110-987-654321",
            displayName: "Aquila 거래제한통장",
            accountStatus: "RESTRICTED",
            currencyCode: "KRW",
            availableBalanceMinor: 0,
            pendingBalanceMinor: 5000000,
            createdAt: "2026-05-02T00:00:00Z",
            balanceUpdatedAt: "2026-05-12T02:20:00Z",
          },
        ],
        nextCursor: null,
      },
    });
  });

  await page.route("**/api/v1/accounts/101", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      json: {
        accountId: 101,
        accountNumber: "110-123-456789",
        displayName: "Aquila 주거래통장",
        accountStatus: "ACTIVE",
        currencyCode: "KRW",
        availableBalanceMinor: 350000000,
        pendingBalanceMinor: 0,
        createdAt: "2026-05-01T00:00:00Z",
        balanceUpdatedAt: "2026-05-12T02:20:00Z",
      },
    });
  });

  await page.route("**/api/v1/accounts/202", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      json: {
        accountId: 202,
        accountNumber: "110-987-654321",
        displayName: "Aquila 거래제한통장",
        accountStatus: "RESTRICTED",
        currencyCode: "KRW",
        availableBalanceMinor: 0,
        pendingBalanceMinor: 5000000,
        createdAt: "2026-05-02T00:00:00Z",
        balanceUpdatedAt: "2026-05-12T02:20:00Z",
      },
    });
  });

  await page.route("**/api/v1/transfers/preview**", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      json: {
        sourceAccountId: 101,
        targetAccount: {
          accountId: 202,
          maskedAccountNumber: "220-***-445566",
          displayName: "홍길동",
          accountStatus: "ACTIVE",
          currencyCode: "KRW",
        },
        amountMinor: 1200000,
        currencyCode: "KRW",
        feeMinor: 0,
        feePolicy: "WAIVED",
        totalDebitMinor: 1200000,
        singleTransferLimitMinor: 500000000,
        dailyTransferLimitMinor: 1000000000,
        dailyUsedMinor: 0,
        dailyRemainingMinor: 998800000,
        allowed: true,
        blockedReason: "",
        otpRequired: true,
      },
    });
  });

  await page.route("**/api/v1/transactions?**", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      json: {
        items: [
          {
            id: 903,
            transactionReference: "TRX-20260512-0001",
            direction: "DEBIT",
            status: "BOOKED",
            amountMinor: 1200000,
            currencyCode: "KRW",
            balanceAfterMinor: 348800000,
            bookedAt: "2026-05-12T02:25:00Z",
          },
          {
            id: 902,
            transactionReference: "TRX-20260511-0002",
            direction: "CREDIT",
            status: "BOOKED",
            amountMinor: 50000000,
            currencyCode: "KRW",
            balanceAfterMinor: 350000000,
            bookedAt: "2026-05-11T14:10:00Z",
          },
          {
            id: 901,
            transactionReference: "TRX-20260510-0003",
            direction: "DEBIT",
            status: "BOOKED",
            amountMinor: 8500000,
            currencyCode: "KRW",
            balanceAfterMinor: 300000000,
            bookedAt: "2026-05-10T09:15:00Z",
          },
        ],
        nextCursor: null,
        hasNext: false,
      },
    });
  });

  await page.route("**/api/v1/customer-service/applications**", async (route) => {
    const url = new URL(route.request().url());
    const response = {
      applicationReference: "APP-DEMO-1",
      accountId: 101,
      applicationType: "INCIDENT_REPORT",
      processingMode: "MANUAL_REVIEW",
      automatedExecutionSupported: false,
      status: "SUBMITTED",
      mfaVerified: true,
      mfaVerifiedAt: "2026-05-12T02:30:00Z",
      submittedAt: "2026-05-12T02:30:00Z",
      updatedAt: "2026-05-12T02:31:00Z",
      reason: null,
      processedBy: null,
      processedAt: null,
      executionResult: null,
    };

    if (url.pathname.endsWith("/APP-DEMO-1")) {
      await route.fulfill({ contentType: "application/json", json: response });
      return;
    }

    await route.fulfill({
      contentType: "application/json",
      json: { items: [response] },
    });
  });
}

async function loginAsMember(page) {
  await page.goto(`${baseURL}/`);
  await page.locator('.bank-layout[data-auth-state="guest"]').waitFor({ timeout: 20000 });
  await page.getByRole("button", { name: "인증센터" }).first().click();
  const loginForm = page.locator("form").filter({ hasText: "아이디와 비밀번호" });
  await loginForm.waitFor({ timeout: 15000 });
  await loginForm.getByLabel("고객 ID").fill("customer01");
  await loginForm.getByLabel("비밀번호").fill("Password1!");
  await loginForm.getByRole("button", { exact: true, name: "로그인" }).click();
  await page.locator('.bank-layout[data-auth-state="member"]').waitFor({ timeout: 15000 });
}

async function run() {
  console.log("Starting Next.js server on port", port);
  const server = spawn(
    "./node_modules/.bin/next",
    ["dev", "-H", "127.0.0.1", "-p", String(port)],
    {
      cwd: join(rootDir, "front"),
      env: {
        ...process.env,
        PORT: String(port),
        NEXT_TELEMETRY_DISABLED: "1",
        NEXT_PUBLIC_API_BASE_URL: "http://127.0.0.1:18080",
      },
      stdio: "pipe",
    }
  );

  server.stderr.on("data", (data) => console.error("server err:", data.toString()));
  server.stdout.on("data", (data) => console.log("server out:", data.toString()));

  try {
    await waitForServer(baseURL);
    console.log("Next.js server is ready at", baseURL);

    const browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({
      viewport: { width: 1280, height: 720 },
      deviceScaleFactor: 1,
    });
    const page = await context.newPage();
    page.on("pageerror", (err) => console.error("PAGE ERROR:", err));
    await setupMockRoutes(page);

    // 7. Ops Console view
    console.log("Capturing readme-ops-monitoring.jpg...");
    const opsPage = await context.newPage();
    opsPage.on("console", (msg) => console.log("OPS CONSOLE:", msg.type(), msg.text()));
    opsPage.on("pageerror", (err) => console.error("OPS PAGE ERROR:", err));
    await opsPage.goto(`${baseURL}/ops`);
    console.log("Ops page URL:", opsPage.url());
    console.log("Ops page Title:", await opsPage.title());
    console.log("Ops page Body:", await opsPage.evaluate(() => document.body.innerHTML));
    await opsPage.evaluate(() => {
      const el = document.getElementById("ops-metrics-monitoring");
      if (el) el.scrollIntoView({ block: "center" });
    });
    await opsPage.waitForTimeout(300);
    await opsPage.screenshot({
      path: join(rootDir, "docs/assets/readme-ops-monitoring.jpg"),
      type: "jpeg",
      quality: 92,
    });
    await opsPage.close();

    // 1. Search view
    console.log("Capturing readme-customer-banking-search.jpg...");
    await loginAsMember(page);
    await page.getByRole("navigation", { name: "주요 메뉴" }).getByRole("button", { exact: true, name: "뱅킹홈" }).click();
    await page.locator("#utility-search-input").fill("이체한도");
    await page.getByLabel("통합검색 결과").waitFor({ timeout: 3000 });
    await page.waitForTimeout(300);
    await page.screenshot({
      path: join(rootDir, "docs/assets/readme-customer-banking-search.jpg"),
      type: "jpeg",
      quality: 92,
    });
    await page.locator("#utility-search-input").fill("");
    await page.keyboard.press("Escape");
    await page.waitForTimeout(300);

    // 2. Accounts view
    console.log("Capturing readme-customer-banking-accounts.jpg...");
    await page.getByRole("navigation", { name: "주요 메뉴" }).getByRole("button", { exact: true, name: "조회" }).click();
    await page.locator("#bank-work-area").getByRole("button", { exact: true, name: "조회" }).click();
    await page.getByRole("cell", { name: "Aquila 주거래통장" }).waitFor({ timeout: 3000 });
    await page.waitForTimeout(300);
    await page.screenshot({
      path: join(rootDir, "docs/assets/readme-customer-banking-accounts.jpg"),
      type: "jpeg",
      quality: 92,
    });

    // 3. Transfer view (showing transfer form with interactive swap button and quick amounts)
    console.log("Capturing readme-customer-banking-transfer.jpg...");
    await page.getByRole("navigation", { name: "주요 메뉴" }).getByRole("button", { exact: true, name: "이체" }).click();
    const transferForm = page.locator("form").filter({ hasText: "이체정보 입력" });
    await transferForm.waitFor({ timeout: 3000 });
    // Fill in realistic input values so quick amount selectors and inputs look active
    await transferForm.getByLabel("입금계좌번호").fill("110-987-654321");
    await transferForm.getByLabel("이체금액").fill("1000000");
    await transferForm.getByLabel("받는 분 통장 표시").fill("생활비 지원");
    // Click +10만 to show quick amount selection interaction
    await transferForm.getByRole("button", { exact: true, name: "+10만" }).click();
    await page.waitForTimeout(300);
    await page.screenshot({
      path: join(rootDir, "docs/assets/readme-customer-banking-transfer.jpg"),
      type: "jpeg",
      quality: 92,
    });

    // 4. Transactions view (showing Keyset table)
    console.log("Capturing readme-customer-banking-transactions.jpg...");
    await page.getByRole("navigation", { name: "주요 메뉴" }).getByRole("button", { exact: true, name: "거래내역 조회" }).click();
    await page.getByRole("button", { name: "1개월" }).click();
    await page.locator("#bank-work-area").getByRole("button", { exact: true, name: "조회" }).click();
    await page.getByText("TRX-20260512-0001").waitFor({ timeout: 3000 });
    await page.waitForTimeout(300);
    // Scroll slightly so the Keyset table with transactions is prominently visible
    await page.evaluate(() => window.scrollTo(0, 180));
    await page.waitForTimeout(200);
    await page.screenshot({
      path: join(rootDir, "docs/assets/readme-customer-banking-transactions.jpg"),
      type: "jpeg",
      quality: 92,
    });
    await page.evaluate(() => window.scrollTo(0, 0));

    // 5. Security view
    console.log("Capturing readme-customer-banking-security.jpg...");
    await page.getByRole("navigation", { name: "주요 메뉴" }).getByRole("button", { exact: true, name: "인증센터" }).click();
    await page.locator("#bank-work-area").getByRole("button", { exact: true, name: "조회" }).click();
    await page.getByText("Chrome Desktop").waitFor({ timeout: 3000 });
    await page.waitForTimeout(300);
    await page.screenshot({
      path: join(rootDir, "docs/assets/readme-customer-banking-security.jpg"),
      type: "jpeg",
      quality: 92,
    });

    // 6. Application / Support Center view
    console.log("Capturing readme-customer-banking-application.jpg...");
    await page.getByRole("navigation", { name: "주요 메뉴" }).getByRole("button", { exact: true, name: "고객센터/사고신고" }).click();
    await page.getByRole("button", { name: "신청 목록 새로고침" }).click();
    await page.getByText("APP-DEMO-1").waitFor({ timeout: 3000 });
    await page.getByRole("button", { name: /APP-DEMO-1/ }).click();
    await page.getByText("신청 상세", { exact: true }).waitFor({ timeout: 3000 });
    await page.waitForTimeout(300);
    // Scroll slightly so the detail panel is well centered
    await page.evaluate(() => window.scrollTo(0, 140));
    await page.waitForTimeout(200);
    await page.screenshot({
      path: join(rootDir, "docs/assets/readme-customer-banking-application.jpg"),
      type: "jpeg",
      quality: 92,
    });
    await page.evaluate(() => window.scrollTo(0, 0));

    // 8. Enterprise Desktop
    console.log("Capturing customer-banking-enterprise-desktop.png...");
    const desktopPage = await context.newPage();
    await desktopPage.setViewportSize({ width: 1366, height: 900 });
    await setupMockRoutes(desktopPage);
    await loginAsMember(desktopPage);
    await desktopPage.getByRole("navigation", { name: "주요 메뉴" }).getByRole("button", { exact: true, name: "공과금/금융상품" }).click();
    await desktopPage.getByText("공과금 · 오픈뱅킹 · 상품신청").waitFor({ timeout: 3000 });
    await desktopPage.waitForTimeout(300);
    await desktopPage.screenshot({
      path: join(rootDir, "customer-banking-enterprise-desktop.png"),
      type: "png",
    });
    await desktopPage.close();

    // 9. Enterprise Mobile
    console.log("Capturing customer-banking-enterprise-mobile.png...");
    const mobilePage = await context.newPage();
    await mobilePage.setViewportSize({ width: 390, height: 844 });
    await setupMockRoutes(mobilePage);
    await loginAsMember(mobilePage);
    await mobilePage.getByRole("navigation", { name: "주요 메뉴" }).getByRole("button", { exact: true, name: "공과금/금융상품" }).click();
    await mobilePage.getByText("공과금 · 오픈뱅킹 · 상품신청").waitFor({ timeout: 3000 });
    await mobilePage.waitForTimeout(300);
    await mobilePage.screenshot({
      path: join(rootDir, "customer-banking-enterprise-mobile.png"),
      type: "png",
    });
    await mobilePage.close();

    await browser.close();
    console.log("All screenshots successfully captured!");
  } finally {
    server.kill("SIGTERM");
  }
}

run().catch((err) => {
  console.error("Capture failed:", err);
  process.exit(1);
});
