/*
 * First-Party Geometric Banking Icon Set (Clean-Room SVG Primitives)
 *
 * Authored on a 20x20 integer-friendly stroke grid for Aquila Bank.
 * Consolidates stroke subpaths per glyph to minimize React VDOM nodes and bundle size.
 */

import type { ReactNode } from "react";
import type { MenuSection } from "@/lib/customer-banking/types";

export type IconProps = {
  size?: number;
  className?: string;
};

function SvgIcon({
  size,
  className,
  filled = false,
  children,
}: {
  size: number;
  className?: string;
  filled?: boolean;
  children: ReactNode;
}) {
  return (
    <svg
      aria-hidden="true"
      className={className ? `ab-icon ${className}` : "ab-icon"}
      fill={filled ? "currentColor" : "none"}
      focusable="false"
      height={size}
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={1.75}
      viewBox="0 0 20 20"
      width={size}
    >
      {children}
    </svg>
  );
}

export function LandmarkIcon({ size = 18, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <path d="M2.5 7.5 10 3l7.5 4.5ZM3.5 14.5h13M2.5 17h15M5 7.5v7M8.3 7.5v7M11.7 7.5v7M15 7.5v7" />
    </SvgIcon>
  );
}

export function WalletIcon({ size = 18, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <rect height="11.5" rx="2" width="15" x="2.5" y="5" />
      <path d="M4.5 5V3.8A1.3 1.3 0 0 1 5.8 2.5H14.5" />
      <rect height="3.5" rx="1" width="4.5" x="13" y="9" />
    </SvgIcon>
  );
}

export function ArrowUpRightIcon({ size = 18, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <path d="M5.5 14.5 14.5 5.5M7.5 5.5h7v7" />
    </SvgIcon>
  );
}

export function ArrowDownLeftIcon({ size = 18, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <path d="M14.5 5.5 5.5 14.5M12.5 14.5h-7v-7" />
    </SvgIcon>
  );
}

export function ArrowLeftRightIcon({ size = 18, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <path d="M3.5 6.5h13M6.5 3.5l-3 3 3 3M16.5 13.5h-13M13.5 10.5l3 3-3 3" />
    </SvgIcon>
  );
}

export function ReceiptTextIcon({ size = 18, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <rect height="15" rx="1.8" width="12" x="4" y="2.5" />
      <path d="M7 7h6M7 10h6M7 13h4" />
    </SvgIcon>
  );
}

export function ShieldCheckIcon({ size = 18, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <path d="M10 2.2 16 4.6v5.2c0 3.9-2.5 6.6-6 8-3.5-1.4-6-4.1-6-8V4.6ZM7.4 10.2 9.2 12l3.6-3.6" />
    </SvgIcon>
  );
}

export function BellIcon({ size = 16, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <path d="M5.5 8.5a4.5 4.5 0 0 1 9 0c0 3.2 1.2 4.4 1.8 5.2H3.7c.6-.8 1.8-2 1.8-5.2ZM8.5 15.8a1.6 1.6 0 0 0 3 0" />
    </SvgIcon>
  );
}

export function SearchIcon({ size = 16, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <circle cx="9" cy="9" r="5.8" />
      <path d="m13.2 13.2 4 4" />
    </SvgIcon>
  );
}

export function CommandIcon({ size = 15, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <path d="M7.5 7.5V4.8a1.8 1.8 0 1 0-1.8 1.8h8.6a1.8 1.8 0 1 0-1.8-1.8v10.4a1.8 1.8 0 1 0 1.8-1.8H5.7a1.8 1.8 0 1 0 1.8 1.8Z" />
    </SvgIcon>
  );
}

export function EyeIcon({ size = 15, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <path d="M2 10s3-5.2 8-5.2S18 10 18 10s-3 5.2-8 5.2S2 10 2 10Z" />
      <circle cx="10" cy="10" r="2.3" />
    </SvgIcon>
  );
}

export function EyeOffIcon({ size = 15, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <path d="M4 6.5C5.8 5 7.8 4.8 10 4.8c5 0 8 5.2 8 5.2a13.2 13.2 0 0 1-2.5 3M12.2 14.8A7.6 7.6 0 0 1 10 15.2c-5 0-8-5.2-8-5.2a14 14 0 0 1 1.6-2.2M3 3l14 14" />
    </SvgIcon>
  );
}

export function SunIcon({ size = 15, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <circle cx="10" cy="10" r="3.4" />
      <path d="M10 2.2V4M10 16v1.8M2.2 10H4M16 10h1.8M4.5 4.5l1.3 1.3M14.2 14.2l1.3 1.3M15.5 4.5l-1.3 1.3M5.8 14.2l-1.3 1.3" />
    </SvgIcon>
  );
}

export function MoonIcon({ size = 15, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <path d="M15.8 11.8A6.2 6.2 0 0 1 8.2 4.2a6.4 6.4 0 1 0 7.6 7.6Z" />
    </SvgIcon>
  );
}

export function PiggyBankIcon({ size = 18, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <circle cx="10" cy="4.2" r="1.8" />
      <rect height="9.2" rx="4.2" width="13" x="3.5" y="7.3" />
      <path d="M8 10.2h4M6.5 16.5V18M13.5 16.5V18" />
    </SvgIcon>
  );
}

export function CalendarClockIcon({ size = 17, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <path d="M9.5 16.5H4.2A1.7 1.7 0 0 1 2.5 14.8V5.2A1.7 1.7 0 0 1 4.2 3.5H14.8A1.7 1.7 0 0 1 16.5 5.2V9M2.5 7.5h14M6.5 2v2.5M12.5 2v2.5M14.2 12.4v1.8l1.3.8" />
      <circle cx="14.2" cy="14.2" r="3.6" />
    </SvgIcon>
  );
}

export function DownloadIcon({ size = 16, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <path d="M10 3v9.8M6.5 9.5 10 13l3.5-3.5M3.5 13.5v2A1.5 1.5 0 0 0 5 17h10a1.5 1.5 0 0 0 1.5-1.5v-2" />
    </SvgIcon>
  );
}

export function HeadphonesIcon({ size = 17, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <path d="M3.5 11.5V10a6.5 6.5 0 0 1 13 0v1.5" />
      <rect height="5.5" rx="1.2" width="3" x="2.5" y="11" />
      <rect height="5.5" rx="1.2" width="3" x="14.5" y="11" />
    </SvgIcon>
  );
}

export function CreditCardIcon({ size = 18, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <rect height="11" rx="1.8" width="15.6" x="2.2" y="4.5" />
      <path d="M2.2 8.5h15.6M5.2 12.2h3" />
    </SvgIcon>
  );
}

export function LockIcon({ size = 17, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <rect height="8.3" rx="1.8" width="12" x="4" y="9.2" />
      <path d="M6.5 9.2V6.5a3.5 3.5 0 0 1 7 0v2.7" />
    </SvgIcon>
  );
}

export function QrCodeIcon({ size = 17, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <rect height="4.5" rx="0.8" width="4.5" x="3" y="3" />
      <rect height="4.5" rx="0.8" width="4.5" x="12.5" y="3" />
      <rect height="4.5" rx="0.8" width="4.5" x="3" y="12.5" />
      <path d="M12.5 17v-4.5H17M17 16v1M10 3v4.5M10 12.5V17" />
    </SvgIcon>
  );
}

export function StarIcon({
  size = 14,
  filled = false,
  className,
}: IconProps & { filled?: boolean }) {
  return (
    <SvgIcon className={className} filled={filled} size={size}>
      <polygon points="10,2.4 12.3,7.1 17.5,7.8 13.7,11.5 14.6,16.7 10,14.2 5.4,16.7 6.3,11.5 2.5,7.8 7.7,7.1" />
    </SvgIcon>
  );
}

export function SlidersHorizontalIcon({ size = 16, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <path d="M3 5h6m3.5 0H17M12.5 3v4M3 10h3.5M10 10h7M6.5 8v4M3 15h8.5M15 15h2M11.5 13v4" />
    </SvgIcon>
  );
}

export function ChevronRightIcon({ size = 16, className }: IconProps) {
  return (
    <SvgIcon className={className} size={size}>
      <polyline points="7.5,4.5 13,10 7.5,15.5" />
    </SvgIcon>
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
