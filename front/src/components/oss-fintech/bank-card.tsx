/*
 * Horizon BankCard, Stacked Card Deck & Account Summary Banner
 *
 * 참조 구현:
 * - adrianhajdin/banking (MIT License, Copyright (c) 2024 Adrian Hajdin - JS Mastery)
 *   components/BankCard.tsx, components/RightSidebar.tsx, components/BankInfo.tsx
 * - abderrahimghazali/shadcn-fintech (MIT License, Copyright (c) 2026 Abderrahim Ghazali)
 *   src/components/dashboard/account-cards.tsx
 */

import { useState } from "react";
import { formatMinorAmount } from "@/lib/customer-banking/format";
import {
  CreditCardIcon,
  EmvChipIcon,
  LandmarkIcon,
  LockIcon,
  NfcWaveIcon,
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
        : account.currencyCode
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
        viewBox="0 0 320 190"
      >
        <circle cx="272" cy="34" fill="rgba(255,255,255,0.12)" r="78" />
        <circle cx="290" cy="158" fill="rgba(255,255,255,0.08)" r="62" />
        <path
          d="M0 142 C85 96, 195 178, 320 112"
          fill="none"
          stroke="rgba(255,255,255,0.16)"
          strokeWidth="1.5"
        />
        <path
          d="M0 162 C95 118, 205 194, 320 134"
          fill="none"
          stroke="rgba(255,255,255,0.1)"
          strokeWidth="1"
        />
      </svg>

      <div className="oss-bank-card-main">
        <div className="oss-bank-card-head">
          <div className="oss-bank-card-title-row">
            <span className="oss-bank-card-pill">{statusLabel}</span>
            <strong className="oss-bank-card-title">{title}</strong>
          </div>
          <span className="oss-bank-card-currency">{account.currencyCode}</span>
        </div>

        <div className="oss-bank-card-balance-block">
          <span className="oss-bank-card-caption">출금가능 잔액</span>
          <strong className="oss-bank-card-balance tabular-nums font-mono">{balanceText}</strong>
        </div>

        <div className="oss-bank-card-foot">
          <span className="oss-bank-card-number tabular-nums font-mono">
            {account.accountNumber}
          </span>
          <span className="oss-bank-card-mask tabular-nums font-mono">•••• {last4}</span>
        </div>
      </div>

      <div className="oss-bank-card-side">
        <div className="oss-bank-card-icons">
          <EmvChipIcon size={compact ? 28 : 34} />
          <NfcWaveIcon size={compact ? 16 : 18} />
        </div>
        <div className="oss-bank-card-network" aria-hidden="true">
          <span className="circle-left" />
          <span className="circle-right" />
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
        <CreditCardIcon size={18} />
        <div>
          <strong>계좌 연동 대기</strong>
          <span>보유계좌 조회 후 계좌 카드가 표시됩니다.</span>
        </div>
      </div>
    );
  }

  const ordered = accounts.slice(0, 3);
  const safeIdx = frontIndex % ordered.length;
  const front = ordered[safeIdx];
  const back = ordered.length > 1 ? ordered[(safeIdx + 1) % ordered.length] : null;
  const frontPref = preferencesMap?.get(front.accountId);
  const backPref = back ? preferencesMap?.get(back.accountId) : undefined;

  return (
    <div className="oss-bank-deck-wrap">
      <div className="oss-bank-deck">
        {back ? (
          <div className="oss-bank-deck-back" aria-hidden="true">
            <HorizonBankCard
              account={{
                ...back,
                customAlias: backPref?.customAlias,
                isPrimary:
                  Boolean(backPref?.primaryAccount) || back.accountId === primaryAccountId,
              }}
              balanceMasked={balanceMasked}
              compact
            />
          </div>
        ) : null}
        <div className="oss-bank-deck-front">
          <HorizonBankCard
            account={{
              ...front,
              customAlias: frontPref?.customAlias,
              isPrimary:
                Boolean(frontPref?.primaryAccount) || front.accountId === primaryAccountId,
            }}
            balanceMasked={balanceMasked}
            compact
            onSelect={
              ordered.length > 1
                ? () => setFrontIndex((idx) => (idx + 1) % ordered.length)
                : onSelectAccount
            }
          />
        </div>
      </div>
      {ordered.length > 1 ? (
        <div className="oss-deck-controls">
          <span>
            {safeIdx + 1} / {ordered.length} 계좌
          </span>
          <button
            onClick={() => setFrontIndex((idx) => (idx + 1) % ordered.length)}
            type="button"
          >
            다음 카드 보기
          </button>
        </div>
      ) : null}
    </div>
  );
}

export function GuestLockedBankCard() {
  return (
    <article className="oss-bank-card variant-slate compact">
      <svg
        aria-hidden="true"
        className="oss-card-mesh"
        focusable="false"
        preserveAspectRatio="none"
        viewBox="0 0 320 190"
      >
        <circle cx="272" cy="34" fill="rgba(255,255,255,0.09)" r="72" />
        <path
          d="M0 142 C85 96, 195 178, 320 112"
          fill="none"
          stroke="rgba(255,255,255,0.12)"
          strokeWidth="1.4"
        />
      </svg>
      <div className="oss-bank-card-main">
        <div className="oss-bank-card-head">
          <div className="oss-bank-card-title-row">
            <span className="oss-bank-card-pill">보호모드</span>
            <strong className="oss-bank-card-title">보안 계좌 카드</strong>
          </div>
          <LockIcon size={15} />
        </div>
        <div className="oss-bank-card-balance-block">
          <span className="oss-bank-card-caption">민감정보 보호</span>
          <strong className="oss-bank-card-balance">로그인 후 확인</strong>
        </div>
        <div className="oss-bank-card-foot">
          <span className="oss-bank-card-number font-mono">•••-•••-••••••</span>
        </div>
      </div>
      <div className="oss-bank-card-side">
        <EmvChipIcon size={28} />
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
        <span className="oss-account-banner-icon">
          {currencyCode === "KRW" ? <LandmarkIcon size={18} /> : <WalletIcon size={18} />}
        </span>
        <div>
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
