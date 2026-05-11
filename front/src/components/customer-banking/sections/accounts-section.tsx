import type { AccountItem, AccountSummaryResponse } from '@/lib/api/types';
import { formatDateTime, formatMinorAmount } from '@/lib/customer-banking/format';
import { BankNoticeStrip, WorkTabs } from '../common';

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
  return (
    <section className="task-section">
      <WorkTabs
        active="전계좌조회"
        items={[
          { id: "전계좌조회", label: "전계좌조회", onClick: onLoadAccounts },
          {
            id: "계좌상세",
            label: "계좌상세",
            onClick: () => {
              if (selectedAccount) {
                onLoadAccountDetail(selectedAccount.accountId);
              }
            },
            disabled: !selectedAccount,
          },
          {
            id: "거래내역",
            label: "거래내역",
            onClick: () => undefined,
            disabled: true,
          },
          {
            id: "이체",
            label: "이체",
            onClick: () => undefined,
            disabled: true,
          },
        ]}
      />
      <div className="section-title">
        <div>
          <p>조회</p>
          <h1>계좌조회</h1>
        </div>
        <div className="button-row">
          <label className="inline-control">
            <span>건수</span>
            <select
              onChange={(event) => onAccountLimitChange(Number(event.target.value))}
              value={accountLimit}
            >
              <option value={10}>10</option>
              <option value={20}>20</option>
              <option value={50}>50</option>
            </select>
          </label>
          <button disabled={isBusy} onClick={onLoadAccounts} type="button">
            조회
          </button>
          <button disabled={!accountCursor || isBusy} onClick={onLoadNextAccounts} type="button">
            다음
          </button>
        </div>
      </div>
      <BankNoticeStrip
        items={[
          { label: "조회구분", value: "전계좌조회" },
          { label: "표시건수", value: `${accountLimit}건` },
          { label: "상태", value: "정상/제한 계좌 포함" },
          { label: "통화", value: "계좌별 통화 표시" },
        ]}
      />

      <div className="account-grid">
        <section className="table-panel embedded">
          <div className="panel-toolbar">
            <div>
              <strong>보유계좌</strong>
              <span>계좌별 잔액과 상태 · {accounts.length}건</span>
            </div>
          </div>
          <div className="bank-table-wrap">
            <table className="bank-table">
              <caption>계좌 목록</caption>
              <thead>
                <tr>
                  <th>계좌번호</th>
                  <th>계좌명</th>
                  <th>상태</th>
                  <th>출금가능액</th>
                  <th>관리</th>
                </tr>
              </thead>
              <tbody>
                {accounts.length === 0 ? (
                  <tr>
                    <td colSpan={5}>조회 내역 없음</td>
                  </tr>
                ) : (
                  accounts.map((account) => (
                    <tr key={account.accountId}>
                      <td>{account.accountNumber}</td>
                      <td>{account.displayName}</td>
                      <td>
                        <span className="status-badge">{account.accountStatus}</span>
                      </td>
                      <td className="amount-cell">
                        {formatMinorAmount(
                          account.availableBalanceMinor,
                          account.currencyCode,
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
            </table>
          </div>
        </section>

        <aside className="detail-panel">
          <div className="form-heading">
            <strong>계좌상세</strong>
            <span>{selectedAccount ? selectedAccount.accountStatus : "미선택"}</span>
          </div>
          {selectedAccount ? (
            <>
              <div className="balance-card">
                <span>{selectedAccount.displayName}</span>
                <strong>
                  {formatMinorAmount(
                    selectedAccount.availableBalanceMinor,
                    selectedAccount.currencyCode,
                  )}
                </strong>
                <small>출금가능금액</small>
              </div>
              <dl className="detail-list">
                <div>
                  <dt>고객계좌번호</dt>
                  <dd>{selectedAccount.accountId}</dd>
                </div>
                <div>
                  <dt>계좌번호</dt>
                  <dd>{selectedAccount.accountNumber}</dd>
                </div>
                <div>
                  <dt>보류금액</dt>
                  <dd>
                    {formatMinorAmount(
                      selectedAccount.pendingBalanceMinor,
                      selectedAccount.currencyCode,
                    )}
                  </dd>
                </div>
                <div>
                  <dt>잔액 갱신</dt>
                  <dd>{formatDateTime(selectedAccount.balanceUpdatedAt)}</dd>
                </div>
              </dl>
            </>
          ) : (
            <p className="rail-copy">계좌 선택 필요</p>
          )}
        </aside>
      </div>
    </section>
  );
}
