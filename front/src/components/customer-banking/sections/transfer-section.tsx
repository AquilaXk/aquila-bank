import type { FormEvent } from 'react';
import { useEffect, useState } from 'react';
import type { TransferPreviewResponse, TransferResponse, TransferReversalResponse } from '@/lib/api/types';
import { formatDateTime, formatMinorAmount } from '@/lib/customer-banking/format';
import {
  BankForm,
  BankActionBar,
  BankDialog,
  BankInput,
  BankNoticeStrip,
  FieldError,
  ReceiptPanel,
  ResultPanel,
  StatusBadge,
  WorkStateGrid,
  WorkTabs,
} from '../common';

type TransferStep =
  | "input"
  | "receiverCheck"
  | "confirm"
  | "otp"
  | "submitting"
  | "complete"
  | "failed";

function stayOnCurrentWorkTab(): void {}

function printTransferReceipt(): void {
  window.print();
}

function getTransferBlockedReasonLabel(reason: string | undefined, allowed: boolean | undefined): string {
  if (allowed) {
    return "이체 가능";
  }
  switch (reason) {
    case "LIMIT_EXCEEDED":
    case "DAILY_LIMIT_EXCEEDED":
    case "SINGLE_LIMIT_EXCEEDED":
      return "한도 확인 필요";
    case "OTP_REQUIRED":
      return "보안 확인 필요";
    case "TARGET_ACCOUNT_NOT_FOUND":
    case "INVALID_TARGET_ACCOUNT":
      return "받는 계좌 확인 필요";
    case "INSUFFICIENT_FUNDS":
      return "출금가능금액 확인 필요";
    case "PREVIEW_REQUIRED":
    default:
      return "받는 계좌 확인 필요";
  }
}

function getTransferStatusLabel(status: string | undefined): string {
  switch (status) {
    case "BOOKED":
    case "REVERSED":
      return "처리 완료";
    case "FAILED":
      return "처리 실패";
    case "PENDING":
      return "처리 중";
    default:
      return status ? "처리 확인 필요" : "-";
  }
}

export function TransferSection({
  isBusy,
  reversalForm,
  reversalResult,
  transferForm,
  transferPreview,
  transferResult,
  onPreviewTransfer,
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
    targetAccountNumber: string;
    amountMinor: string;
    currencyCode: string;
    summary: string;
  };
  transferPreview: TransferPreviewResponse | null;
  transferResult: TransferResponse | null;
  onPreviewTransfer: () => Promise<boolean>;
  onReversal: (event: FormEvent<HTMLFormElement>) => void;
  onReversalChange: (value: {
    transactionReference: string;
    sourceAccountId: string;
    amountMinor: string;
    reversalReason: string;
    summary: string;
  }) => void;
  onTransfer: (event: FormEvent<HTMLFormElement>, totpCode?: string) => Promise<boolean>;
  onTransferChange: (value: {
    sourceAccountId: string;
    targetAccountNumber: string;
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

  function handleSwapAccounts(): void {
    const prevSource = transferForm.sourceAccountId;
    const prevTarget = transferForm.targetAccountNumber;
    if (!prevSource && !prevTarget) return;
    updateTransferForm({
      ...transferForm,
      sourceAccountId: prevTarget.replace(/[^0-9]/g, ""),
      targetAccountNumber: prevSource,
    });
  }

  function handleQuickAmount(addAmountMinor: number): void {
    if (addAmountMinor === 0) {
      updateTransferForm({ ...transferForm, amountMinor: "" });
      return;
    }
    const current = Number(transferForm.amountMinor) || 0;
    const next = current + addAmountMinor;
    updateTransferForm({ ...transferForm, amountMinor: String(next) });
  }

  async function handleTransferSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (transferStep === "input") {
      setTransferStep("submitting");
      const ok = await onPreviewTransfer();
      setTransferStep(ok ? "receiverCheck" : "failed");
      return;
    }
    if (transferStep === "failed") {
      setTransferStep("input");
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
    const ok = await onTransfer(event, otpRequired ? otpCode : undefined);
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
  const transferFeeMinor = transferPreview?.feeMinor ?? 0;
  const limitMinor = transferPreview?.singleTransferLimitMinor ?? 1000000000;
  const remainingLimitMinor = transferPreview?.dailyRemainingMinor ?? limitMinor;
  const blockedReasonLabel = getTransferBlockedReasonLabel(
    transferPreview?.blockedReason,
    transferPreview?.allowed,
  );
  const otpRequired = transferPreview?.otpRequired ?? true;
  const isOverLimit = transferPreview
    ? !transferPreview.allowed
    : amountMinor > limitMinor;
  const submitLabel =
    transferStep === "receiverCheck"
      ? "받는 분 확인 완료"
      : transferStep === "confirm"
        ? "OTP 확인"
        : transferStep === "otp"
          ? "이체 실행"
          : transferStep === "failed"
            ? "입력 다시 확인"
          : "받는 분 확인";
  const sourceAccountError = transferForm.sourceAccountId ? "" : "출금계좌를 입력하세요.";
  const targetAccountError = transferForm.targetAccountNumber ? "" : "입금계좌를 입력하세요.";
  const amountError = amountMinor > 0 ? "" : "이체금액을 입력하세요.";
  const summaryError = transferForm.summary ? "" : "받는 분 통장 표시를 입력하세요.";
  const otpError =
    transferStep === "otp" && otpRequired && otpCode.length < 6
      ? "OTP 6자리를 입력하세요."
      : "";
  const recipientMismatchLabel =
    transferPreview && !transferPreview.allowed ? "수취계좌 불일치" : "수취계좌 확인";
  const limitExceededLabel = isOverLimit ? "한도 초과" : "한도 정상";
  const otpErrorLabel = otpError ? "OTP 오류" : "OTP 대기";
  const showFailedReceipt = transferStep === "failed";

  return (
    <section className="task-section compact-work-section">
      <div className="section-title">
        <div>
          <p>이체</p>
          <h1>즉시이체</h1>
        </div>
      </div>
      <WorkTabs
        active="transfer"
        items={[
          { id: "transfer", label: "즉시이체", onClick: stayOnCurrentWorkTab, disabled: true },
          { id: "reversal", label: "이체 취소", onClick: stayOnCurrentWorkTab, disabled: true },
        ]}
      />
      <BankNoticeStrip
        items={[
          { label: "이용시간", value: "00:05-23:50" },
          { label: "수수료", value: formatMinorAmount(transferFeeMinor, transferForm.currencyCode) },
          { label: "잔여한도", value: formatMinorAmount(remainingLimitMinor, transferForm.currencyCode) },
          { label: "보안 확인", value: otpRequired ? "OTP 필요" : "추가 확인 없음" },
        ]}
      />
      <div className="work-summary-strip" aria-label="이체 처리 결과">
        <span>받는 분 {transferPreview ? "확인" : "대기"}</span>
        <span>최종 확인 {transferStep === "confirm" ? "진행" : "대기"}</span>
        <span>처리 결과 {transferResult ? getTransferStatusLabel(transferResult.status) : "대기"}</span>
      </div>
      <WorkStateGrid
        label="이체 진행 상태"
        items={[
          { label: "받는 분", value: transferPreview ? "확인" : "대기" },
          {
            label: "수수료",
            value: formatMinorAmount(transferFeeMinor, transferForm.currencyCode),
          },
          {
            label: "한도",
            value: isOverLimit ? "확인 필요" : "정상",
            tone: isOverLimit ? "warn" : "success",
          },
          { label: "OTP", value: otpRequired ? "필요" : "생략" },
        ]}
      />

      <div className="two-column">
        <BankForm
          className="tight-work-form"
          meta={transferStep === "confirm" ? "최종 확인 후 실행" : "요청 단위 중복 방지"}
          onSubmit={handleTransferSubmit}
          title="이체정보 입력"
        >
          <div className="process-panel">
            <div className="form-heading">
              <strong>이체 절차</strong>
              <span>받는 분 확인 · 이체정보 확인 · OTP 확인 · 완료증</span>
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
          </div>
          <div className="transfer-verification-panel" aria-label="받는 사람 검증 결과">
            <div>
              <span>받는 사람 검증 결과</span>
              <strong>{transferPreview ? blockedReasonLabel : "검증 전"}</strong>
              <small>{recipientMismatchLabel}</small>
            </div>
            <div>
              <span>OTP 검증 상태</span>
              <strong>{transferStep === "complete" ? "확인 완료" : otpRequired ? "확인 필요" : "생략"}</strong>
              <small>{otpErrorLabel}</small>
            </div>
            <div>
              <span>한도 확인</span>
              <strong>{limitExceededLabel}</strong>
            </div>
          </div>
          <div className="transfer-risk-grid transfer-process-grid" aria-label="이체 사전 확인">
            <div>
              <span>받는 분</span>
              <strong>
                {transferPreview
                  ? `${transferPreview.targetAccount.displayName} ${transferPreview.targetAccount.maskedAccountNumber}`
                  : `계좌번호 ${transferForm.targetAccountNumber || "-"}`}
              </strong>
              <small>{blockedReasonLabel}</small>
            </div>
            <div>
              <span>수수료</span>
              <strong>
                {formatMinorAmount(transferFeeMinor, transferForm.currencyCode)}
              </strong>
              <small>이체 실행 전 최종 확인</small>
            </div>
            <div>
              <span>잔여한도</span>
              <strong className={isOverLimit ? "danger-text" : ""}>
                {formatMinorAmount(remainingLimitMinor, transferForm.currencyCode)}
              </strong>
              <small>1회 한도 {formatMinorAmount(limitMinor, transferForm.currencyCode)}</small>
            </div>
            <div>
              <span>보안 확인</span>
              <strong>
                <StatusBadge tone={otpRequired ? "warn" : "success"}>
                  {otpRequired ? "필요" : "미필요"}
                </StatusBadge>
              </strong>
              <small>OTP 또는 보안매체 확인</small>
            </div>
          </div>
          <div className="transfer-swap-widget my-3 p-4 rounded-xl bg-white/[0.02] border border-white/[0.08] backdrop-blur-sm">
            <div className="flex items-center justify-between mb-3">
              <span className="text-xs font-bold uppercase tracking-wider text-purple-300 whitespace-nowrap">스왑 송금</span>
              <div className="flex gap-1.5 flex-wrap">
                {[
                  { label: "+1만", value: 1_000_000 },
                  { label: "+5만", value: 5_000_000 },
                  { label: "+10만", value: 10_000_000 },
                  { label: "+100만", value: 100_000_000 },
                  { label: "정정", value: 0 },
                ].map((item) => (
                  <button
                    className="px-2 py-0.5 rounded text-xs font-semibold bg-white/5 text-purple-200 border border-purple-500/20 hover:bg-purple-500/20 hover:border-purple-500/40 transition-all font-mono whitespace-nowrap"
                    key={item.label}
                    onClick={() => handleQuickAmount(item.value)}
                    type="button"
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-[1fr,auto,1fr] items-center gap-3">
              <div className="p-3.5 rounded-xl bg-[#141824] border border-purple-500/20 hover:border-purple-500/40 transition-colors">
                <div className="text-[11px] font-semibold text-slate-400 mb-1 flex items-center justify-between gap-1">
                  <span className="whitespace-nowrap">출금 계좌</span>
                  <span className="text-emerald-400 font-mono text-[10px] whitespace-nowrap">출금가능</span>
                </div>
                <div className="text-sm font-bold text-white font-mono">
                  {transferForm.sourceAccountId ? `ID #${transferForm.sourceAccountId}` : "출금계좌 입력"}
                </div>
                <div className="text-xs text-slate-400 mt-1">
                  Aquila 입출금 통장
                </div>
              </div>
              <div className="flex justify-center">
                <button
                  type="button"
                  onClick={handleSwapAccounts}
                  className="w-9 h-9 rounded-full bg-purple-500/10 hover:bg-purple-500/25 border border-purple-500/30 hover:border-purple-500/60 flex items-center justify-center text-purple-300 hover:text-purple-100 shadow-[0_0_12px_rgba(168,85,247,0.25)] transition-all duration-300 group cursor-pointer active:scale-95"
                  title="출금 계좌와 입금 계좌 교환"
                  aria-label="출금 계좌와 입금 계좌 교환"
                >
                  <svg
                    className="w-4 h-4 rotate-90 md:rotate-0 transition-transform duration-300 group-hover:scale-110"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth="2.2"
                      d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4"
                    />
                  </svg>
                </button>
              </div>
              <div className="p-3.5 rounded-xl bg-[#141824] border border-white/[0.08] hover:border-purple-500/30 transition-colors">
                <div className="text-[11px] font-semibold text-slate-400 mb-1 flex items-center justify-between gap-1">
                  <span className="whitespace-nowrap">입금 대상</span>
                  <span className="text-purple-300 text-[10px] whitespace-nowrap">수취 확인</span>
                </div>
                <div className="text-sm font-bold text-white font-mono truncate">
                  {transferPreview
                    ? `${transferPreview.targetAccount.displayName}`
                    : transferForm.targetAccountNumber
                    ? transferForm.targetAccountNumber
                    : "입금계좌 입력"}
                </div>
                <div className="text-xs text-slate-400 mt-1 truncate">
                  {transferPreview?.targetAccount.maskedAccountNumber || "계좌 검증 대기"}
                </div>
              </div>
            </div>
          </div>
          <div className="form-grid">
            <BankInput
              error={sourceAccountError}
              inputMode="numeric"
              label="출금계좌 ID"
              onChange={(event) =>
                updateTransferForm({
                  ...transferForm,
                  sourceAccountId: event.target.value,
                })
              }
              required
              value={transferForm.sourceAccountId}
            />
            <BankInput
              error={targetAccountError}
              inputMode="numeric"
              label="입금계좌번호"
              onChange={(event) =>
                updateTransferForm({
                  ...transferForm,
                  targetAccountNumber: event.target.value,
                })
              }
              required
              value={transferForm.targetAccountNumber}
            />
            <BankInput
              error={amountError}
              inputMode="numeric"
              label="이체금액"
              onChange={(event) =>
                updateTransferForm({
                  ...transferForm,
                  amountMinor: event.target.value,
                })
              }
              required
              value={transferForm.amountMinor}
            />
            <BankInput
              label="통화"
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
          </div>
          <BankInput
            error={summaryError}
            label="받는 분 통장 표시"
            maxLength={120}
            onChange={(event) =>
              updateTransferForm({ ...transferForm, summary: event.target.value })
            }
            required
            value={transferForm.summary}
          />
          {transferStep === "confirm" ? (
            <div className="confirm-box" role="status">
              <strong>이체정보 확인</strong>
              <span>
                출금계좌 {transferForm.sourceAccountId}에서 입금계좌{" "}
                {transferForm.targetAccountNumber}로{" "}
                {formatMinorAmount(
                  Number(transferForm.amountMinor || 0),
                  transferForm.currencyCode,
                )}
                을 이체합니다.
                {transferPreview
                  ? ` 총 출금액은 ${formatMinorAmount(
                      transferPreview.totalDebitMinor,
                      transferPreview.currencyCode,
                    )}입니다.`
                  : ""}
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
              <FieldError message={otpError} />
            </label>
          ) : null}
          {isOverLimit ? (
            <div className="confirm-box warning" role="alert">
              <strong>이체 사전 검증 실패</strong>
              <span>{blockedReasonLabel}. 고객센터의 이체한도 메뉴에서 보안등급과 한도를 확인하세요.</span>
            </div>
          ) : null}
          <BankActionBar>
            <div className="mobile-sticky-actions">
              <button disabled={isBusy || isOverLimit} type="submit">
                {submitLabel}
              </button>
            </div>
          </BankActionBar>
        </BankForm>

        <div className="transfer-receipt transfer-receipt-panel">
          <ReceiptPanel
            action={
              <>
                <span>완료증 출력</span>
                <button
                  className="print-receipt-button"
                  disabled={!transferResult}
                  onClick={printTransferReceipt}
                  type="button"
                >
                  인쇄
                </button>
              </>
            }
            label="이체 완료증 인쇄"
            rows={
              transferResult
                ? [
                    ["거래번호", transferResult.transactionReference],
                    ["상태", getTransferStatusLabel(transferResult.status)],
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
                      "총 출금액",
                      formatMinorAmount(
                        transferPreview?.totalDebitMinor ?? transferResult.amountMinor,
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
                    ["출력구분", "이체 완료증 출력 전용"],
                    ["기장시각", formatDateTime(transferResult.bookedAt)],
                  ]
                : []
            }
            title="이체 완료증"
          />
          <BankDialog open={showFailedReceipt} title="실패 완료증">
            <dl className="detail-list">
              <div>
                <dt>처리상태</dt>
                <dd>실패 완료증</dd>
              </div>
              <div>
                <dt>사유</dt>
                <dd>{blockedReasonLabel}</dd>
              </div>
            </dl>
          </BankDialog>
        </div>
      </div>

      <div className="two-column">
        <form className="bank-form" onSubmit={onReversal}>
          <div className="form-heading">
            <strong>이체 취소</strong>
            <span>원거래번호 기준</span>
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
              <span>취소금액</span>
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
              <option value="CUSTOMER_REQUEST">고객 요청</option>
              <option value="DUPLICATE">중복 이체</option>
              <option value="WRONG_AMOUNT">금액 오류</option>
              <option value="WRONG_TARGET">받는 분 오류</option>
              <option value="FRAUD_REPORTED">사기 의심 신고</option>
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
                  ["상태", getTransferStatusLabel(reversalResult.status)],
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
