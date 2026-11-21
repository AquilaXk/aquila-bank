/*
 * Executive Account Cards & Summary Banner (shadcn/ui & Fintech Architecture)
 *
 * 참조 구현:
 * - shadcn-ui/ui (MIT License, Copyright (c) 2023 shadcn) — cards/payment-method
 * - adrianhajdin/banking (MIT License, Copyright (c) 2024 Adrian Hajdin - JS Mastery)
 * - abderrahimghazali/shadcn-fintech (MIT License, Copyright (c) 2026 Abderrahim Ghazali)
 */

import { useState } from "react";
import { formatMinorAmount } from "@/lib/customer-banking/format";
import {
  CreditCardIcon,
  LandmarkIcon,
  LockIcon,
  WalletIcon,
} from "./icons";

export type BankCardModel = {
  accountId: number;
  accountNumber: string;
  displayName: string;
  accountStatus: string;
  currencyCode: string;
  availableBalanceMinor: number;
  customAlias?: string;
  isPrimary?: boolean;
};

const MASKED_TEXT = "••••••";

function resolveCardVariant(account: BankCardModel): "royal" | "emerald" | "slate" {
  if (account.accountStatus !== "ACTIVE") {
    return "slate";
  }
  if (account.currencyCode !== "KRW") {
    return "emerald";
  }
  return "royal";
}

function getLastFourDigits(accountNumber: string): string {
  const digits = accountNumber.replace(/[^0-9]/g, "");
  return digits.slice(-4) || accountNumber.slice(-4);
}

export function HorizonBankCard({
  account,
  balanceMasked = false,
  compact = false,
  onSelect,
  selected = false,
}: {
  account: BankCardModel;
  balanceMasked?: boolean;
  compact?: boolean;
  onSelect?: (accountId: number) => void;
  selected?: boolean;
}) {
  const variant = resolveCardVariant(account);
  const title = account.customAlias?.trim() || account.displayName;
  const last4 = getLastFourDigits(account.accountNumber);
  const balanceText = balanceMasked
    ? MASKED_TEXT
    : formatMinorAmount(account.availableBalanceMinor, account.currencyCode);
  const statusLabel =
    account.accountStatus === "ACTIVE"
      ? account.isPrimary
        ? "대표계좌"
        : "정상"
      : account.accountStatus === "RESTRICTED"
        ? "거래제한"
        : "지급정지";

  const cardClass = [
    "oss-bank-card",
    `variant-${variant}`,
    compact ? "compact" : "",
    selected ? "selected" : "",
  ]
    .filter(Boolean)
    .join(" ");

  const cardInner = (
    <>
      <svg
        aria-hidden="true"
        className="oss-card-mesh"
        focusable="false"
        preserveAspectRatio="none"
        viewBox="0 0 320 180"
      >
        <path
          d="M0 140 Q 90 105 180 135 T 320 110"
          fill="none"
          stroke="currentColor"
          strokeOpacity="0.1"
          strokeWidth="1.25"
        />
        <circle
          cx="286"
          cy="32"
          fill="none"
          r="54"
          stroke="currentColor"
          strokeOpacity="0.08"
          strokeWidth="1"
        />
      </svg>

      <div className="oss-bank-card-main">
        <div className="oss-bank-card-head">
          <div className="oss-bank-card-title-row">
            <span className="oss-bank-card-icon" aria-hidden="true">
              {account.currencyCode === "KRW" ? (
                <LandmarkIcon size={15} />
              ) : (
                <WalletIcon size={15} />
              )}
            </span>
            <strong className="oss-bank-card-title">{title}</strong>
            <span className="oss-bank-card-pill">{statusLabel}</span>
          </div>
          <span className="oss-bank-card-currency font-mono">{account.currencyCode}</span>
        </div>

        <div className="oss-bank-card-balance-block">
          <span className="oss-bank-card-caption">출금가능 잔액</span>
          <strong className="oss-bank-card-balance tabular-nums font-mono">{balanceText}</strong>
        </div>

        <div className="oss-bank-card-foot">
          <span className="oss-bank-card-number tabular-nums font-mono">
            {account.accountNumber}
          </span>
          <span className="oss-bank-card-mask tabular-nums font-mono">끝번호 {last4}</span>
        </div>
      </div>
    </>
  );

  if (onSelect) {
    return (
      <button
        aria-pressed={selected}
        className={cardClass}
        onClick={() => onSelect(account.accountId)}
        type="button"
      >
        {cardInner}
      </button>
    );
  }

  return <article className={cardClass}>{cardInner}</article>;
}

export function StackedBankDeck({
  accounts,
  balanceMasked = false,
  onSelectAccount,
  preferencesMap,
  primaryAccountId,
}: {
  accounts: BankCardModel[];
  balanceMasked?: boolean;
  onSelectAccount?: () => void;
  preferencesMap?: ReadonlyMap<number, { customAlias?: string; primaryAccount?: boolean }>;
  primaryAccountId?: number | null;
}) {
  const [frontIndex, setFrontIndex] = useState(0);

  if (accounts.length === 0) {
    return (
      <div className="oss-empty-deck">
        <CreditCardIcon size={16} />
        <div>
          <strong>계좌 연동 대기</strong>
          <span>보유계좌 조회 후 표시됩니다.</span>
        </div>
      </div>
    );
  }

  const ordered = accounts.slice(0, 3);
  const safeIdx = frontIndex % ordered.length;
  const front = ordered[safeIdx];
  const frontPref = preferencesMap?.get(front.accountId);

  return (
    <div className="oss-bank-deck-wrap">
      <HorizonBankCard
        account={{
          ...front,
          customAlias: frontPref?.customAlias,
          isPrimary: Boolean(frontPref?.primaryAccount) || front.accountId === primaryAccountId,
        }}
        balanceMasked={balanceMasked}
        compact
        onSelect={
          ordered.length > 1
            ? () => setFrontIndex((idx) => (idx + 1) % ordered.length)
            : onSelectAccount
        }
      />
      {ordered.length > 1 ? (
        <div className="oss-deck-controls">
          <span className="tabular-nums">
            {safeIdx + 1} / {ordered.length} 계좌
          </span>
          <button
            onClick={() => setFrontIndex((idx) => (idx + 1) % ordered.length)}
            type="button"
          >
            다음 계좌
          </button>
        </div>
      ) : null}
    </div>
  );
}

export function GuestLockedBankCard() {
  return (
    <article className="oss-bank-card variant-slate compact">
      <div className="oss-bank-card-main">
        <div className="oss-bank-card-head">
          <div className="oss-bank-card-title-row">
            <span className="oss-bank-card-icon" aria-hidden="true">
              <LockIcon size={15} />
            </span>
            <strong className="oss-bank-card-title">보안 계좌 카드</strong>
            <span className="oss-bank-card-pill">보호모드</span>
          </div>
          <span className="oss-bank-card-currency font-mono">KRW</span>
        </div>
        <div className="oss-bank-card-balance-block">
          <span className="oss-bank-card-caption">민감정보 보호</span>
          <strong className="oss-bank-card-balance">로그인 후 확인</strong>
        </div>
        <div className="oss-bank-card-foot">
          <span className="oss-bank-card-number font-mono">•••-•••-••••••</span>
        </div>
      </div>
    </article>
  );
}

export function HorizonAccountBanner({
  accountNumber,
  balanceMinor,
  balanceMasked = false,
  currencyCode = "KRW",
  label,
  statusLabel,
}: {
  accountNumber: string;
  balanceMinor?: number;
  balanceMasked?: boolean;
  currencyCode?: string;
  label: string;
  statusLabel?: string;
}) {
  return (
    <div className="oss-account-banner">
      <div className="oss-account-banner-left">
        <span className="oss-account-banner-icon" aria-hidden="true">
          {currencyCode === "KRW" ? <LandmarkIcon size={17} /> : <WalletIcon size={17} />}
        </span>
        <div className="oss-account-banner-text">
          <div className="oss-account-banner-title">
            <strong>{label}</strong>
            {statusLabel ? <span className="oss-account-banner-badge">{statusLabel}</span> : null}
          </div>
          <small className="tabular-nums font-mono">{accountNumber}</small>
        </div>
      </div>
      {typeof balanceMinor === "number" ? (
        <div className="oss-account-banner-balance">
          <span>출금가능 잔액</span>
          <strong className="tabular-nums font-mono">
            {balanceMasked ? MASKED_TEXT : formatMinorAmount(balanceMinor, currencyCode)}
          </strong>
        </div>
      ) : null}
    </div>
  );
}
