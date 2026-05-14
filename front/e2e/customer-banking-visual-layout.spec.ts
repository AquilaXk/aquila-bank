import { expect, test } from "@playwright/test";

async function expectNoHorizontalOverflow(page: import("@playwright/test").Page) {
  const overflow = await page.evaluate(() => {
    const root = document.documentElement;
    return root.scrollWidth - root.clientWidth;
  });
  expect(overflow).toBeLessThanOrEqual(1);
}

async function expectNoTextClipping(
  page: import("@playwright/test").Page,
  selector: string,
) {
  const clipped = await page.locator(selector).evaluateAll((items) =>
    items
      .filter((item) => {
        const element = item as HTMLElement;
        return (
          element.scrollWidth > element.clientWidth + 1 ||
          element.scrollHeight > element.clientHeight + 1
        );
      })
      .map((item) => item.textContent?.trim() ?? ""),
  );
  expect(clipped).toEqual([]);
}

test("desktop 공개 화면은 3열 업무형 구조와 미로그인 보호 업무 가드를 유지한다", async ({
  page,
}, testInfo) => {
  test.skip(!testInfo.project.name.includes("desktop"), "desktop 전용 visual QA");

  await page.goto("/");
  await expect(page).toHaveTitle(/Aquila Bank 개인 인터넷뱅킹/);
  await expect(page.locator(".bank-layout")).toHaveAttribute("data-auth-state", "guest");
  await expect(page.locator(".bank-layout")).toHaveAttribute("data-active-section", "dashboard");
  await expect(page.locator(".side-menu")).toBeVisible();
  await expect(page.locator(".work-area")).toBeVisible();
  await expect(page.locator(".right-rail")).toBeVisible();

  const sideBox = await page.locator(".side-menu").boundingBox();
  const workBox = await page.locator(".work-area").boundingBox();
  const railBox = await page.locator(".right-rail").boundingBox();
  expect(sideBox?.x ?? 0).toBeLessThan(workBox?.x ?? 0);
  expect(workBox?.x ?? 0).toBeLessThan(railBox?.x ?? 0);
  expect(Math.abs((sideBox?.y ?? 0) - (workBox?.y ?? 0))).toBeLessThanOrEqual(2);

  await expectNoHorizontalOverflow(page);
  await page.screenshot({
    fullPage: true,
    path: testInfo.outputPath("customer-banking-desktop-home.png"),
  });

  await page
    .getByRole("navigation", { name: "주요 메뉴" })
    .getByRole("button", { exact: true, name: "이체" })
    .click();
  await expect(page.locator(".bank-layout")).toHaveAttribute("data-active-section", "transfer");
  await expect(page.getByLabel("로그인 필요 안내")).toBeVisible();
  await expect(page.getByRole("heading", { name: "즉시이체" })).toHaveCount(0);
  await expect(page.locator(".bank-form").filter({ hasText: "이체정보 입력" })).toHaveCount(0);
});

test("mobile 공개 화면은 단일 흐름으로 접히고 키보드 접근 순서를 제공한다", async ({
  page,
}, testInfo) => {
  test.skip(!testInfo.project.name.includes("mobile"), "mobile 전용 visual QA");

  await page.goto("/");
  await expect(page.locator(".bank-layout")).toHaveAttribute("data-auth-state", "guest");
  await expectNoHorizontalOverflow(page);

  const sideBox = await page.locator(".side-menu").boundingBox();
  const workBox = await page.locator(".work-area").boundingBox();
  const railBox = await page.locator(".right-rail").boundingBox();
  const hasMobilePriorityWork = (await page.locator(".mobile-priority-work").count()) > 0;
  if (hasMobilePriorityWork) {
    expect(workBox?.y ?? 0).toBeLessThan(sideBox?.y ?? 0);
    expect(workBox?.y ?? 0).toBeLessThan(railBox?.y ?? 0);
    await expect(page.getByRole("link", { name: "모바일 업무 바로가기" })).toBeVisible();
  } else {
    expect(sideBox?.y ?? 0).toBeLessThan(workBox?.y ?? 0);
  }

  await page.keyboard.press("Tab");
  await expect(page.locator(".skip-link")).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.locator("#bank-work-area")).toBeFocused();

  await page.getByRole("button", { name: "전체서비스" }).click();
  await expect(page.getByRole("button", { name: "전체서비스" })).toHaveAttribute(
    "aria-expanded",
    "true",
  );
  await expect(page.getByLabel("전체서비스 메뉴")).toBeVisible();

  await page.screenshot({
    fullPage: true,
    path: testInfo.outputPath("customer-banking-mobile-home.png"),
  });
});

test("tablet compact 폭에서도 검색, 메뉴, 보안알림 텍스트가 컨테이너 안에 머문다", async ({
  page,
}, testInfo) => {
  test.skip(!testInfo.project.name.includes("desktop"), "tablet 폭 visual QA");

  await page.setViewportSize({ width: 820, height: 900 });
  await page.goto("/");
  await expectNoHorizontalOverflow(page);
  await expect(page.getByLabel("추천검색어")).toBeVisible();
  await expect(page.getByLabel("개인뱅킹 메뉴")).toBeVisible();
  await expect(page.getByText("보안알림")).toBeVisible();

  await expectNoTextClipping(page, ".keyword-list button");
  await expectNoTextClipping(page, ".side-menu .side-item span");
  await expectNoTextClipping(page, ".side-menu .side-item strong");
  await expectNoTextClipping(page, ".security-notice .bank-notice-strip dt");
  await expectNoTextClipping(page, ".security-notice .bank-notice-strip dd");

  await page.screenshot({
    fullPage: true,
    path: testInfo.outputPath("customer-banking-tablet-compact-home.png"),
  });
});
