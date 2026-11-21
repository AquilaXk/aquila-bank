import type {
  AccountPreferenceItem,
  ScheduledTransferItem,
  TransactionAnnotationItem,
  TransactionItem,
} from "@/lib/api/types";
import {
  bankingNewsItems,
  favoriteServiceItems,
  serviceHourItems,
} from "@/lib/customer-banking/constants";
import { formatDateTime, formatMinorAmount } from "@/lib/customer-banking/format";
import { getDirectionLabel } from "@/lib/customer-banking/labels";
import type { MenuSection } from "@/lib/customer-banking/types";
import { GuestLockedBankCard, HorizonBankCard } from "../../oss-fintech/bank-card";
import { HorizonCategoryBadge } from "../../oss-fintech/category-badge";
import {
  CategorySpendingDonut,
  MoneyMovementChart,
  PortfolioDonutBox,
} from "../../oss-fintech/fintech-charts";
import { ArrowDownLeftIcon, ArrowUpRightIcon, LockIcon, MenuSectionIcon } from "../../oss-fintech/icons";
import { BankNoticeStrip, WorkTabs } from "../common";

const MASKED_AMOUNT_TEXT = "••••••";

type DashboardAccountItem = {
  accountId: number;
  accountNumber: string;
  displayName: string;
  accountStatus: string;
  currencyCode: string;
  availableBalanceMinor: number;
};

const DASHBOARD_WORK_TABS: Array<{ label: string; section: MenuSection }> = [
  { label: "개인뱅킹", section: "dashboard" },
  { label: "조회", section: "accounts" },
  { label: "이체", section: "transfer" },
  { label: "인증센터", section: "security" },
  { label: "고객센터", section: "supportCenter" },
];

function resolvePrimaryAccountDisplay(
  primaryAccount: DashboardAccountItem | null,
  hasSession: boolean,
  customAlias?: string,
  balanceMasked = false,
) {
  if (primaryAccount) {
    return {
      displayName: customAlias?.trim() || primaryAccount.displayName,
      accountNumber: primaryAccount.accountNumber,
      balanceDisplay: balanceMasked
        ? MASKED_AMOUNT_TEXT
        : formatMinorAmount(
            primaryAccount.availableBalanceMinor,
            primaryAccount.currencyCode,
          ),
    };
  }
  if (hasSession) {
    return {
      displayName: "보유계좌 조회 대기",
      accountNumber: "계좌조회 메뉴에서 보유계좌를 확인하세요",
      balanceDisplay: "조회 전",
    };
  }
  return {
    displayName: "로그인 후 계좌 확인",
    accountNumber: "로그인 후 대표계좌 번호가 표시됩니다",
    balanceDisplay: "로그인 후 확인",
  };
}

function resolveDashboardWorkStatus(hasSession: boolean) {
  if (hasSession) {
    return {
      notice: "조회/이체/인증 정상",
      lookup: "조회 결과 대기",
      transfer: "이체 처리 대기",
      notification: "알림 수신 대기",
      transactionState: "다음 조회 가능",
      transferState: "중복 방지",
      notificationState: "실시간 알림",
    };
  }
  return {
    notice: "로그인 후 업무 가능",
    lookup: "로그인 후 조회",
    transfer: "인증 후 이체",
    notification: "로그인 후 알림 확인",
    transactionState: "인증 후 가능",
    transferState: "인증 후 가능",
    notificationState: "로그인 후 알림 확인",
  };
}

export function DashboardSection({
  accountPreferencesMap,
  accounts,
  annotationsMap,
  balanceMasked = false,
  hasSession,
  isBusy,
  onMove,
  onRefresh,
  primaryAccountId,
  scheduledTransfers = [],
  transactions = [],
}: {
  accountPreferencesMap?: ReadonlyMap<number, AccountPreferenceItem>;
  accounts?: DashboardAccountItem[];
  annotationsMap?: ReadonlyMap<string, TransactionAnnotationItem>;
  balanceMasked?: boolean;
  hasSession: boolean;
  isBusy: boolean;
  onMove: (section: MenuSection) => void;
  onRefresh: () => void;
  primaryAccountId?: number | null;
  scheduledTransfers?: ScheduledTransferItem[];
  transactions?: TransactionItem[];
}) {
  const memberAccounts = hasSession && accounts ? accounts : [];
  const primaryAccount =
    memberAccounts.find((item) => item.accountId === primaryAccountId) ??
    memberAccounts[0] ??
    null;
  const customAlias = primaryAccount
    ? accountPreferencesMap?.get(primaryAccount.accountId)?.customAlias
    : undefined;
  const {
    displayName: primaryDisplayName,
    accountNumber: primaryAccountNumber,
    balanceDisplay: primaryBalanceDisplay,
  } = resolvePrimaryAccountDisplay(primaryAccount, hasSession, customAlias, balanceMasked);
  const workStatus = resolveDashboardWorkStatus(hasSession);

  // 통화별 보유 잔액, 분류별 지출, 입출금 합계를 단일 패스로 집계한다.
  const currencyTotals = new Map<string, number>();
  for (let i = 0; i < memberAccounts.length; i += 1) {
    const acc = memberAccounts[i];
    currencyTotals.set(
      acc.currencyCode,
      (currencyTotals.get(acc.currencyCode) ?? 0) + Math.max(0, acc.availableBalanceMinor),
    );
  }
  const currencyEntries = Array.from(currencyTotals.entries());

  let totalInflowMinor = 0;
  let totalOutflowMinor = 0;
  const flowBars: Array<{ ref: string; amount: number; direction: string }> = [];
  const categoryStats = new Map<string, { amountMinor: number; count: number }>();
  for (let i = 0; i < transactions.length; i += 1) {
    const tx = transactions[i];
    if (tx.direction === "CREDIT") {
      totalInflowMinor += tx.amountMinor;
    } else {
      totalOutflowMinor += tx.amountMinor;
      const cat = annotationsMap?.get(tx.transactionReference)?.category ?? "OTHER";
      const entry = categoryStats.get(cat) ?? { amountMinor: 0, count: 0 };
      entry.amountMinor += tx.amountMinor;
      entry.count += 1;
      categoryStats.set(cat, entry);
    }
    if (flowBars.length < 14) {
      flowBars.push({
        ref: tx.transactionReference,
        amount: tx.amountMinor,
        direction: tx.direction,
      });
    }
  }
  const recentFiveTransactions = transactions.slice(0, 5);
  const activeSchedulesCount = scheduledTransfers.filter(
    (rule) => rule.ruleStatus === "ACTIVE",
  ).length;
  const categorySummaries = Array.from(categoryStats.entries())
    .map(([category, stat]) => ({ category, ...stat }))
    .sort((a, b) => b.amountMinor - a.amountMinor);
  const dashboardCurrencyCode = primaryAccount?.currencyCode ?? "KRW";

  return (
    <section className="task-section compact-work-section">
      <WorkTabs
        active="개인뱅킹"
        items={DASHBOARD_WORK_TABS.map(({ label, section }) => ({
          id: label,
          label,
          onClick: () => onMove(section),
        }))}
      />
      <div className="section-title">
        <div>
          <p>개인뱅킹</p>
          <h1>뱅킹 업무</h1>
        </div>
        <div className="button-row">
          <button onClick={() => onMove("accounts")} type="button">
            {hasSession ? "계좌조회" : "로그인 후 계좌조회"}
          </button>
          <button onClick={() => onMove("transfer")} type="button">
            {hasSession ? "이체" : "로그인 후 이체"}
          </button>
          <button disabled={!hasSession || isBusy} onClick={onRefresh} type="button">
            토큰 재발급
          </button>
        </div>
      </div>
      <BankNoticeStrip
        items={[
          { label: "이용시간", value: "00:30~23:30" },
          { label: "보안등급", value: hasSession ? "개인 인증 완료 후 이체 가능" : "로그인 후 확인" },
          { label: "상담", value: "평일 09:00~18:00" },
          { label: "업무현황", value: workStatus.notice },
          { label: "조회", value: workStatus.lookup },
          { label: "이체", value: workStatus.transfer },
          { label: "알림", value: workStatus.notification },
          { label: "인증", value: "인증서 관리" },
        ]}
      />
      <div className="oss-hero-grid" aria-label="개인 자산 요약">
        {hasSession ? (
          <PortfolioDonutBox
            accounts={memberAccounts.map((acc) => ({
              ...acc,
              displayName:
                accountPreferencesMap?.get(acc.accountId)?.customAlias || acc.displayName,
            }))}
            activeSchedulesCount={activeSchedulesCount}
            balanceMasked={balanceMasked}
            currencyEntries={currencyEntries}
          />
        ) : (
          <section className="oss-donut-box">
            <div className="oss-donut-meta">
              <div className="form-heading">
                <strong className="oss-inline-icon">
                  <LockIcon size={16} />
                  <span>자산 현황 요약</span>
                </strong>
                <span>보호모드</span>
              </div>
              <p className="field-note">
                인증센터 로그인 후 통화별 보유 자산 비중과 입출금 분석 차트가 표시됩니다.
              </p>
              <div className="button-row compact">
                <button onClick={() => onMove("security")} type="button">
                  인증센터 이동
                </button>
              </div>
            </div>
          </section>
        )}

        <div className="dashboard-primary-account-card" aria-label="대표 계좌 요약">
          <div className="oss-primary-card-top">
            <div className="dashboard-primary-account-meta">
              <div className="dashboard-primary-account-badge">
                <span className="account-badge-label">대표 계좌</span>
                <span className={hasSession ? "account-status-dot active" : "account-status-dot"} />
                <span className="account-status-text">{hasSession ? "정상 이용중" : "로그인 필요"}</span>
              </div>
              <strong className="dashboard-primary-account-name">{primaryDisplayName}</strong>
              <span className="dashboard-primary-account-number tabular-nums">
                {primaryAccountNumber}
              </span>
            </div>
            <div className="dashboard-primary-account-balance">
              <span className="balance-label">출금가능 잔액</span>
              <strong className="balance-value tabular-nums">{primaryBalanceDisplay}</strong>
              <div className="button-row compact">
                <button onClick={() => onMove("transfer")} type="button">
                  즉시이체
                </button>
                <button onClick={() => onMove("accounts")} type="button">
                  계좌관리
                </button>
              </div>
            </div>
          </div>
          {hasSession && primaryAccount ? (
            <HorizonBankCard
              account={{
                ...primaryAccount,
                customAlias,
                isPrimary: true,
              }}
              balanceMasked={balanceMasked}
              compact
              onSelect={() => onMove("accounts")}
            />
          ) : (
            <GuestLockedBankCard />
          )}
        </div>
      </div>

      {hasSession ? (
        <div className="two-column" aria-label="개인 자산 및 입출금 요약">
          <MoneyMovementChart
            balanceMasked={balanceMasked}
            currencyCode={dashboardCurrencyCode}
            flowBars={flowBars}
            inflowMinor={totalInflowMinor}
            onViewAll={() => onMove("transactions")}
            outflowMinor={totalOutflowMinor}
            transactionCount={transactions.length}
          />
          <section className="dashboard-cashflow-card">
            <div className="form-heading">
              <strong>최근 거래 요약</strong>
              <button onClick={() => onMove("transactions")} type="button">
                전체보기
              </button>
            </div>
            <CategorySpendingDonut
              balanceMasked={balanceMasked}
              categories={categorySummaries}
              currencyCode={dashboardCurrencyCode}
            />
            {recentFiveTransactions.length === 0 ? (
              <p className="field-note">최근 조회된 거래가 없습니다.</p>
            ) : (
              <ul className="bank-news-list">
                {recentFiveTransactions.map((tx) => {
                  const txCategory = annotationsMap?.get(tx.transactionReference)?.category;
                  return (
                    <li key={tx.transactionReference}>
                      <button onClick={() => onMove("transactions")} type="button">
                        <span className="oss-inline-icon">
                          {tx.direction === "CREDIT" ? (
                            <ArrowDownLeftIcon size={14} />
                          ) : (
                            <ArrowUpRightIcon size={14} />
                          )}
                          <span>
                            {formatDateTime(tx.bookedAt)} ·{" "}
                            {tx.counterpartyMaskedName ||
                              tx.summary ||
                              getDirectionLabel(tx.direction)}
                          </span>
                        </span>
                        <span className="oss-inline-icon">
                          {txCategory ? <HorizonCategoryBadge category={txCategory} /> : null}
                          <strong className="tabular-nums font-mono">
                            {balanceMasked
                              ? MASKED_AMOUNT_TEXT
                              : formatMinorAmount(tx.amountMinor, tx.currencyCode)}
                          </strong>
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </section>
        </div>
      ) : null}
      <div className="bank-home-grid">
        <section className="table-panel bank-home-panel">
          <div className="panel-toolbar">
            <strong>자주찾는서비스</strong>
            <span>개인뱅킹</span>
          </div>
          <div className="bank-service-list">
            {favoriteServiceItems.map((item) => (
              <button
                key={item.label}
                onClick={() => onMove(item.section)}
                type="button"
              >
                <span className="oss-inline-icon">
                  <MenuSectionIcon section={item.section} size={14} />
                  <span>{item.group}</span>
                </span>
                <strong>{item.label}</strong>
              </button>
            ))}
          </div>
        </section>
        <section className="table-panel bank-home-panel">
          <div className="panel-toolbar">
            <strong>새소식</strong>
            <span>공지</span>
          </div>
          <ul className="bank-news-list">
            {bankingNewsItems.map((item) => (
              <li key={item}>
                <button onClick={() => onMove("notifications")} type="button">
                  {item}
                </button>
              </li>
            ))}
          </ul>
        </section>
        <section className="table-panel bank-home-panel hours-panel">
          <div className="panel-toolbar">
            <strong>서비스 이용시간</strong>
            <span>상태</span>
          </div>
          <table className="service-hours-table">
            <caption>서비스 이용시간</caption>
            <thead>
              <tr>
                <th>업무</th>
                <th>시간</th>
                <th>상태</th>
              </tr>
            </thead>
            <tbody>
              {serviceHourItems.map((item) => (
                <tr key={item.task}>
                  <td>{item.task}</td>
                  <td>{item.time}</td>
                  <td>{item.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      </div>
      <details className="spec-disclosure">
        <summary>업무별 처리 기준 안내</summary>
        <div className="bank-table-wrap">
          <table className="bank-table">
            <caption>업무별 처리 기준</caption>
            <thead>
              <tr>
                <th>업무</th>
                <th>기준</th>
                <th>상태</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>거래내역 조회</td>
                <td>계좌와 기간 조건 조회</td>
                <td>{workStatus.transactionState}</td>
              </tr>
              <tr>
                <td>이체</td>
                <td>요청 단위 중복 방지</td>
                <td>{workStatus.transferState}</td>
              </tr>
              <tr>
                <td>알림</td>
                <td>실시간 알림과 알림함</td>
                <td>{workStatus.notificationState}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>
    </section>
  );
}
