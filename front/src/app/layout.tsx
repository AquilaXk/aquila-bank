import type { Metadata } from "next";
import "./globals.css";
import "../styles/customer-banking.css";

// 실제 페이지가 붙기 전에도 워크스페이스 정체성이 보이도록 공통 metadata를 둡니다.
export const metadata: Metadata = {
  title: "Aquila Bank 개인 인터넷뱅킹",
  description:
    "계좌조회, 이체, 거래내역, 알림 업무를 제공하는 Aquila Bank 개인 인터넷뱅킹.",
  icons: {
    icon: "/favicon.ico",
    apple: "/apple-touch-icon.png",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
