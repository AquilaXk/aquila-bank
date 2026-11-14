import type { FormEvent } from "react";
import { useState } from "react";
import type {
  TransactionDetailResponse,
  TransactionItem,
  TransactionQueryResponse,
} from "@/lib/api/types";
import { formatDateTime, formatMinorAmount, toLocalInputValue } from "@/lib/customer-banking/format";
import {
  getDirectionLabel,
  getTransactionStatusLabel,
} from "@/lib/customer-banking/labels";
import {
  BankActionBar,
  BankCurrencyInput,
  BankDateRange,
  BankDrawer,
  BankNoticeStrip,
  BankToolbar,
  CopyButton,
  EmptyState,
  PaginationBar,
  StatusBadge,
  WorkStateGrid,
  WorkTabs,
} from "../common";

type TransactionFilters = {
  accountId: string;
  from: string;
  to: string;
  limit: string;
  cursor: string;
  status: string;
  direction: string;
  minAmountKrw: string;
  maxAmountKrw: string;
  transactionReference: string;
  responseShape: "full" | "slim";
};

function getTransactionStatusTone(status: string): "success" | "warn" | "danger" | "reversed" {
  switch (status) {
    case "BOOKED":
    case "POSTED":
      return "success";
    case "PENDING":
      return "warn";
    case "FAILED":
    case "REJECTED":
      return "danger";
    case "REVERSED":
    case "CANCELLED":
      return "reversed";
    default:
      return "warn";
  }
}

export function TransactionsSection({
  accounts,
  filters,
  isBusy,
  mode,
  slice,
  transactionDetail,
  transactions,
  onDetail,
  onFilterChange,
  onModeChange,
  onNext,
  onReset,
  onSearch,
}: {
  accounts?: Array<{
    accountId: number;
    accountNumber: string;
    displayName: string;
  }>;
  filters: TransactionFilters;
  isBusy: boolean;
  mode: "active" | "archive";
  slice: TransactionQueryResponse | null;
  transactionDetail: TransactionDetailResponse | null;
  transactions: TransactionItem[];
  onDetail: (transactionReference: string) => void;
  onFilterChange: (value: TransactionFilters) => void;
  onModeChange: (mode: "active" | "archive") => void;
  onNext: () => void;
  onReset: () => void;
  onSearch: (event: FormEvent<HTMLFormElement>) => void;
}) {
  const [filterOpen, setFilterOpen] = useState(true);
  const hasOwnedAccounts = Boolean(accounts && accounts.length > 0);
  const selectedAccount = accounts?.find(
    (item) => String(item.accountId) === filters.accountId,
  );
  const accountSummaryLabel = selectedAccount
    ? `${selectedAccount.displayName} (${selectedAccount.accountNumber})`
    : filters.accountId || "미선택";

  function setQuickRange(days: number): void {
    const to = new Date();
    const from = new Date(to);
    if (days === 0) {
      from.setHours(0, 0, 0, 0);
    } else {
      from.setDate(from.getDate() - days);
    }
    onFilterChange({
      ...filters,
      cursor: "",
      from: toLocalInputValue(from),
      to: toLocalInputValue(to),
    });
  }

  return (
    <section className="task-section compact-work-section">
      <WorkTabs
        active={mode}
        items={[
          {
            id: "active",
            label: "일반 거래내역",
            onClick: () => onModeChange("active"),
          },
          {
            id: "archive",
            label: "보관 거래내역",
            onClick: () => onModeChange("archive"),
          },
        ]}
        label="거래내역 구분 탭"
      />
      <div className="section-title">
        <div>
          <p>조회</p>
          <h1>거래내역 조회</h1>
        </div>
      </div>
      <BankNoticeStrip
        items={[
          { label: "조회기간", value: `${filters.from || "-"} ~ ${filters.to || "-"}` },
          { label: "계좌", value: accountSummaryLabel },
          { label: "표시건수", value: `${filters.limit}건` },
          { label: "다음 조회", value: slice?.hasNext ? "가능" : "대기" },
        ]}
      />
      <WorkStateGrid
        label="거래 조회 상태"
        items={[
          { label: "조건", value: filters.accountId ? "입력" : "대기" },
          { label: "조회 결과", value: slice ? `${transactions.length}건` : "조회 전" },
          { label: "상세", value: transactionDetail ? "선택" : "미선택" },
          { label: "다음조회", value: slice?.hasNext ? "가능" : "없음" },
        ]}
      />

      <div className="transaction-work-grid">
        <form className="bank-form filter-form mobile-filter-drawer" onSubmit={onSearch}>
          <div className="filter-summary" aria-label="현재 조건">
            <strong>거래 조건</strong>
            <span>계좌 {accountSummaryLabel}</span>
            <span>
              기간 {filters.from || "-"} ~ {filters.to || "-"}
            </span>
            <span>건수 {filters.limit}</span>
            <span>
              다음 조회 {slice?.nextCursor ? "준비됨" : "첫 조회"}
            </span>
          </div>
          <BankToolbar label="거래내역 기간 빠른 선택">
            <div className="quick-range-toolbar touch-action-strip" aria-label="기간 빠른 선택">
              <button onClick={() => setQuickRange(0)} type="button">
                오늘
              </button>
              <button onClick={() => setQuickRange(30)} type="button">
                1개월
              </button>
              <button onClick={() => setQuickRange(90)} type="button">
                3개월
              </button>
              <button onClick={onReset} type="button">
                초기화
              </button>
              <button
                aria-controls="transaction-detail-filter"
                aria-expanded={filterOpen}
                onClick={() => setFilterOpen((value) => !value)}
                type="button"
              >
                {filterOpen ? "상세조건 접기" : "상세조건 펼치기"}
              </button>
            </div>
          </BankToolbar>
          {filterOpen ? (
            <div className="filter-grid" id="transaction-detail-filter">
              {hasOwnedAccounts && accounts ? (
                <label>
                  <span>계좌 ID</span>
                  <select
                    onChange={(event) =>
                      onFilterChange({ ...filters, accountId: event.target.value })
                    }
                    required
                    value={filters.accountId}
                  >
                    <option value="">조회할 계좌를 선택하세요</option>
                    {accounts.map((account) => (
                      <option key={account.accountId} value={String(account.accountId)}>
                        {account.displayName} ({account.accountNumber})
                      </option>
                    ))}
                  </select>
                </label>
              ) : (
                <label>
                  <span>계좌 ID</span>
                  <input
                    inputMode="numeric"
                    onChange={(event) =>
                      onFilterChange({ ...filters, accountId: event.target.value })
                    }
                    required
                    value={filters.accountId}
                  />
                </label>
              )}
              <BankDateRange
                from={filters.from}
                onFromChange={(value) => onFilterChange({ ...filters, from: value })}
                onToChange={(value) => onFilterChange({ ...filters, to: value })}
                to={filters.to}
              />
              <label>
                <span>건수</span>
                <select
                  onChange={(event) =>
                    onFilterChange({ ...filters, limit: event.target.value })
                  }
                  value={filters.limit}
                >
                  <option value="20">20</option>
                  <option value="50">50</option>
                  <option value="100">100</option>
                </select>
              </label>
              <label>
                <span>상태</span>
                <select
                  onChange={(event) =>
                    onFilterChange({ ...filters, status: event.target.value })
                  }
                  value={filters.status}
                >
                  <option value="">전체</option>
                  <option value="PENDING">처리대기</option>
                  <option value="BOOKED">처리완료</option>
                  <option value="REVERSED">취소완료</option>
                  <option value="FAILED">실패</option>
                </select>
              </label>
              <label>
                <span>입출금</span>
                <select
                  onChange={(event) =>
                    onFilterChange({ ...filters, direction: event.target.value })
                  }
                  value={filters.direction}
                >
                  <option value="">전체</option>
                  <option value="DEBIT">출금</option>
                  <option value="CREDIT">입금</option>
                </select>
              </label>
              <BankCurrencyInput
                label="최소금액"
                onValueChange={(digits) =>
                  onFilterChange({
                    ...filters,
                    minAmountKrw: digits,
                  })
                }
                placeholder="최소 원화 금액"
                value={filters.minAmountKrw}
              />
              <BankCurrencyInput
                label="최대금액"
                onValueChange={(digits) =>
                  onFilterChange({
                    ...filters,
                    maxAmountKrw: digits,
                  })
                }
                placeholder="최대 원화 금액"
                value={filters.maxAmountKrw}
              />
              <label>
                <span>거래번호</span>
                <input
                  onChange={(event) =>
                    onFilterChange({
                      ...filters,
                      transactionReference: event.target.value,
                    })
                  }
                  value={filters.transactionReference}
                />
              </label>
              <label>
                <span>조회방식</span>
                <select
                  onChange={(event) =>
                    onFilterChange({
                      ...filters,
                      responseShape: event.target.value as "full" | "slim",
                    })
                  }
                  value={filters.responseShape}
                >
                  <option value="full">상세</option>
                  <option value="slim">요약</option>
                </select>
              </label>
            </div>
          ) : (
            <div className="filter-collapsed-line" id="transaction-detail-filter" role="status">
              상세조건 접힘 · 계좌, 기간, 금액 조건은 현재 값으로 유지됩니다.
            </div>
          )}
          <div className="button-row">
            <button disabled={isBusy} type="submit">
              조회
            </button>
            <button disabled={!slice?.nextCursor || isBusy} onClick={onNext} type="button">
              다음 페이지
            </button>
          </div>
        </form>

        <div className="split-work">
          <section className="table-panel embedded">
            <div className="panel-toolbar">
              <div>
                <strong>입출금 내역</strong>
                <span>
                  {slice
                    ? `${transactions.length}건 / 다음 조회 ${slice.hasNext ? "가능" : "없음"}`
                    : "조회 전"}
                </span>
              </div>
            </div>
            <div className="table-scroll-hint">거래내역 표는 좌우로 스크롤해서 볼 수 있습니다.</div>
            <div className="bank-table-wrap">
              <table className="bank-table dense-bank-table">
                <caption>거래내역 목록</caption>
                <thead>
                  <tr>
                    <th>기장일시</th>
                    <th>거래번호</th>
                    <th>입출금</th>
                    <th>상태</th>
                    <th>금액</th>
                    <th>잔액</th>
                    <th>상세</th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.length === 0 ? (
                    <tr>
                      <td colSpan={7}>
                        <EmptyState
                          title="거래내역 없음"
                          description="계좌와 기간을 입력한 뒤 조회하세요."
                        />
                      </td>
                    </tr>
                  ) : (
                    transactions.map((item) => (
                      <tr key={`${item.id}-${item.transactionReference}`}>
                        <td className="tabular-nums">{formatDateTime(item.bookedAt)}</td>
                        <td className="tabular-nums">{item.transactionReference}</td>
                        <td>{getDirectionLabel(item.direction)}</td>
                        <td>
                          <StatusBadge tone={getTransactionStatusTone(item.status)}>
                            {getTransactionStatusLabel(item.status)}
                          </StatusBadge>
                        </td>
                        <td
                          className={
                            item.direction === "DEBIT"
                              ? "amount debit font-mono tabular-nums"
                              : "amount credit font-mono tabular-nums"
                          }
                        >
                          {formatMinorAmount(item.amountMinor, item.currencyCode)}
                        </td>
                        <td className="font-mono tabular-nums">
                          {item.balanceAfterMinor == null
                            ? "-"
                            : formatMinorAmount(
                                item.balanceAfterMinor,
                                item.currencyCode,
                              )}
                        </td>
                        <td>
                          <div className="table-row-actions">
                            <button
                              disabled={isBusy}
                              onClick={() => onDetail(item.transactionReference)}
                              type="button"
                            >
                              상세
                            </button>
                            <button
                              className="receipt-link-button"
                              disabled={item.direction !== "DEBIT"}
                              onClick={() => onDetail(item.transactionReference)}
                              type="button"
                            >
                              이체확인증 출력
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
            <PaginationBar
              disabled={isBusy}
              hasNext={Boolean(slice?.hasNext)}
              label="거래내역 페이지 탐색"
              onNext={onNext}
            />
          </section>

          <BankDrawer open={Boolean(transactionDetail)} title="거래 상세내역">
            <div className="form-heading">
              <strong>거래 상세정보</strong>
              <span>
                {transactionDetail
                  ? getTransactionStatusLabel(transactionDetail.transactionStatus)
                  : "미선택"}
              </span>
            </div>
            {transactionDetail ? (
              <>
                <dl className="detail-list receipt-print-view">
                  <div>
                    <dt>거래번호</dt>
                    <dd className="tabular-nums inline-copy-cell">
                      <span>{transactionDetail.transactionReference}</span>
                      <CopyButton
                        label="거래번호"
                        value={transactionDetail.transactionReference}
                      />
                    </dd>
                  </div>
                  <div>
                    <dt>입출금</dt>
                    <dd>{getDirectionLabel(transactionDetail.direction)}</dd>
                  </div>
                  <div>
                    <dt>금액</dt>
                    <dd className="font-mono tabular-nums">
                      {formatMinorAmount(
                        transactionDetail.amountMinor,
                        transactionDetail.currencyCode,
                      )}
                    </dd>
                  </div>
                  <div>
                    <dt>잔액</dt>
                    <dd className="font-mono tabular-nums">
                      {formatMinorAmount(
                        transactionDetail.balanceAfterMinor,
                        transactionDetail.currencyCode,
                      )}
                    </dd>
                  </div>
                  <div>
                    <dt>상대방</dt>
                    <dd>{transactionDetail.counterpartyMaskedName ?? "-"}</dd>
                  </div>
                  <div>
                    <dt>처리번호</dt>
                    <dd className="tabular-nums">{transactionDetail.entryReference}</dd>
                  </div>
                  <div>
                    <dt>발생시각</dt>
                    <dd className="tabular-nums">
                      {formatDateTime(transactionDetail.occurredAt)}
                    </dd>
                  </div>
                  <div>
                    <dt>설명</dt>
                    <dd>{transactionDetail.description ?? "-"}</dd>
                  </div>
                  <div>
                    <dt>출력</dt>
                    <dd>거래 상세정보 출력 전용 · 이체확인증 출력 전용</dd>
                  </div>
                </dl>
                <BankActionBar label="거래 상세 출력">
                  <button
                    className="print-receipt-button"
                    onClick={() => window.print()}
                    type="button"
                  >
                    인쇄
                  </button>
                </BankActionBar>
              </>
            ) : (
              <EmptyState
                title="거래 미선택"
                description="입출금 내역에서 상세 버튼을 선택하세요."
              />
            )}
          </BankDrawer>
        </div>
      </div>
    </section>
  );
}
