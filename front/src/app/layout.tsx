import type { Metadata } from "next";
import "./globals.css";

// 실제 페이지가 붙기 전에도 워크스페이스 정체성이 보이도록 공통 metadata를 둡니다.
export const metadata: Metadata = {
  title: "Aquila Bank",
  description:
    "Real-time notifications and high-volume transaction lookup workspace.",
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
