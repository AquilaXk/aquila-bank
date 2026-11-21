/*
 * Lucide Stroke Icon Set (React inline SVG adaptation for Aquila Bank)
 *
 * 참조 구현: lucide-icons/lucide (icons/*.svg)
 * Copyright (c) 2026 Lucide Icons and Contributors (ISC License)
 * Derived from Feather icons: Copyright (c) 2013-present Cole Bemis (MIT License)
 * https://github.com/lucide-icons/lucide
 */

import type { SVGProps } from "react";
import type { MenuSection } from "@/lib/customer-banking/types";

export type IconProps = SVGProps<SVGSVGElement> & {
  size?: number;
};

function createLucideBaseProps(size: number, props: SVGProps<SVGSVGElement>): SVGProps<SVGSVGElement> {
  return {
    "aria-hidden": true,
    fill: "none",
    focusable: false,
    height: size,
    stroke: "currentColor",
    strokeLinecap: "round",
    strokeLinejoin: "round",
    strokeWidth: 1.9,
    viewBox: "0 0 24 24",
    width: size,
    ...props,
  };
}

export function LandmarkIcon({ size = 18, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="M10 18v-7" />
      <path d="M11.119 2.205a2 2 0 0 1 1.762 0l7.84 3.846A.5.5 0 0 1 20.5 7h-17a.5.5 0 0 1-.22-.949z" />
      <path d="M14 18v-7" />
      <path d="M18 18v-7" />
      <path d="M3 22h18" />
      <path d="M6 18v-7" />
    </svg>
  );
}

export function WalletIcon({ size = 18, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1" />
      <path d="M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4" />
    </svg>
  );
}

export function ArrowUpRightIcon({ size = 18, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="M7 7h10v10" />
      <path d="M7 17 17 7" />
    </svg>
  );
}

export function ArrowDownLeftIcon({ size = 18, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="M17 7 7 17" />
      <path d="M17 17H7V7" />
    </svg>
  );
}

export function ArrowLeftRightIcon({ size = 18, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="M8 3 4 7l4 4" />
      <path d="M4 7h16" />
      <path d="m16 21 4-4-4-4" />
      <path d="M20 17H4" />
    </svg>
  );
}

export function ReceiptTextIcon({ size = 18, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="M13 16H8" />
      <path d="M14 8H8" />
      <path d="M16 12H8" />
      <path d="M4 3a1 1 0 0 1 1-1 1.3 1.3 0 0 1 .7.2l.933.6a1.3 1.3 0 0 0 1.4 0l.934-.6a1.3 1.3 0 0 1 1.4 0l.933.6a1.3 1.3 0 0 0 1.4 0l.933-.6a1.3 1.3 0 0 1 1.4 0l.934.6a1.3 1.3 0 0 0 1.4 0l.933-.6A1.3 1.3 0 0 1 19 2a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1 1.3 1.3 0 0 1-.7-.2l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.934.6a1.3 1.3 0 0 1-1.4 0l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-1.4 0l-.934-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-.7.2 1 1 0 0 1-1-1z" />
    </svg>
  );
}

export function ShieldCheckIcon({ size = 18, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}

export function BellIcon({ size = 16, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="M10.268 21a2 2 0 0 0 3.464 0" />
      <path d="M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326" />
    </svg>
  );
}

export function SearchIcon({ size = 16, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="m21 21-4.34-4.34" />
      <circle cx="11" cy="11" r="8" />
    </svg>
  );
}

export function CommandIcon({ size = 15, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="M15 6v12a3 3 0 1 0 3-3H6a3 3 0 1 0 3 3V6a3 3 0 1 0-3 3h12a3 3 0 1 0-3-3" />
    </svg>
  );
}

export function EyeIcon({ size = 15, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

export function EyeOffIcon({ size = 15, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49" />
      <path d="M14.084 14.158a3 3 0 0 1-4.242-4.242" />
      <path d="M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143" />
      <path d="m2 2 20 20" />
    </svg>
  );
}

export function SunIcon({ size = 15, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2" />
      <path d="M12 20v2" />
      <path d="m4.93 4.93 1.41 1.41" />
      <path d="m17.66 17.66 1.41 1.41" />
      <path d="M2 12h2" />
      <path d="M20 12h2" />
      <path d="m6.34 17.66-1.41 1.41" />
      <path d="m19.07 4.93-1.41 1.41" />
    </svg>
  );
}

export function MoonIcon({ size = 15, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401" />
    </svg>
  );
}

export function PiggyBankIcon({ size = 18, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="M11 17h3v2a1 1 0 0 0 1 1h2a1 1 0 0 0 1-1v-3a3.16 3.16 0 0 0 2-2h1a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1h-1a5 5 0 0 0-2-4V3a4 4 0 0 0-3.2 1.6l-.3.4H11a6 6 0 0 0-6 6v1a5 5 0 0 0 2 4v3a1 1 0 0 0 1 1h2a1 1 0 0 0 1-1z" />
      <path d="M16 10h.01" />
      <path d="M2 8v1a2 2 0 0 0 2 2h1" />
    </svg>
  );
}

export function CalendarClockIcon({ size = 17, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="M16 2v3" />
      <path d="M17 15.5v1.7l1 .625" />
      <path d="M21 8.936V5a2 2 0 00-2-2H5a2 2 0 00-2 2v14a2 2 0 002 2h3.935" />
      <path d="M3 9h9.873" />
      <path d="M8 2v3" />
      <circle cx="17" cy="17" r="5" />
    </svg>
  );
}

export function DownloadIcon({ size = 16, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="M12 15V3" />
      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
      <path d="m7 10 5 5 5-5" />
    </svg>
  );
}

export function HeadphonesIcon({ size = 17, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="M3 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a9 9 0 0 1 18 0v7a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3" />
    </svg>
  );
}

export function CreditCardIcon({ size = 18, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <rect width="20" height="14" x="2" y="5" rx="2" />
      <line x1="2" x2="22" y1="10" y2="10" />
      <path d="M6 14h2" />
    </svg>
  );
}

export function LockIcon({ size = 17, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <rect width="18" height="11" x="3" y="11" rx="2" ry="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </svg>
  );
}

export function QrCodeIcon({ size = 17, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <rect width="5" height="5" x="3" y="3" rx="1" />
      <rect width="5" height="5" x="16" y="3" rx="1" />
      <rect width="5" height="5" x="3" y="16" rx="1" />
      <path d="M21 16h-3a2 2 0 0 0-2 2v3" />
      <path d="M21 21v.01" />
      <path d="M12 7v3a2 2 0 0 1-2 2H7" />
      <path d="M3 12h.01" />
      <path d="M12 3h.01" />
      <path d="M12 16v.01" />
      <path d="M16 12h1" />
      <path d="M21 12v.01" />
      <path d="M12 21v-1" />
    </svg>
  );
}

export function StarIcon({ size = 14, filled = false, ...props }: IconProps & { filled?: boolean }) {
  return (
    <svg
      {...createLucideBaseProps(size, props)}
      fill={filled ? "currentColor" : "none"}
    >
      <path d="M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z" />
    </svg>
  );
}

export function SparklesIcon({ size = 16, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z" />
      <path d="M20 2v4" />
      <path d="M22 4h-4" />
      <circle cx="4" cy="20" r="2" />
    </svg>
  );
}

export function SlidersHorizontalIcon({ size = 16, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="M10 5H3" />
      <path d="M12 19H3" />
      <path d="M14 3v4" />
      <path d="M16 17v4" />
      <path d="M21 12h-9" />
      <path d="M21 19h-5" />
      <path d="M21 5h-7" />
      <path d="M8 10v4" />
      <path d="M8 12H3" />
    </svg>
  );
}

export function ChevronRightIcon({ size = 16, ...props }: IconProps) {
  return (
    <svg {...createLucideBaseProps(size, props)}>
      <path d="m9 18 6-6-6-6" />
    </svg>
  );
}

export function EmvChipIcon({ size = 34 }: { size?: number }) {
  return (
    <svg
      aria-hidden="true"
      focusable="false"
      height={Math.round(size * 0.72)}
      viewBox="0 0 36 26"
      width={size}
    >
      <rect
        fill="rgba(255, 255, 255, 0.22)"
        height="24"
        rx="5"
        stroke="rgba(255, 255, 255, 0.55)"
        strokeWidth="1.2"
        width="34"
        x="1"
        y="1"
      />
      <path
        d="M1 9h10v8H1M25 9h10v8H25M12 1v24M24 1v24M12 13h12"
        fill="none"
        stroke="rgba(255, 255, 255, 0.45)"
        strokeWidth="1.1"
      />
    </svg>
  );
}

export function NfcWaveIcon({ size = 20 }: { size?: number }) {
  return (
    <svg
      aria-hidden="true"
      fill="none"
      focusable="false"
      height={size}
      stroke="currentColor"
      strokeLinecap="round"
      strokeWidth="1.8"
      viewBox="0 0 24 24"
      width={size}
    >
      <path d="M6 16a6 6 0 0 0 0-8" opacity="0.55" />
      <path d="M10 18.5a9.5 9.5 0 0 0 0-13" opacity="0.75" />
      <path d="M14.5 21a13 13 0 0 0 0-18" />
    </svg>
  );
}

export function MenuSectionIcon({
  section,
  size = 16,
  className,
}: {
  section: MenuSection;
  size?: number;
  className?: string;
}) {
  switch (section) {
    case "dashboard":
      return <LandmarkIcon className={className} size={size} />;
    case "accounts":
      return <WalletIcon className={className} size={size} />;
    case "transfer":
      return <ArrowLeftRightIcon className={className} size={size} />;
    case "transactions":
      return <ReceiptTextIcon className={className} size={size} />;
    case "notifications":
      return <BellIcon className={className} size={size} />;
    case "security":
      return <ShieldCheckIcon className={className} size={size} />;
    case "securityHub":
      return <QrCodeIcon className={className} size={size} />;
    case "supportCenter":
      return <HeadphonesIcon className={className} size={size} />;
    case "enterpriseServices":
      return <CreditCardIcon className={className} size={size} />;
    default:
      return <LandmarkIcon className={className} size={size} />;
  }
}
