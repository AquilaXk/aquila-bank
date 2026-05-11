import { expect, test } from "@playwright/test";

test("고객 웹뱅킹 주요 공개 업무와 미로그인 이체 가드를 실제 클릭으로 확인한다", async ({
  page,
}) => {
  await page.goto("/");

  await expect(page.getByRole("heading", { name: "뱅킹 업무" })).toBeVisible();
  await page.getByRole("navigation", { name: "주요 메뉴" }).getByRole("button", { name: "이체" }).click();
  await expect(page.getByText("권한 만료 또는 미로그인")).toBeVisible();

  await page.getByRole("button", { name: "인증센터로 이동" }).click();
  await expect(page.getByRole("heading", { name: "로그인 및 보안관리" })).toBeVisible();

  await page
    .getByRole("navigation", { name: "주요 메뉴" })
    .getByRole("button", { name: "보안센터/OTP" })
    .click();
  await expect(page.getByText("공동인증서 등록")).toBeVisible();
  await expect(page.getByText("금융인증서 등록")).toBeVisible();
  await expect(page.getByText("보안매체 등록")).toBeVisible();

  await page
    .getByRole("navigation", { name: "주요 메뉴" })
    .getByRole("button", { name: "공과금/금융상품" })
    .click();
  await expect(page.getByText("공과금 상세")).toBeVisible();
  await expect(page.getByText("오픈뱅킹 상세")).toBeVisible();
  await expect(page.getByText("외환 상세")).toBeVisible();

  await page
    .getByRole("navigation", { name: "주요 메뉴" })
    .getByRole("button", { name: "고객센터/사고신고" })
    .click();
  await expect(page.getByText("사고신고 접수", { exact: true })).toBeVisible();
  await expect(page.getByText("이체확인증", { exact: true })).toBeVisible();
  await page.getByText("FAQ 이체확인증은 어디서 발급하나요?").click();
  await expect(page.getByText("이체 완료 후 완료증 영역")).toBeVisible();

  await page.getByLabel("통합검색").fill("OTP");
  await expect(page.getByLabel("통합검색")).toHaveValue("OTP");
});
