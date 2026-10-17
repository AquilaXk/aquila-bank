import type { Metadata, Viewport } from "next";
import "./globals.css";
import "../styles/customer-banking.css";
import "../styles/ops-console.css";

// 실제 페이지가 붙기 전에도 워크스페이스 정체성이 보이도록 공통 metadata를 둡니다.
export const metadata: Metadata = {
  applicationName: "Aquila Bank",
  metadataBase: new URL("https://bank.aquilaxk.site"),
  title: "Aquila Bank 개인 인터넷뱅킹",
  description:
    "계좌조회, 이체, 거래내역, 알림 업무를 제공하는 Aquila Bank 개인 인터넷뱅킹.",
  openGraph: {
    title: "Aquila Bank 개인 인터넷뱅킹",
    description:
      "계좌조회, 이체, 거래내역, 알림 업무를 제공하는 Aquila Bank 개인 인터넷뱅킹.",
    siteName: "Aquila Bank",
    type: "website",
    url: "https://bank.aquilaxk.site",
  },
  robots: {
    index: false,
    follow: false,
  },
  icons: {
    icon: "/favicon.ico",
    apple: "/apple-touch-icon.png",
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  colorScheme: "light dark",
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f8f9fc" },
    { media: "(prefers-color-scheme: dark)", color: "#121317" },
  ],
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" suppressHydrationWarning>
      <head>
        <meta name="color-scheme" content="light dark" />
        <script
          dangerouslySetInnerHTML={{
            __html: `(function(){try{var s=localStorage.getItem("color-scheme");var m=document.querySelector('meta[name="color-scheme"]');if(m&&(s==="light"||s==="dark")){m.content=s;document.documentElement.dataset.theme=s;}else{document.documentElement.removeAttribute("data-theme");}}catch(e){}})();`,
          }}
        />
      </head>
      <body className="min-h-screen antialiased">
        {children}
      </body>
    </html>
  );
}
