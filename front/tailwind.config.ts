import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        bank: {
          bg: "#0B0D13",
          surface: "#121622",
          elevated: "#181D2D",
          border: "rgba(255, 255, 255, 0.08)",
          "border-subtle": "rgba(255, 255, 255, 0.04)",
          "border-hover": "rgba(167, 139, 250, 0.3)",
          accent: "#8B5CF6",
          "accent-glow": "rgba(139, 92, 246, 0.15)",
          "accent-light": "#A78BFA",
          muted: "#94A3B8",
          text: "#F8FAFC",
          heading: "#FFFFFF",
        },
        lavender: {
          50: "#FAF5FF",
          100: "#F3E8FF",
          200: "#E9D5FF",
          300: "#D8B4FE",
          400: "#C084FC",
          500: "#A855F7",
          600: "#9333EA",
          700: "#7E22CE",
          800: "#6B21A8",
          900: "#581C87",
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
      backgroundImage: {
        "micro-grid":
          "radial-gradient(circle, rgba(255, 255, 255, 0.07) 1px, transparent 1px)",
      },
      boxShadow: {
        glow: "0 0 25px -5px rgba(139, 92, 246, 0.25)",
        "glow-card":
          "0 4px 20px -2px rgba(0, 0, 0, 0.5), 0 0 15px -3px rgba(167, 139, 250, 0.12)",
        "glow-hover":
          "0 8px 30px -4px rgba(0, 0, 0, 0.6), 0 0 25px -2px rgba(167, 139, 250, 0.22)",
      },
    },
  },
  plugins: [],
};

export default config;
