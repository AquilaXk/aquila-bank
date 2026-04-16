import type { Metadata } from "next";
import "./globals.css";

// Shared metadata keeps the initial workspace shell recognizable before real pages exist.
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
