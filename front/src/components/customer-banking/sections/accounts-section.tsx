import type { AccountItem, AccountSummaryResponse } from '@/lib/api/types';
import { formatDateTime, formatMinorAmount } from '@/lib/customer-banking/format';
import {
  BankNoticeStrip,
  BankActionBar,
  BankSelect,
  BankTable,
  BankToolbar,
  EmptyState,
  PaginationBar,
  StatusBadge,
  WorkStateGrid,
  WorkTabs,
} from '../common';

function getAccountStatusLabel(status: string): string {
  switch (status) {
    case "ACTIVE":
      return "정상";
    case "SUSPENDED":
      return "지급정지";
    case "RESTRICTED":
      return "거래제한";
    case "CLOSED":
      return "해지";
    default:
      return status;
  }
}

function getAccountStatusTone(status: string): "success" | "warn" | "danger" | "muted" {
  switch (status) {
    case "ACTIVE":
      return "success";
    case "SUSPENDED":
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
  const activeCount = accounts.filter((account) => account.accountStatus === "ACTIVE").length;
  const suspendedCount = accounts.filter((account) => account.accountStatus === "SUSPENDED").length;
  const restrictedCount = accounts.filter((account) => account.accountStatus === "RESTRICTED").length;
  const lowBalanceCount = accounts.filter(
    (account) => account.availableBalanceMinor <= 0,
  ).length;

  return (
    <section className="task-section compact-work-section">
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
      <div className="work-summary-strip" aria-label="계좌 조회 결과">
        <span>조회 결과 {accounts.length}건</span>
        <span>상세 {selectedAccount ? "선택" : "대기"}</span>
        <span>다음 조회 {accountCursor ? "가능" : "없음"}</span>
      </div>
      <WorkStateGrid
        label="조회 업무 상태"
        items={[
          { label: "계좌목록", value: accounts.length > 0 ? `${accounts.length}건` : "조회 전" },
          {
            label: "상세조회",
            value: selectedAccount ? selectedAccount.accountStatus : "미선택",
          },
          { label: "다음조회", value: accountCursor ? "가능" : "없음" },
          { label: "처리상태", value: isBusy ? "조회 중" : "대기" },
        ]}
      />

      <div className="account-summary-panel" aria-label="계좌 업무 요약">
        <div>
          <span>계좌 업무 요약</span>
          <strong>{accounts.length}건</strong>
          <small>보유계좌</small>
        </div>
        <div>
          <span>계좌 상태</span>
          <strong>{selectedAccount ? selectedAccount.accountStatus : "미선택"}</strong>
          <small>상세 조회</small>
        </div>
        <div>
          <span>출금가능금액</span>
          <strong>
            {selectedAccount
              ? formatMinorAmount(
                  selectedAccount.availableBalanceMinor,
                  selectedAccount.currencyCode,
                )
              : "-"}
          </strong>
          <small>선택 계좌 기준</small>
        </div>
      </div>

      <div className="account-status-grid" aria-label="계좌 상태 요약">
        <div>
          <span>출금가능</span>
          <strong>{activeCount}건</strong>
          <small>정상 계좌</small>
        </div>
        <div>
          <span>지급정지</span>
          <strong>{suspendedCount}건</strong>
          <small>출금 제한</small>
        </div>
        <div>
          <span>거래제한</span>
          <strong>{restrictedCount}건</strong>
          <small>업무 확인 필요</small>
        </div>
        <div>
          <span>잔액부족</span>
          <strong>{lowBalanceCount}건</strong>
          <small>출금 가능액 확인</small>
        </div>
      </div>

      <section className="work-command-panel" aria-label="조회 업무">
        <div>
          <strong>조회 업무</strong>
          <span>계좌 목록을 조회하고 선택 계좌의 잔액과 보류금액을 확인합니다.</span>
        </div>
        <BankActionBar>
          <button disabled={isBusy} onClick={onLoadAccounts} type="button">
            전계좌조회
          </button>
          <button
            disabled={!selectedAccount || isBusy}
            onClick={() =>
              selectedAccount
                ? onLoadAccountDetail(selectedAccount.accountId)
                : undefined
            }
            type="button"
          >
            계좌상세조회
          </button>
          <button disabled={!accountCursor || isBusy} onClick={onLoadNextAccounts} type="button">
            다음 조회
          </button>
        </BankActionBar>
      </section>

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
                      <td>{account.accountNumber}</td>
                      <td>{account.displayName}</td>
                      <td>
                        <StatusBadge tone={getAccountStatusTone(account.accountStatus)}>
                          {getAccountStatusLabel(account.accountStatus)}
                        </StatusBadge>
                      </td>
                      <td className="amount-cell">
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
            label="계좌 keyset pagination"
            nextCursor={accountCursor}
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
