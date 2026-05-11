import type { AccountItem, AccountSummaryResponse } from '@/lib/api/types';
import { formatDateTime, formatMinorAmount } from '@/lib/customer-banking/format';

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

      <div className="account-grid">
        <section className="table-panel embedded">
          <div className="panel-toolbar">
            <div>
              <strong>보유계좌</strong>
              <span>계좌별 잔액과 상태</span>
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
                    <td colSpan={5}>조회된 계좌가 없습니다.</td>
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
                  <dt>Account ID</dt>
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
            <p className="rail-copy">계좌 목록에서 상세 버튼을 선택하세요.</p>
          )}
        </aside>
      </div>
    </section>
  );
}
