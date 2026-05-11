import type { FormEvent } from 'react';
import type { TransactionDetailResponse, TransactionItem, TransactionQueryResponse } from '@/lib/api/types';
import { formatDateTime, formatMinorAmount } from '@/lib/customer-banking/format';

export function TransactionsSection({
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
  onSearch,
}: {
  filters: {
    accountId: string;
    from: string;
    to: string;
    limit: string;
    cursor: string;
    status: string;
    direction: string;
    minAmountMinor: string;
    maxAmountMinor: string;
    transactionReference: string;
    responseShape: "full" | "slim";
  };
  isBusy: boolean;
  mode: "active" | "archive";
  slice: TransactionQueryResponse | null;
  transactionDetail: TransactionDetailResponse | null;
  transactions: TransactionItem[];
  onDetail: (transactionReference: string) => void;
  onFilterChange: (value: {
    accountId: string;
    from: string;
    to: string;
    limit: string;
    cursor: string;
    status: string;
    direction: string;
    minAmountMinor: string;
    maxAmountMinor: string;
    transactionReference: string;
    responseShape: "full" | "slim";
  }) => void;
  onModeChange: (mode: "active" | "archive") => void;
  onNext: () => void;
  onSearch: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <section className="task-section">
      <div className="section-title">
        <div>
          <p>조회</p>
          <h1>거래내역 조회</h1>
        </div>
        <div className="tab-switch" role="tablist" aria-label="거래 조회 구분">
          <button
            aria-selected={mode === "active"}
            className={mode === "active" ? "active" : ""}
            onClick={() => onModeChange("active")}
            role="tab"
            type="button"
          >
            일반
          </button>
          <button
            aria-selected={mode === "archive"}
            className={mode === "archive" ? "active" : ""}
            onClick={() => onModeChange("archive")}
            role="tab"
            type="button"
          >
            아카이브
          </button>
        </div>
      </div>

      <form className="bank-form filter-form" onSubmit={onSearch}>
        <div className="filter-grid">
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
          <label>
            <span>시작일시</span>
            <input
              onChange={(event) =>
                onFilterChange({ ...filters, from: event.target.value })
              }
              required
              type="datetime-local"
              value={filters.from}
            />
          </label>
          <label>
            <span>종료일시</span>
            <input
              onChange={(event) =>
                onFilterChange({ ...filters, to: event.target.value })
              }
              required
              type="datetime-local"
              value={filters.to}
            />
          </label>
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
              <option value="PENDING">PENDING</option>
              <option value="BOOKED">BOOKED</option>
              <option value="REVERSED">REVERSED</option>
              <option value="FAILED">FAILED</option>
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
          <label>
            <span>최소금액 minor</span>
            <input
              inputMode="numeric"
              onChange={(event) =>
                onFilterChange({
                  ...filters,
                  minAmountMinor: event.target.value,
                })
              }
              value={filters.minAmountMinor}
            />
          </label>
          <label>
            <span>최대금액 minor</span>
            <input
              inputMode="numeric"
              onChange={(event) =>
                onFilterChange({
                  ...filters,
                  maxAmountMinor: event.target.value,
                })
              }
              value={filters.maxAmountMinor}
            />
          </label>
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
            <span>응답형태</span>
            <select
              onChange={(event) =>
                onFilterChange({
                  ...filters,
                  responseShape: event.target.value as "full" | "slim",
                })
              }
              value={filters.responseShape}
            >
              <option value="full">full</option>
              <option value="slim">slim</option>
            </select>
          </label>
        </div>
        <div className="button-row">
          <button disabled={isBusy} type="submit">
            조회
          </button>
          <button disabled={!slice?.nextCursor || isBusy} onClick={onNext} type="button">
            다음 거래
          </button>
        </div>
      </form>

      <div className="split-work">
        <section className="table-panel embedded">
          <div className="panel-toolbar">
            <div>
              <strong>거래내역</strong>
              <span>
                {slice
                  ? `${transactions.length}건 표시 / next ${slice.hasNext ? "있음" : "없음"}`
                  : "조회 전"}
              </span>
            </div>
          </div>
          <div className="bank-table-wrap">
            <table className="bank-table">
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
                    <td colSpan={7}>조회된 거래가 없습니다.</td>
                  </tr>
                ) : (
                  transactions.map((item) => (
                    <tr key={`${item.id}-${item.transactionReference}`}>
                      <td>{formatDateTime(item.bookedAt)}</td>
                      <td>{item.transactionReference}</td>
                      <td>{item.direction}</td>
                      <td>
                        <span className="status-badge">{item.status}</span>
                      </td>
                      <td className={item.direction === "DEBIT" ? "amount debit" : "amount credit"}>
                        {formatMinorAmount(item.amountMinor, item.currencyCode)}
                      </td>
                      <td>
                        {item.balanceAfterMinor == null
                          ? "-"
                          : formatMinorAmount(
                              item.balanceAfterMinor,
                              item.currencyCode,
                            )}
                      </td>
                      <td>
                        <button
                          disabled={isBusy}
                          onClick={() => onDetail(item.transactionReference)}
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
            <strong>거래상세</strong>
            <span>{transactionDetail?.transactionStatus ?? "미선택"}</span>
          </div>
          {transactionDetail ? (
            <dl className="detail-list">
              <div>
                <dt>거래번호</dt>
                <dd>{transactionDetail.transactionReference}</dd>
              </div>
              <div>
                <dt>입출금</dt>
                <dd>{transactionDetail.direction}</dd>
              </div>
              <div>
                <dt>금액</dt>
                <dd>
                  {formatMinorAmount(
                    transactionDetail.amountMinor,
                    transactionDetail.currencyCode,
                  )}
                </dd>
              </div>
              <div>
                <dt>잔액</dt>
                <dd>
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
                <dt>Ledger</dt>
                <dd>{transactionDetail.entryReference}</dd>
              </div>
              <div>
                <dt>발생시각</dt>
                <dd>{formatDateTime(transactionDetail.occurredAt)}</dd>
              </div>
              <div>
                <dt>설명</dt>
                <dd>{transactionDetail.description ?? "-"}</dd>
              </div>
            </dl>
          ) : (
            <p className="rail-copy">거래내역에서 상세 버튼을 선택하세요.</p>
          )}
        </aside>
      </div>
    </section>
  );
}
