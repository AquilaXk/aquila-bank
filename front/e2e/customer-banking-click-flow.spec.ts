import { expect, test } from "@playwright/test";

test("고객 웹뱅킹 주요 공개 업무와 미로그인 이체 가드를 실제 클릭으로 확인한다", async ({
  page,
}) => {
  await page.goto("/");

  await expect(page.getByRole("heading", { name: "뱅킹 업무" })).toBeVisible();
  await expect(page.getByText("보안등급").first()).toBeVisible();
  await expect(page.getByText("이용시간")).toBeVisible();
  await expect(page.getByText("업무현황")).toBeVisible();
  await expect(page.getByText("알림 수신 설정")).toBeVisible();

  const protectedMenuNames = [
    "조회",
    "이체",
    "거래내역 조회",
    "알림",
    "보안센터/OTP",
    "고객센터/사고신고",
    "공과금/금융상품",
  ];

  for (const menuName of protectedMenuNames) {
    await page
      .getByRole("navigation", { name: "주요 메뉴" })
      .getByRole("button", { name: menuName })
      .click();
    await expect(page.getByText("로그인이 필요한 업무")).toBeVisible();
    await expect(page.getByText("공동인증서 로그인")).toBeVisible();
    await expect(page.getByText("금융인증서 로그인")).toBeVisible();
    await expect(page.getByText("아이디 로그인")).toBeVisible();
    await expect(page.getByRole("heading", { name: "계좌조회" })).toHaveCount(0);
    await expect(page.getByRole("heading", { name: "즉시이체" })).toHaveCount(0);
    await expect(page.getByRole("heading", { name: "거래내역 조회" })).toHaveCount(0);
    await expect(page.getByText("공과금 납부", { exact: true })).toHaveCount(0);
    await expect(page.getByText("사고신고 접수", { exact: true })).toHaveCount(0);
  }

  await page.getByRole("button", { name: "인증센터 로그인" }).click();
  await expect(page.getByRole("heading", { name: "로그인 및 보안관리" })).toBeVisible();

  await page.getByLabel("통합검색").fill("OTP");
  await expect(page.getByLabel("통합검색")).toHaveValue("OTP");
});
