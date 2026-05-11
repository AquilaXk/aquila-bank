import type { FormEvent } from 'react';
import { useEffect, useState } from 'react';
import type { TransferResponse, TransferReversalResponse } from '@/lib/api/types';
import { formatDateTime, formatMinorAmount } from '@/lib/customer-banking/format';
import { ResultPanel } from '../common';

type TransferStep =
  | "input"
  | "receiverCheck"
  | "confirm"
  | "otp"
  | "submitting"
  | "complete"
  | "failed";

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
  onTransfer: (event: FormEvent<HTMLFormElement>) => Promise<boolean>;
  onTransferChange: (value: {
    sourceAccountId: string;
    targetAccountId: string;
    amountMinor: string;
    currencyCode: string;
    summary: string;
  }) => void;
}) {
  const [transferStep, setTransferStep] = useState<TransferStep>("input");
  const [otpCode, setOtpCode] = useState("");

  useEffect(() => {
    if (transferResult) {
      setTransferStep("complete");
    }
  }, [transferResult]);

  function updateTransferForm(value: typeof transferForm): void {
    setTransferStep("input");
    setOtpCode("");
    onTransferChange(value);
  }

  async function handleTransferSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (transferStep === "input") {
      setTransferStep("receiverCheck");
      return;
    }
    if (transferStep === "receiverCheck") {
      setTransferStep("confirm");
      return;
    }
    if (transferStep === "confirm") {
      setTransferStep("otp");
      return;
    }
    if (transferStep !== "otp" || otpCode.length < 6) {
      setTransferStep("otp");
      return;
    }

    setTransferStep("submitting");
    const ok = await onTransfer(event);
    setTransferStep(ok ? "complete" : "failed");
  }

  const stepLabels: Array<{ id: TransferStep; label: string }> = [
    { id: "input", label: "입력" },
    { id: "receiverCheck", label: "받는 분 확인" },
    { id: "confirm", label: "이체 확인" },
    { id: "otp", label: "OTP 확인" },
    { id: "submitting", label: "처리 중" },
    { id: "complete", label: "완료" },
    { id: "failed", label: "실패" },
  ];
  const amountMinor = Number(transferForm.amountMinor || 0);
  const transferFeeMinor = amountMinor >= 100000000 ? 500 : 0;
  const limitMinor = 1000000000;
  const isOverLimit = amountMinor > limitMinor;
  const submitLabel =
    transferStep === "receiverCheck"
      ? "받는 분 확인 완료"
      : transferStep === "confirm"
        ? "OTP 확인"
        : transferStep === "otp"
          ? "이체 실행"
          : "받는 분 확인";

  return (
    <section className="task-section">
      <div className="section-title">
        <div>
          <p>이체</p>
          <h1>즉시이체</h1>
        </div>
      </div>

      <div className="two-column">
        <form className="bank-form" onSubmit={handleTransferSubmit}>
          <div className="form-heading">
            <strong>이체정보 입력</strong>
            <span>{transferStep === "confirm" ? "최종 확인 후 실행" : "요청 단위 중복 방지"}</span>
          </div>
          <ol className="stepper" aria-label="이체 진행 단계">
            {stepLabels.map((step) => (
              <li
                className={transferStep === step.id ? "active" : ""}
                key={step.id}
              >
                {step.label}
              </li>
            ))}
          </ol>
          <div className="transfer-risk-grid" aria-label="이체 사전 확인">
            <div>
              <span>받는 분 확인</span>
              <strong>계좌 ID {transferForm.targetAccountId || "-"}</strong>
            </div>
            <div>
              <span>수수료</span>
              <strong>
                {formatMinorAmount(transferFeeMinor, transferForm.currencyCode)}
              </strong>
            </div>
            <div>
              <span>이체한도</span>
              <strong className={isOverLimit ? "danger-text" : ""}>
                {formatMinorAmount(limitMinor, transferForm.currencyCode)}
              </strong>
            </div>
          </div>
          <div className="form-grid">
            <label>
              <span>출금계좌 ID</span>
              <input
                inputMode="numeric"
                onChange={(event) =>
                  updateTransferForm({
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
                  updateTransferForm({
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
                  updateTransferForm({
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
                  updateTransferForm({
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
                updateTransferForm({ ...transferForm, summary: event.target.value })
              }
              required
              value={transferForm.summary}
            />
          </label>
          {transferStep === "confirm" ? (
            <div className="confirm-box" role="status">
              <strong>이체 확인</strong>
              <span>
                출금계좌 {transferForm.sourceAccountId}에서 입금계좌{" "}
                {transferForm.targetAccountId}로{" "}
                {formatMinorAmount(
                  Number(transferForm.amountMinor || 0),
                  transferForm.currencyCode,
                )}
                을 이체합니다.
              </span>
            </div>
          ) : null}
          {transferStep === "otp" ? (
            <label>
              <span>OTP 확인</span>
              <input
                inputMode="numeric"
                maxLength={6}
                onChange={(event) => setOtpCode(event.target.value)}
                placeholder="6자리"
                required
                value={otpCode}
              />
            </label>
          ) : null}
          {isOverLimit ? (
            <div className="confirm-box warning" role="alert">
              <strong>이체한도 초과</strong>
              <span>고객센터의 이체한도 메뉴에서 보안등급과 한도를 확인하세요.</span>
            </div>
          ) : null}
          <button disabled={isBusy || isOverLimit} type="submit">
            {submitLabel}
          </button>
        </form>

        <div className="transfer-receipt">
          <ResultPanel
            title="이체 완료증"
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
                      "수수료",
                      formatMinorAmount(transferFeeMinor, transferResult.currencyCode),
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
