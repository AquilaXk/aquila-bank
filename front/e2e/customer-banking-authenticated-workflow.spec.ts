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

  let applicationStatus = "SUBMITTED";
  await page.route("**/api/v1/customer-service/applications**", async (route) => {
    const url = new URL(route.request().url());
    const response = {
      applicationReference: "APP-DEMO-1",
      accountId: 101,
      applicationType: "INCIDENT_REPORT",
      processingMode: "MANUAL_REVIEW",
      automatedExecutionSupported: false,
      status: applicationStatus,
      mfaVerified: true,
      mfaVerifiedAt: "2026-05-12T02:30:00Z",
      submittedAt: "2026-05-12T02:30:00Z",
      updatedAt: "2026-05-12T02:31:00Z",
      reason: applicationStatus === "CANCELLED" ? "고객 요청 취소" : null,
      processedBy: applicationStatus === "CANCELLED" ? "customer" : null,
      processedAt: applicationStatus === "CANCELLED" ? "2026-05-12T02:40:00Z" : null,
      executionResult: applicationStatus === "CANCELLED" ? { cancelled: true } : {},
    };

    if (url.pathname.endsWith("/cancel")) {
      applicationStatus = "CANCELLED";
      await route.fulfill({
        contentType: "application/json",
        json: { ...response, status: "CANCELLED", reason: "고객 요청 취소" },
      });
      return;
    }

    if (url.pathname.endsWith("/APP-DEMO-1")) {
      await route.fulfill({ contentType: "application/json", json: response });
      return;
    }

    await route.fulfill({
      contentType: "application/json",
      json: { items: [response] },
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
  await expect(page.getByLabel("조회 업무 상태")).toContainText("2건");
  await expect(page.getByLabel("계좌 상태 요약")).toContainText("거래제한");
  await expect(page.getByText("선택됨")).toBeVisible();

  await page
    .getByRole("navigation", { name: "주요 메뉴" })
    .getByRole("button", { exact: true, name: "이체" })
    .click();
  const transferForm = page.locator("form").filter({ hasText: "이체정보 입력" });
  await transferForm.getByLabel("출금계좌 ID").selectOption("101");
  await transferForm.getByLabel("입금계좌번호").fill("202");
  await transferForm.getByLabel("이체금액").fill("12000");
  await transferForm.getByLabel("받는 분 통장 표시").fill("생활비");
  await transferForm.getByRole("button", { name: "받는 분 확인" }).click();
  await expect(page.getByLabel("이체 진행 상태")).toContainText("확인");
  await transferForm.getByRole("button", { name: "받는 분 확인 완료" }).click();
  await transferForm.getByRole("button", { name: "OTP 확인" }).click();
  await transferForm.getByLabel("OTP 확인").fill("123456");
  await transferForm.getByRole("button", { name: "이체 실행" }).click();
  await expect(page.getByLabel("이체 완료증 인쇄")).toContainText("TRX-20260512-0001");
  await expect(page.locator(".print-receipt-button")).toBeVisible();
  await expect(page.getByLabel("이체 사전 확인")).toBeVisible();

  await page
    .getByRole("navigation", { name: "주요 메뉴" })
    .getByRole("button", { exact: true, name: "거래내역 조회" })
    .click();
  await expect(page.getByRole("button", { name: "상세조건 접기" })).toBeVisible();
  await page.getByRole("button", { name: "1개월" }).click();
  await page.locator("#bank-work-area").getByRole("button", { exact: true, name: "조회" }).click();
  await expect(page.getByText("TRX-20260512-0001")).toBeVisible();
  await expect(page.getByText("페이지 탐색")).toBeVisible();
  await expect(page.getByRole("button", { name: "이체확인증 출력" })).toBeVisible();
  await page.getByRole("button", { name: "초기화" }).click();
  await expect(page.getByLabel("거래 조회 상태")).toContainText("조회 전");

  await page
    .getByRole("navigation", { name: "주요 메뉴" })
    .getByRole("button", { exact: true, name: "인증센터" })
    .click();
  await page.locator("#bank-work-area").getByRole("button", { exact: true, name: "조회" }).click();
  await expect(page.getByText("Chrome Desktop")).toBeVisible();
  await expect(page.getByText("세션/기기 목록", { exact: true })).toBeVisible();
  await expect(page.getByText("보안매체 등록", { exact: true })).toBeVisible();

  await page
    .getByRole("navigation", { name: "주요 메뉴" })
    .getByRole("button", { exact: true, name: "고객센터/사고신고" })
    .click();
  await page.getByRole("button", { name: "신청 목록 새로고침" }).click();
  await expect(page.getByText("신청 현황")).toBeVisible();
  await expect(page.getByText("APP-DEMO-1")).toBeVisible();
  await page.getByRole("button", { name: /APP-DEMO-1/ }).click();
  await expect(page.getByText("신청 상세", { exact: true })).toBeVisible();
  await expect(page.getByText("처리 모드")).toBeVisible();
  await expect(page.getByText("접수 · 심사 · 처리 상태 조회")).toBeVisible();
  await page.getByRole("button", { name: "취소 요청" }).click();
  await expect(page.getByText("신청 취소 요청이 처리되었습니다.")).toBeVisible();
});

test("로그인 후 수취인 칩, 예약이체, 저축 포켓, 거래 메모, TOTP QR, 이체한도 변경, 커맨드 팔레트를 검증한다", async ({
  page,
}, testInfo) => {
  test.skip(!testInfo.project.name.includes("desktop"), "desktop 확장 플로우 E2E");

  let submittedLimitPayload: Record<string, unknown> | null = null;

  await page.route("**/api/v1/auth/login", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      json: {
        status: "SUCCESS",
        accessToken: "browser-cookie-session",
        refreshToken: null,
        tokenType: "Cookie",
        expiresAt: "2026-11-22T03:30:00Z",
        refreshExpiresAt: "2026-11-22T04:30:00Z",
        userId: 1001,
        challengeId: null,
        challengeType: null,
        challengeExpiresAt: null,
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
            balanceUpdatedAt: "2026-11-21T02:20:00Z",
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
        balanceUpdatedAt: "2026-11-21T02:20:00Z",
      },
    });
  });

  await page.route("**/api/v1/accounts/preferences", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      json: { items: [] },
    });
  });

  await page.route("**/api/v1/accounts/101/preferences", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      json: {
        accountId: 101,
        customAlias: "급여 주계좌",
        primaryAccount: true,
        updatedAt: "2026-11-21T02:25:00Z",
      },
    });
  });

  await page.route("**/api/v1/accounts/101/pockets", async (route) => {
    if (route.request().method() === "POST") {
      await route.fulfill({
        contentType: "application/json",
        json: {
          pocketId: 21,
          accountId: 101,
          pocketName: "비상금",
          targetAmountMinor: 100000000,
          savedAmountMinor: 25000000,
          currencyCode: "KRW",
          createdAt: "2026-11-21T02:25:00Z",
          updatedAt: "2026-11-21T02:25:00Z",
        },
      });
      return;
    }
    await route.fulfill({
      contentType: "application/json",
      json: {
        items: [
          {
            pocketId: 20,
            accountId: 101,
            pocketName: "여행 적금",
            targetAmountMinor: 200000000,
            savedAmountMinor: 100000000,
            currencyCode: "KRW",
            createdAt: "2026-11-20T02:25:00Z",
            updatedAt: "2026-11-20T02:25:00Z",
          },
        ],
      },
    });
  });

  await page.route("**/api/v1/transfers/recipients**", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      json: {
        items: [
          {
            recipientId: 11,
            nickname: "가족생활비",
            institutionCode: "AQUILA",
            accountNumber: "220-001-445566",
            currencyCode: "KRW",
            favorite: true,
            createdAt: "2026-11-01T00:00:00Z",
            updatedAt: "2026-11-01T00:00:00Z",
          },
        ],
      },
    });
  });

  await page.route("**/api/v1/transfers/schedules**", async (route) => {
    if (route.request().method() === "POST") {
      await route.fulfill({
        contentType: "application/json",
        json: {
          scheduleId: 31,
          sourceAccountId: 101,
          targetAccountNumber: "220-001-445566",
          amountMinor: 1000000,
          currencyCode: "KRW",
          summary: "정기저축",
          scheduleType: "MONTHLY",
          scheduledDay: 25,
          nextExecutionAt: "2026-12-25T09:00:00Z",
          ruleStatus: "ACTIVE",
          createdAt: "2026-11-21T02:30:00Z",
          updatedAt: "2026-11-21T02:30:00Z",
        },
      });
      return;
    }
    await route.fulfill({ contentType: "application/json", json: { items: [] } });
  });

  await page.route("**/api/v1/transactions/annotations**", async (route) => {
    await route.fulfill({ contentType: "application/json", json: { items: [] } });
  });

  await page.route("**/api/v1/transactions/TRX-20261121-0001/annotation", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      json: {
        accountId: 101,
        transactionReference: "TRX-20261121-0001",
        category: "FOOD",
        memo: "점심 식사 결제",
        updatedAt: "2026-11-21T03:00:00Z",
      },
    });
  });

  await page.route("**/api/v1/transactions/TRX-20261121-0001?**", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      json: {
        accountId: 101,
        transactionReference: "TRX-20261121-0001",
        direction: "DEBIT",
        transactionStatus: "BOOKED",
        amountMinor: 1200000,
        balanceAfterMinor: 348800000,
        currencyCode: "KRW",
        summary: "식당 결제",
        counterpartyMaskedName: "한식당*",
        bookedAt: "2026-11-21T02:25:00Z",
        entryReference: "ENT-20261121-0001",
        entryStatus: "POSTED",
        occurredAt: "2026-11-21T02:25:00Z",
        description: "점심 결제",
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
            accountId: 101,
            transactionReference: "TRX-20261121-0001",
            direction: "DEBIT",
            status: "BOOKED",
            amountMinor: 1200000,
            currencyCode: "KRW",
            balanceAfterMinor: 348800000,
            summary: "식당 결제",
            counterpartyMaskedName: "한식당*",
            bookedAt: "2026-11-21T02:25:00Z",
          },
        ],
        nextCursor: null,
        hasNext: false,
        limit: 50,
      },
    });
  });

  await page.route("**/api/v1/auth/mfa/totp/enroll", async (route) => {
    await route.fulfill({
      contentType: "application/json",
      json: {
        secretKey: "JBSWY3DPEHPK3PXP",
        otpauthUri: "otpauth://totp/AquilaBank:customer01?secret=JBSWY3DPEHPK3PXP&issuer=AquilaBank",
        expiresAt: "2026-11-22T00:00:00Z",
        status: "PENDING_VERIFICATION",
      },
    });
  });

  await page.route("**/api/v1/customer-service/applications**", async (route) => {
    if (route.request().method() === "POST") {
      submittedLimitPayload = route.request().postDataJSON() as Record<string, unknown>;
      await route.fulfill({
        contentType: "application/json",
        json: {
          applicationReference: "APP-LIMIT-99",
          accountId: 101,
          applicationType: "TRANSFER_LIMIT_CHANGE",
          processingMode: "MANUAL_REVIEW",
          automatedExecutionSupported: false,
          status: "SUBMITTED",
          submittedAt: "2026-11-21T03:10:00Z",
        },
      });
      return;
    }
    await route.fulfill({ contentType: "application/json", json: { items: [] } });
  });

  await page.goto("/");
  await page.getByRole("button", { name: "인증센터" }).first().click();
  const loginForm = page.locator("form").filter({ hasText: "아이디와 비밀번호" });
  await loginForm.getByLabel("고객 ID").fill("customer01");
  await loginForm.getByLabel("비밀번호").fill("Password1!");
  await loginForm.getByRole("button", { exact: true, name: "로그인" }).click();
  await expect(page.locator(".bank-layout")).toHaveAttribute("data-auth-state", "member");

  // 금액 숨김 토글 및 커맨드 팔레트 열기/닫기
  await page.getByRole("button", { name: "금액 숨김" }).click();
  await expect(page.getByRole("button", { name: "금액 표시" })).toBeVisible();
  await page.getByRole("button", { name: "빠른 메뉴 팔레트 열기" }).click();
  await expect(page.getByRole("textbox", { name: "빠른 메뉴 및 계좌 검색" }).or(page.getByLabel("빠른 메뉴 및 계좌 검색"))).toBeVisible();
  await page.keyboard.press("Escape");

  // 계좌 상세 + 저축 포켓 + 개인화 저장
  await page.getByRole("navigation", { name: "주요 메뉴" }).getByRole("button", { exact: true, name: "조회" }).click();
  await expect(page.getByText("여행 적금")).toBeVisible();
  await page.getByLabel("계좌 별칭").fill("급여 주계좌");
  await page.getByRole("button", { name: "계좌 설정 저장" }).click();
  await expect(page.getByText("대표계좌").first()).toBeVisible();

  // 이체 수취인 칩 클릭 및 예약·자동이체 탭
  await page.getByRole("navigation", { name: "주요 메뉴" }).getByRole("button", { exact: true, name: "이체" }).click();
  await page.getByLabel("자주 쓰는 계좌").getByRole("button", { name: /가족생활비/ }).click();
  await expect(page.getByLabel("입금계좌번호")).toHaveValue("220-001-445566");
  await page.getByRole("tab", { name: "예약·자동이체" }).click();
  await page.getByLabel("입금계좌번호").fill("220-001-445566");
  await page.getByLabel("예약 이체금액").fill("10000");
  await page.getByLabel("받는 분 통장 표시").fill("정기저축");
  await page.getByLabel("OTP 인증번호").fill("123456");
  await page.getByRole("button", { name: "예약이체 등록" }).click();
  await expect(page.getByText("정기저축").first()).toBeVisible();

  // 거래내역 메모/분류 저장 + CSV 내보내기 버튼 활성화
  await page.getByRole("navigation", { name: "주요 메뉴" }).getByRole("button", { exact: true, name: "거래내역 조회" }).click();
  await page.locator("#bank-work-area").getByRole("button", { exact: true, name: "조회" }).click();
  await expect(page.getByRole("button", { name: "CSV 내보내기" })).toBeEnabled();
  await page.getByRole("button", { exact: true, name: "상세" }).click();
  await page.getByLabel("메모").fill("점심 식사 결제");
  await page.getByRole("button", { name: "메모 저장" }).click();
  await expect(page.getByText("점심 식사 결제").first()).toBeVisible();

  // 인증센터 TOTP SVG QR 코드 렌더링
  await page.getByRole("navigation", { name: "주요 메뉴" }).getByRole("button", { exact: true, name: "인증센터" }).click();
  await page.getByRole("button", { name: "등록 시작" }).click();
  await expect(page.getByRole("img", { name: "OTP 등록 QR 코드" })).toBeVisible();

  // 고객센터 이체한도 변경 신청
  await page.getByRole("navigation", { name: "주요 메뉴" }).getByRole("button", { exact: true, name: "고객센터/사고신고" }).click();
  await page.getByRole("button", { name: "이체한도변경" }).click();
  const limitCard = page.locator(".limit-change-form");
  await limitCard.getByLabel("OTP").fill("123456");
  await limitCard.getByRole("button", { name: "이체한도 변경 접수" }).click();
  await expect(page.locator(".application-result-line")).toContainText("APP-LIMIT-99");
  expect(submittedLimitPayload?.applicationType).toBe("TRANSFER_LIMIT_CHANGE");
});
