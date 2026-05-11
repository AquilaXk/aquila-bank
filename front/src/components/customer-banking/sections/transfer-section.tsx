import type { FormEvent } from 'react';
import type { TransferResponse, TransferReversalResponse } from '@/lib/api/types';
import { formatDateTime, formatMinorAmount } from '@/lib/customer-banking/format';
import { ResultPanel } from '../common';

export function TransferSection({
  isBusy,
  reversalForm,
  reversalResult,
  transferForm,
  transferResult,
  onReversal,
  onReversalChange,
  onTransfer,
  onTransferChange,
}: {
  isBusy: boolean;
  reversalForm: {
    transactionReference: string;
    sourceAccountId: string;
    amountMinor: string;
    reversalReason: string;
    summary: string;
  };
  reversalResult: TransferReversalResponse | null;
  transferForm: {
    sourceAccountId: string;
    targetAccountId: string;
    amountMinor: string;
    currencyCode: string;
    summary: string;
  };
  transferResult: TransferResponse | null;
  onReversal: (event: FormEvent<HTMLFormElement>) => void;
  onReversalChange: (value: {
    transactionReference: string;
    sourceAccountId: string;
    amountMinor: string;
    reversalReason: string;
    summary: string;
  }) => void;
  onTransfer: (event: FormEvent<HTMLFormElement>) => void;
  onTransferChange: (value: {
    sourceAccountId: string;
    targetAccountId: string;
    amountMinor: string;
    currencyCode: string;
    summary: string;
  }) => void;
}) {
  return (
    <section className="task-section">
      <div className="section-title">
        <div>
          <p>이체</p>
          <h1>즉시이체</h1>
        </div>
      </div>

      <div className="two-column">
        <form className="bank-form" onSubmit={onTransfer}>
          <div className="form-heading">
            <strong>이체정보 입력</strong>
            <span>요청 단위 중복 방지</span>
          </div>
          <div className="form-grid">
            <label>
              <span>출금계좌 ID</span>
              <input
                inputMode="numeric"
                onChange={(event) =>
                  onTransferChange({
                    ...transferForm,
                    sourceAccountId: event.target.value,
                  })
                }
                required
                value={transferForm.sourceAccountId}
              />
            </label>
            <label>
              <span>입금계좌 ID</span>
              <input
                inputMode="numeric"
                onChange={(event) =>
                  onTransferChange({
                    ...transferForm,
                    targetAccountId: event.target.value,
                  })
                }
                required
                value={transferForm.targetAccountId}
              />
            </label>
            <label>
              <span>금액 minor</span>
              <input
                inputMode="numeric"
                onChange={(event) =>
                  onTransferChange({
                    ...transferForm,
                    amountMinor: event.target.value,
                  })
                }
                required
                value={transferForm.amountMinor}
              />
            </label>
            <label>
              <span>통화</span>
              <input
                maxLength={3}
                onChange={(event) =>
                  onTransferChange({
                    ...transferForm,
                    currencyCode: event.target.value.toUpperCase(),
                  })
                }
                required
                value={transferForm.currencyCode}
              />
            </label>
          </div>
          <label>
            <span>받는 분 통장 표시</span>
            <input
              maxLength={120}
              onChange={(event) =>
                onTransferChange({ ...transferForm, summary: event.target.value })
              }
              required
              value={transferForm.summary}
            />
          </label>
          <button disabled={isBusy} type="submit">
            이체 실행
          </button>
        </form>

        <ResultPanel
          title="이체 결과"
          rows={
            transferResult
              ? [
                  ["거래번호", transferResult.transactionReference],
                  ["상태", transferResult.status],
                  [
                    "이체금액",
                    formatMinorAmount(
                      transferResult.amountMinor,
                      transferResult.currencyCode,
                    ),
                  ],
                  [
                    "이체 후 잔액",
                    formatMinorAmount(
                      transferResult.availableBalanceAfterMinor,
                      transferResult.currencyCode,
                    ),
                  ],
                  ["기장시각", formatDateTime(transferResult.bookedAt)],
                ]
              : []
          }
        />
      </div>

      <div className="two-column">
        <form className="bank-form" onSubmit={onReversal}>
          <div className="form-heading">
            <strong>이체 취소</strong>
            <span>원거래 reference 기준</span>
          </div>
          <label>
            <span>원거래번호</span>
            <input
              onChange={(event) =>
                onReversalChange({
                  ...reversalForm,
                  transactionReference: event.target.value,
                })
              }
              required
              value={reversalForm.transactionReference}
            />
          </label>
          <div className="form-grid">
            <label>
              <span>출금계좌 ID</span>
              <input
                inputMode="numeric"
                onChange={(event) =>
                  onReversalChange({
                    ...reversalForm,
                    sourceAccountId: event.target.value,
                  })
                }
                required
                value={reversalForm.sourceAccountId}
              />
            </label>
            <label>
              <span>취소금액 minor</span>
              <input
                inputMode="numeric"
                onChange={(event) =>
                  onReversalChange({
                    ...reversalForm,
                    amountMinor: event.target.value,
                  })
                }
                required
                value={reversalForm.amountMinor}
              />
            </label>
          </div>
          <label>
            <span>취소사유</span>
            <select
              onChange={(event) =>
                onReversalChange({
                  ...reversalForm,
                  reversalReason: event.target.value,
                })
              }
              value={reversalForm.reversalReason}
            >
              <option value="CUSTOMER_REQUEST">CUSTOMER_REQUEST</option>
              <option value="DUPLICATE">DUPLICATE</option>
              <option value="WRONG_AMOUNT">WRONG_AMOUNT</option>
              <option value="WRONG_TARGET">WRONG_TARGET</option>
              <option value="FRAUD_REPORTED">FRAUD_REPORTED</option>
            </select>
          </label>
          <label>
            <span>적요</span>
            <input
              maxLength={120}
              onChange={(event) =>
                onReversalChange({ ...reversalForm, summary: event.target.value })
              }
              required
              value={reversalForm.summary}
            />
          </label>
          <button disabled={isBusy} type="submit">
            취소 실행
          </button>
        </form>

        <ResultPanel
          title="취소 결과"
          rows={
            reversalResult
              ? [
                  ["원거래", reversalResult.originalTransactionReference],
                  ["취소거래", reversalResult.reversalTransactionReference],
                  ["상태", reversalResult.status],
                  [
                    "취소금액",
                    formatMinorAmount(
                      reversalResult.amountMinor,
                      reversalResult.currencyCode,
                    ),
                  ],
                  ["기장시각", formatDateTime(reversalResult.bookedAt)],
                ]
              : []
          }
        />
      </div>
    </section>
  );
}
