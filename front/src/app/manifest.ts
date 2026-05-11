import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Aquila Bank 개인 인터넷뱅킹",
    short_name: "Aquila Bank",
    description: "계좌조회, 이체, 거래내역, 알림 업무를 제공하는 웹뱅킹",
    start_url: "https://bank.aquilaxk.site",
    scope: "/",
    display: "standalone",
    background_color: "#ffffff",
    theme_color: "#1157a6",
    icons: [
      {
        src: "/favicon.ico",
        sizes: "48x48",
        type: "image/x-icon",
      },
      {
        src: "/apple-touch-icon.png",
        sizes: "180x180",
        type: "image/png",
      },
    ],
  };
}
