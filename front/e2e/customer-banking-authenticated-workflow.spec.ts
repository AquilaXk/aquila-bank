import { expect, test } from "@playwright/test";

test("로그인 후 계좌, 이체, 거래내역, 세션 관리 업무 화면을 실제 클릭 흐름으로 확인한다", async ({
  page,
}) => {
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

  await page.route("**/api/v1/transfers", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      json: {
        transactionReference: "TRX-20260512-0001",
        sourceAccountId: 101,
        targetAccountId: 202,
        amountMinor: 1200000,
        currencyCode: "KRW",
        availableBalanceAfterMinor: 348800000,
        bookedAt: "2026-05-12T02:25:00Z",
        status: "BOOKED",
      },
    });
  });

  await page.route("**/api/v1/transactions?**", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      json: {
        items: [
          {
            id: 901,
            transactionReference: "TRX-20260512-0001",
            direction: "DEBIT",
            status: "BOOKED",
            amountMinor: 1200000,
            currencyCode: "KRW",
            balanceAfterMinor: 348800000,
            bookedAt: "2026-05-12T02:25:00Z",
          },
        ],
        nextCursor: null,
        hasNext: false,
      },
    });
  });

  await page.goto("/");
  await page.getByRole("button", { name: "인증센터" }).first().click();
  const loginForm = page.locator("form").filter({ hasText: "아이디와 비밀번호" });
  await loginForm.getByLabel("고객 ID").fill("customer01");
  await loginForm.getByLabel("비밀번호").fill("Password1!");
  await loginForm.getByRole("button", { exact: true, name: "로그인" }).click();
  await expect(page.locator(".bank-layout")).toHaveAttribute("data-auth-state", "member");

  await page
    .getByRole("navigation", { name: "주요 메뉴" })
    .getByRole("button", { exact: true, name: "조회" })
    .click();
  await page.locator("#bank-work-area").getByRole("button", { exact: true, name: "조회" }).click();
  await expect(page.getByRole("cell", { name: "Aquila 주거래통장" })).toBeVisible();
  await expect(page.getByLabel("조회 업무 상태")).toContainText("1건");

  await page
    .getByRole("navigation", { name: "주요 메뉴" })
    .getByRole("button", { exact: true, name: "이체" })
    .click();
  const transferForm = page.locator("form").filter({ hasText: "이체정보 입력" });
  await transferForm.getByLabel("출금계좌 ID").fill("101");
  await transferForm.getByLabel("입금계좌 ID").fill("202");
  await transferForm.getByLabel("이체금액").fill("1200000");
  await transferForm.getByLabel("받는 분 통장 표시").fill("생활비");
  await transferForm.getByRole("button", { name: "받는 분 확인" }).click();
  await expect(page.getByLabel("이체 진행 상태")).toContainText("확인");
  await transferForm.getByRole("button", { name: "받는 분 확인 완료" }).click();
  await transferForm.getByRole("button", { name: "OTP 확인" }).click();
  await transferForm.getByLabel("OTP 확인").fill("123456");
  await transferForm.getByRole("button", { name: "이체 실행" }).click();
  await expect(page.getByLabel("이체 완료증 인쇄")).toContainText("TRX-20260512-0001");
  await expect(page.locator(".print-receipt-button")).toBeVisible();

  await page
    .getByRole("navigation", { name: "주요 메뉴" })
    .getByRole("button", { exact: true, name: "거래내역 조회" })
    .click();
  await expect(page.getByRole("button", { name: "상세조건 접기" })).toBeVisible();
  await page.getByRole("button", { name: "1개월" }).click();
  await page.locator("#bank-work-area").getByRole("button", { exact: true, name: "조회" }).click();
  await expect(page.getByText("TRX-20260512-0001")).toBeVisible();
  await page.getByRole("button", { name: "초기화" }).click();
  await expect(page.getByLabel("거래 조회 상태")).toContainText("조회 전");

  await page
    .getByRole("navigation", { name: "주요 메뉴" })
    .getByRole("button", { exact: true, name: "인증센터" })
    .click();
  await page.locator("#bank-work-area").getByRole("button", { exact: true, name: "조회" }).click();
  await expect(page.getByText("Chrome Desktop")).toBeVisible();
});
