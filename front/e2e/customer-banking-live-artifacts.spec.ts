import { expect, test } from "@playwright/test";

async function expectVisibleBox(page: import("@playwright/test").Page, selector: string) {
  const box = await page.locator(selector).boundingBox();
  expect(box?.width ?? 0).toBeGreaterThan(100);
  expect(box?.height ?? 0).toBeGreaterThan(40);
}

test("live desktop 화면은 주요 영역 screenshot artifact를 남긴다", async ({
  page,
}, testInfo) => {
  test.skip(!testInfo.project.name.includes("desktop"), "desktop live artifact 전용");

  await page.goto("/");
  await expect(page).toHaveTitle(/Aquila Bank 개인 인터넷뱅킹/);
  await expect(page.locator(".bank-layout")).toHaveAttribute("data-auth-state", "guest");
  await expectVisibleBox(page, ".bank-header");
  await expectVisibleBox(page, ".side-menu");
  await expectVisibleBox(page, ".work-area");
  await expectVisibleBox(page, ".right-rail");

  await page.screenshot({
    fullPage: true,
    path: testInfo.outputPath("customer-banking-live-desktop.png"),
  });
});

test("live mobile 화면은 접힌 업무 구조 screenshot artifact를 남긴다", async ({
  page,
}, testInfo) => {
  test.skip(!testInfo.project.name.includes("mobile"), "mobile live artifact 전용");

  await page.goto("/");
  await expect(page.locator(".bank-layout")).toHaveAttribute("data-auth-state", "guest");
  await expectVisibleBox(page, ".bank-header");
  await expect(page.getByRole("button", { exact: true, name: "검색" })).toBeVisible();
  await page.getByRole("button", { exact: true, name: "검색" }).click();
  await expect(page.getByText("추천검색어")).toBeVisible();

  await page.screenshot({
    fullPage: true,
    path: testInfo.outputPath("customer-banking-live-mobile.png"),
  });
});
