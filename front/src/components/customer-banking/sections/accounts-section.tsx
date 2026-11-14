import type { AccountItem, AccountSummaryResponse } from "@/lib/api/types";
import { formatDateTime, formatMinorAmount } from "@/lib/customer-banking/format";
import { getAccountStatusLabel } from "@/lib/customer-banking/labels";
import {
  BankActionBar,
  BankNoticeStrip,
  BankSelect,
  BankTable,
  BankToolbar,
  CopyButton,
  EmptyState,
  PaginationBar,
  StatusBadge,
  WorkStateGrid,
  WorkTabs,
} from "../common";

const BALANCE_BAR_COLORS = ["#1d4ed8", "#0f766e", "#b45309", "#6d28d9", "#475569"] as const;

function getAccountStatusTone(status: string): "success" | "warn" | "danger" | "muted" {
  switch (status) {
    case "ACTIVE":
      return "success";
    case "SUSPENDED":
    case "FROZEN":
      return "danger";
    case "RESTRICTED":
      return "warn";
    default:
      return "muted";
  }
}

export function AccountsSection({
  accountCursor,
  accountLimit,
  accounts,
  isBusy,
  selectedAccount,
  onAccountLimitChange,
  onLoadAccountDetail,
  onLoadAccounts,
  onLoadNextAccounts,
}: {
  accountCursor: string;
  accountLimit: number;
  accounts: AccountItem[];
  isBusy: boolean;
  selectedAccount: AccountSummaryResponse | null;
  onAccountLimitChange: (value: number) => void;
  onLoadAccountDetail: (accountId: number) => void;
  onLoadAccounts: () => void;
  onLoadNextAccounts: () => void;
}) {
  const selectedAccountId = selectedAccount?.accountId;

  // 단일 패스 순회로 상태별 건수와 총 출금가능금액을 동시에 집계한다.
  let activeCount = 0;
  let suspendedCount = 0;
  let restrictedCount = 0;
  let lowBalanceCount = 0;
  let totalAvailableMinor = 0;
  for (let i = 0; i < accounts.length; i += 1) {
    const account = accounts[i];
    if (account.accountStatus === "ACTIVE") {
      activeCount += 1;
    } else if (
      account.accountStatus === "SUSPENDED" ||
      account.accountStatus === "FROZEN"
    ) {
      suspendedCount += 1;
    } else if (account.accountStatus === "RESTRICTED") {
      restrictedCount += 1;
    }
    if (account.availableBalanceMinor <= 0) {
      lowBalanceCount += 1;
    } else {
      totalAvailableMinor += account.availableBalanceMinor;
    }
  }

  return (
    <section className="task-section compact-work-section">
      <WorkTabs
        active={selectedAccount ? "계좌상세" : "전계좌조회"}
        items={[
          { id: "전계좌조회", label: "전계좌조회", onClick: onLoadAccounts },
          {
            id: "계좌상세",
            label: "계좌상세",
            onClick: () => {
              if (selectedAccount) {
                onLoadAccountDetail(selectedAccount.accountId);
              } else if (accounts.length > 0) {
                onLoadAccountDetail(accounts[0].accountId);
              } else {
                onLoadAccounts();
              }
            },
          },
        ]}
      />
      <div className="section-title">
        <div>
          <p>조회</p>
          <h1>계좌조회</h1>
        </div>
        <BankToolbar label="계좌조회 상단 업무 버튼">
          <BankSelect
            label="건수"
            onChange={(value) => onAccountLimitChange(Number(value))}
            options={[
              { label: "10", value: 10 },
              { label: "20", value: 20 },
              { label: "50", value: 50 },
            ]}
            value={accountLimit}
          />
          <button disabled={isBusy} onClick={onLoadAccounts} type="button">
            조회
          </button>
          <button disabled={!accountCursor || isBusy} onClick={onLoadNextAccounts} type="button">
            다음
          </button>
        </BankToolbar>
      </div>
      <BankNoticeStrip
        items={[
          { label: "조회구분", value: "전계좌조회" },
          { label: "표시건수", value: `${accountLimit}건` },
          { label: "상태", value: "정상/제한 계좌 포함" },
          { label: "통화", value: "계좌별 통화 표시" },
        ]}
      />
      <WorkStateGrid
        label="조회 업무 상태"
        items={[
          { label: "계좌목록", value: accounts.length > 0 ? `${accounts.length}건` : "조회 전" },
          {
            label: "상세조회",
            value: selectedAccount
              ? getAccountStatusLabel(selectedAccount.accountStatus)
              : "미선택",
          },
          { label: "다음조회", value: accountCursor ? "가능" : "없음" },
          { label: "처리상태", value: isBusy ? "조회 중" : "대기" },
        ]}
      />

      <div className="account-status-grid" aria-label="계좌 상태 요약">
        <div>
          <span>출금가능</span>
          <strong className="tabular-nums">{activeCount}건</strong>
          <small>정상 계좌</small>
        </div>
        <div>
          <span>지급정지</span>
          <strong className="tabular-nums">{suspendedCount}건</strong>
          <small>출금 제한</small>
        </div>
        <div>
          <span>거래제한</span>
          <strong className="tabular-nums">{restrictedCount}건</strong>
          <small>업무 확인 필요</small>
        </div>
        <div>
          <span>잔액부족</span>
          <strong className="tabular-nums">{lowBalanceCount}건</strong>
          <small>출금 가능액 확인</small>
        </div>
      </div>

      {accounts.length > 0 && totalAvailableMinor > 0 ? (
        <div className="account-balance-chart" aria-label="계좌별 출금가능잔액 비중">
          <div className="balance-chart-head">
            <span>총 출금가능잔액</span>
            <strong className="tabular-nums font-mono">
              {formatMinorAmount(totalAvailableMinor, accounts[0]?.currencyCode ?? "KRW")}
            </strong>
          </div>
          <svg
            aria-label="계좌별 잔액 비율 막대"
            className="balance-ratio-svg"
            height={12}
            preserveAspectRatio="none"
            role="img"
            viewBox="0 0 1000 12"
            width="100%"
          >
            {(() => {
              let offsetX = 0;
              return accounts.map((account, idx) => {
                if (account.availableBalanceMinor <= 0) {
                  return null;
                }
                const ratioWidth = Math.max(
                  8,
                  Math.round((account.availableBalanceMinor / totalAvailableMinor) * 1000),
                );
                const x = offsetX;
                offsetX = Math.min(1000, offsetX + ratioWidth);
                return (
                  <rect
                    fill={BALANCE_BAR_COLORS[idx % BALANCE_BAR_COLORS.length]}
                    height={12}
                    key={account.accountId}
                    rx={2}
                    width={Math.min(ratioWidth, 1000 - x)}
                    x={x}
                    y={0}
                  >
                    <title>
                      {`${account.displayName}: ${formatMinorAmount(
                        account.availableBalanceMinor,
                        account.currencyCode,
                      )}`}
                    </title>
                  </rect>
                );
              });
            })()}
          </svg>
        </div>
      ) : null}

      <div className="account-grid">
        <section className="table-panel embedded">
          <div className="panel-toolbar">
            <div>
              <strong>보유계좌</strong>
              <span>계좌별 잔액과 상태 · {accounts.length}건</span>
            </div>
          </div>
          <div className="table-scroll-hint">표는 좌우로 스크롤해서 볼 수 있습니다.</div>
          <BankTable caption="계좌 목록" className="dense-bank-table">
            <thead>
              <tr>
                <th>계좌번호</th>
                <th>계좌명</th>
                <th>상태</th>
                <th>출금가능액</th>
                <th>선택</th>
                <th>관리</th>
              </tr>
            </thead>
            <tbody>
              {accounts.length === 0 ? (
                <tr>
                  <td colSpan={6}>
                    <EmptyState
                      title="계좌 조회 전"
                      description="전계좌조회 버튼으로 보유계좌를 확인하세요."
                    />
                  </td>
                </tr>
              ) : (
                accounts.map((account) => (
                  <tr
                    className={account.accountId === selectedAccountId ? "selected-row" : undefined}
                    key={account.accountId}
                  >
                    <td className="tabular-nums">{account.accountNumber}</td>
                    <td>{account.displayName}</td>
                    <td>
                      <StatusBadge tone={getAccountStatusTone(account.accountStatus)}>
                        {getAccountStatusLabel(account.accountStatus)}
                      </StatusBadge>
                    </td>
                    <td className="amount-cell font-mono tabular-nums">
                      {formatMinorAmount(
                        account.availableBalanceMinor,
                        account.currencyCode,
                      )}
                    </td>
                    <td>
                      {account.accountId === selectedAccountId ? (
                        <StatusBadge tone="success">선택계좌 선택됨</StatusBadge>
                      ) : (
                        "-"
                      )}
                    </td>
                    <td>
                      <button
                        disabled={isBusy}
                        onClick={() => onLoadAccountDetail(account.accountId)}
                        type="button"
                      >
                        상세
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </BankTable>
          <PaginationBar
            disabled={isBusy}
            hasNext={Boolean(accountCursor)}
            label="계좌 목록 페이지 탐색"
            onNext={onLoadNextAccounts}
          />
        </section>

        <aside className="detail-panel account-detail-work-panel">
          <div className="form-heading">
            <strong>계좌 상태</strong>
            <span>
              {selectedAccount ? getAccountStatusLabel(selectedAccount.accountStatus) : "미선택"}
            </span>
          </div>
          {selectedAccount ? (
            <>
              <div className="balance-card">
                <span>{selectedAccount.displayName}</span>
                <strong className="font-mono tabular-nums">
                  {formatMinorAmount(
                    selectedAccount.availableBalanceMinor,
                    selectedAccount.currencyCode,
                  )}
                </strong>
                <small>출금가능금액</small>
              </div>
              <dl className="detail-list">
                <div>
                  <dt>계좌번호</dt>
                  <dd className="tabular-nums inline-copy-cell">
                    <span>{selectedAccount.accountNumber}</span>
                    <CopyButton label="계좌번호" value={selectedAccount.accountNumber} />
                  </dd>
                </div>
                <div>
                  <dt>보류금액</dt>
                  <dd className="tabular-nums">
                    {formatMinorAmount(
                      selectedAccount.pendingBalanceMinor,
                      selectedAccount.currencyCode,
                    )}
                  </dd>
                </div>
                <div>
                  <dt>잔액 갱신</dt>
                  <dd className="tabular-nums">{formatDateTime(selectedAccount.balanceUpdatedAt)}</dd>
                </div>
              </dl>
              <BankActionBar label="선택 계좌 상세 액션">
                <button
                  disabled={isBusy}
                  onClick={() => onLoadAccountDetail(selectedAccount.accountId)}
                  type="button"
                >
                  잔액 새로고침
                </button>
              </BankActionBar>
            </>
          ) : (
            <EmptyState
              title="계좌 미선택"
              description="보유계좌 목록에서 상세 버튼을 선택하세요."
            />
          )}
        </aside>
      </div>
    </section>
  );
}
