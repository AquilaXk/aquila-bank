import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        bank: {
          bg: "var(--page)",
          surface: "var(--surface)",
          elevated: "var(--surface-muted)",
          border: "var(--line)",
          "border-subtle": "var(--line)",
          "border-hover": "var(--line-strong)",
          accent: "var(--primary)",
          "accent-soft": "var(--primary-soft)",
          "accent-strong": "var(--primary-dark)",
          muted: "var(--muted)",
          text: "var(--text)",
          heading: "var(--text)",
        },
      },
      fontFamily: {
        mono: [
          "JetBrains Mono",
          "ui-monospace",
          "SFMono-Regular",
          "Menlo",
          "Monaco",
          "Consolas",
          "monospace",
        ],
      },
      boxShadow: {
        subtle: "var(--shadow)",
      },
    },
  },
  plugins: [],
};

export default config;
