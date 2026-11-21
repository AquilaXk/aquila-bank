/*
 * First-Party Account Card Deck & Account Summary Banner
 */

import { useState } from "react";
import { formatMinorAmount } from "@/lib/customer-banking/format";
import { CreditCardIcon, LandmarkIcon, LockIcon, WalletIcon } from "./icons";

export type AccountCardModel = {
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

function resolveCardVariant(account: AccountCardModel): "royal" | "emerald" | "slate" {
  if (account.accountStatus !== "ACTIVE") {
    return "slate";
  }
  return account.currencyCode === "KRW" ? "royal" : "emerald";
}

// Single backward pass extracts the trailing 4 ASCII digits without regex allocations.
function extractLastFourDigits(accountNumber: string): string {
  const digits = ["", "", "", ""];
  let found = 0;
  for (let i = accountNumber.length - 1; i >= 0 && found < 4; i -= 1) {
    const code = accountNumber.charCodeAt(i);
    if (code >= 48 && code <= 57) {
      digits[3 - found] = accountNumber[i];
      found += 1;
    }
  }
  if (found > 0) {
    return `${digits[0]}${digits[1]}${digits[2]}${digits[3]}`;
  }
  return accountNumber.slice(-4);
}

function resolveAccountStatusLabel(status: string, isPrimary?: boolean): string {
  if (status === "ACTIVE") {
    return isPrimary ? "대표계좌" : "정상";
  }
  return status === "RESTRICTED" ? "거래제한" : "지급정지";
}

export function AccountCard({
  account,
  balanceMasked = false,
  compact = false,
  onSelect,
  selected = false,
}: {
  account: AccountCardModel;
  balanceMasked?: boolean;
  compact?: boolean;
  onSelect?: (accountId: number) => void;
  selected?: boolean;
}) {
  const variant = resolveCardVariant(account);
  const title = account.customAlias?.trim() || account.displayName;
  const last4 = extractLastFourDigits(account.accountNumber);
  const balanceText = balanceMasked
    ? MASKED_TEXT
    : formatMinorAmount(account.availableBalanceMinor, account.currencyCode);
  const statusLabel = resolveAccountStatusLabel(account.accountStatus, account.isPrimary);

  const cardClass = `ab-bank-card variant-${variant}${compact ? " compact" : ""}${selected ? " selected" : ""}`;

  const cardInner = (
    <>
      <svg
        aria-hidden="true"
        className="ab-card-mesh"
        focusable="false"
        preserveAspectRatio="none"
        style={{ pointerEvents: "none" }}
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

      <div className="ab-bank-card-main">
        <div className="ab-bank-card-head">
          <div className="ab-bank-card-title-row">
            <span className="ab-bank-card-icon" aria-hidden="true">
              {account.currencyCode === "KRW" ? (
                <LandmarkIcon size={15} />
              ) : (
                <WalletIcon size={15} />
              )}
            </span>
            <strong className="ab-bank-card-title">{title}</strong>
            <span className="ab-bank-card-pill">{statusLabel}</span>
          </div>
          <span className="ab-bank-card-currency font-mono">{account.currencyCode}</span>
        </div>

        <div className="ab-bank-card-balance-block">
          <span className="ab-bank-card-caption">출금가능 잔액</span>
          <strong className="ab-bank-card-balance tabular-nums font-mono">{balanceText}</strong>
        </div>

        <div className="ab-bank-card-foot">
          <span className="ab-bank-card-number tabular-nums font-mono">
            {account.accountNumber}
          </span>
          <span className="ab-bank-card-mask tabular-nums font-mono">끝번호 {last4}</span>
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

export function StackedAccountDeck({
  accounts,
  balanceMasked = false,
  onSelectAccount,
  preferencesMap,
  primaryAccountId,
}: {
  accounts: AccountCardModel[];
  balanceMasked?: boolean;
  onSelectAccount?: () => void;
  preferencesMap?: ReadonlyMap<number, { customAlias?: string; primaryAccount?: boolean }>;
  primaryAccountId?: number | null;
}) {
  const [frontIndex, setFrontIndex] = useState(0);

  if (accounts.length === 0) {
    return (
      <div className="ab-empty-deck">
        <CreditCardIcon size={16} />
        <div>
          <strong>계좌 연동 대기</strong>
          <span>보유계좌 조회 후 표시됩니다.</span>
        </div>
      </div>
    );
  }

  const deckCount = Math.min(3, accounts.length);
  const safeIdx = frontIndex % deckCount;
  const front = accounts[safeIdx];
  const frontPref = preferencesMap?.get(front.accountId);

  return (
    <div className="ab-bank-deck-wrap">
      <AccountCard
        account={{
          ...front,
          customAlias: frontPref?.customAlias,
          isPrimary: Boolean(frontPref?.primaryAccount) || front.accountId === primaryAccountId,
        }}
        balanceMasked={balanceMasked}
        compact
        onSelect={
          deckCount > 1
            ? () => setFrontIndex((idx) => (idx + 1) % deckCount)
            : onSelectAccount
        }
      />
      {deckCount > 1 ? (
        <div className="ab-deck-controls">
          <span className="tabular-nums">
            {safeIdx + 1} / {deckCount} 계좌
          </span>
          <button
            onClick={() => setFrontIndex((idx) => (idx + 1) % deckCount)}
            type="button"
          >
            다음 계좌
          </button>
        </div>
      ) : null}
    </div>
  );
}

export function GuestLockedAccountCard() {
  return (
    <article className="ab-bank-card variant-slate compact">
      <div className="ab-bank-card-main">
        <div className="ab-bank-card-head">
          <div className="ab-bank-card-title-row">
            <span className="ab-bank-card-icon" aria-hidden="true">
              <LockIcon size={15} />
            </span>
            <strong className="ab-bank-card-title">보안 계좌 카드</strong>
            <span className="ab-bank-card-pill">보호모드</span>
          </div>
          <span className="ab-bank-card-currency font-mono">KRW</span>
        </div>
        <div className="ab-bank-card-balance-block">
          <span className="ab-bank-card-caption">민감정보 보호</span>
          <strong className="ab-bank-card-balance">로그인 후 확인</strong>
        </div>
        <div className="ab-bank-card-foot">
          <span className="ab-bank-card-number font-mono">•••-•••-••••••</span>
        </div>
      </div>
    </article>
  );
}

export function AccountSummaryBanner({
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
    <div className="ab-account-banner">
      <div className="ab-account-banner-left">
        <span className="ab-account-banner-icon" aria-hidden="true">
          {currencyCode === "KRW" ? <LandmarkIcon size={17} /> : <WalletIcon size={17} />}
        </span>
        <div className="ab-account-banner-text">
          <div className="ab-account-banner-title">
            <strong>{label}</strong>
            {statusLabel ? <span className="ab-account-banner-badge">{statusLabel}</span> : null}
          </div>
          <small className="tabular-nums font-mono">{accountNumber}</small>
        </div>
      </div>
      {typeof balanceMinor === "number" ? (
        <div className="ab-account-banner-balance">
          <span>출금가능 잔액</span>
          <strong className="tabular-nums font-mono">
            {balanceMasked ? MASKED_TEXT : formatMinorAmount(balanceMinor, currencyCode)}
          </strong>
        </div>
      ) : null}
    </div>
  );
}
