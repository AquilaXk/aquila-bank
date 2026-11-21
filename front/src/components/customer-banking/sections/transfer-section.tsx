import type { FormEvent } from "react";
import { useEffect, useState } from "react";
import type {
  CreateScheduledTransferRequest,
  SavedRecipientItem,
  SaveRecipientRequest,
  ScheduledTransferFrequency,
  ScheduledTransferItem,
  ScheduledTransferStatus,
  TransferPreviewResponse,
  TransferResponse,
  TransferReversalResponse,
} from "@/lib/api/types";
import {
  addKrwAmount,
  tryKrwToMinor,
} from "@/lib/customer-banking/currency";
import { formatDateTime, formatMinorAmount, toIsoDateTime, toLocalInputValue } from "@/lib/customer-banking/format";
import {
  getScheduledTransferFrequencyLabel,
  getScheduledTransferStatusLabel,
  getTransactionStatusLabel,
} from "@/lib/customer-banking/labels";
import { HorizonAccountBanner } from "../../oss-fintech/bank-card";
import { CalendarClockIcon, StarIcon } from "../../oss-fintech/icons";
import { CategoryBar } from "../../oss-fintech/tremor-primitives";
import {
  BankActionBar,
  BankCurrencyInput,
  BankDialog,
  BankForm,
  BankInput,
  BankNoticeStrip,
  CopyButton,
  FieldError,
  OtpCodeInput,
  ReceiptPanel,
  ResultPanel,
  StatusBadge,
  WorkStateGrid,
  WorkTabs,
} from "../common";

type TransferStep =
  | "input"
  | "receiverCheck"
  | "confirm"
  | "otp"
  | "submitting"
  | "complete"
  | "failed";

function printTransferReceipt(): void {
  window.print();
}

function getTransferBlockedReasonLabel(
  reason: string | undefined,
  allowed: boolean | undefined,
): string {
  if (allowed) {
    return "이체 가능";
  }
  switch (reason) {
    case "CURRENCY_MISMATCH":
      return "통화 불일치 확인 필요";
    case "TARGET_ACCOUNT_NOT_ACTIVE":
      return "입금계좌 거래제한 상태";
    case "SINGLE_LIMIT_EXCEEDED":
      return "1회 이체한도 초과";
    case "DAILY_LIMIT_EXCEEDED":
      return "1일 이체한도 초과";
    case "LIMIT_EXCEEDED":
      return "한도 확인 필요";
    case "OTP_REQUIRED":
      return "보안 확인 필요";
    case "TARGET_ACCOUNT_NOT_FOUND":
    case "INVALID_TARGET_ACCOUNT":
      return "받는 계좌 확인 필요";
    case "INSUFFICIENT_BALANCE":
    case "INSUFFICIENT_FUNDS":
      return "출금가능금액 부족";
    case "PREVIEW_REQUIRED":
    default:
      return "받는 계좌 확인 필요";
  }
}

export function TransferSection({
  accounts,
  isBusy,
  onCreateScheduledTransfer,
  onDeleteRecipient,
  onPreviewTransfer,
  onReversal,
  onReversalChange,
  onSaveRecipient,
  onTransfer,
  onTransferChange,
  onUpdateScheduledTransferStatus,
  recipients = [],
  reversalForm,
  reversalResult,
  scheduledTransfers = [],
  transferForm,
  transferPreview,
  transferResult,
}: {
  accounts?: Array<{
    accountId: number;
    accountNumber: string;
    displayName: string;
    currencyCode: string;
    availableBalanceMinor?: number;
  }>;
  isBusy: boolean;
  onCreateScheduledTransfer?: (request: CreateScheduledTransferRequest) => Promise<boolean>;
  onDeleteRecipient?: (recipientId: number) => Promise<boolean>;
  onPreviewTransfer: () => Promise<boolean>;
  onReversal: (event: FormEvent<HTMLFormElement>) => void;
  onReversalChange: (value: {
    transactionReference: string;
    sourceAccountId: string;
    amountKrw: string;
    reversalReason: string;
    summary: string;
    totpCode: string;
  }) => void;
  onSaveRecipient?: (request: SaveRecipientRequest) => Promise<boolean>;
  onTransfer: (event: FormEvent<HTMLFormElement>, totpCode?: string) => Promise<boolean>;
  onTransferChange: (value: {
    sourceAccountId: string;
    targetAccountNumber: string;
    amountKrw: string;
    currencyCode: string;
    summary: string;
  }) => void;
  onUpdateScheduledTransferStatus?: (
    scheduleId: number,
    status: ScheduledTransferStatus,
    totpCode?: string,
  ) => Promise<boolean>;
  recipients?: SavedRecipientItem[];
  reversalForm: {
    transactionReference: string;
    sourceAccountId: string;
    amountKrw: string;
    reversalReason: string;
    summary: string;
    totpCode: string;
  };
  reversalResult: TransferReversalResponse | null;
  scheduledTransfers?: ScheduledTransferItem[];
  transferForm: {
    sourceAccountId: string;
    targetAccountNumber: string;
    amountKrw: string;
    currencyCode: string;
    summary: string;
  };
  transferPreview: TransferPreviewResponse | null;
  transferResult: TransferResponse | null;
}) {
  const [activeTab, setActiveTab] = useState<"transfer" | "scheduled" | "reversal">("transfer");
  const [transferStep, setTransferStep] = useState<TransferStep>("input");
  const [otpCode, setOtpCode] = useState("");
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const [recipientNickname, setRecipientNickname] = useState("");
  const [scheduleResumeTotp, setScheduleResumeTotp] = useState("");
  const [scheduleForm, setScheduleForm] = useState(() => ({
    sourceAccountId: transferForm.sourceAccountId || "",
    targetAccountNumber: "",
    amountKrw: "",
    currencyCode: "KRW",
    summary: "",
    scheduleType: "MONTHLY" as ScheduledTransferFrequency,
    scheduledDay: "25",
    nextExecutionLocal: toLocalInputValue(new Date(Date.now() + 24 * 60 * 60 * 1000)),
    totpCode: "",
  }));

  useEffect(() => {
    if (transferResult) {
      setTransferStep("complete");
    }
  }, [transferResult]);

  const selectedSourceAccount = accounts?.find(
    (item) => String(item.accountId) === transferForm.sourceAccountId,
  );
  const matchedRecipient = recipients.find(
    (item) => item.accountNumber === transferForm.targetAccountNumber.trim(),
  );

  function updateTransferForm(value: typeof transferForm): void {
    setTransferStep("input");
    setOtpCode("");
    onTransferChange(value);
  }

  function handleQuickAmount(deltaKrw: number): void {
    if (deltaKrw === 0) {
      updateTransferForm({ ...transferForm, amountKrw: "" });
      return;
    }
    const nextDigits = addKrwAmount(transferForm.amountKrw, deltaKrw);
    updateTransferForm({ ...transferForm, amountKrw: nextDigits });
  }

  async function handleTransferSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitAttempted(true);
    if (transferStep === "failed") {
      setTransferStep("input");
      return;
    }
    if (transferStep === "input") {
      setTransferStep("submitting");
      const ok = await onPreviewTransfer();
      setTransferStep(ok ? "receiverCheck" : "failed");
      return;
    }
    if (transferStep === "receiverCheck") {
      setTransferStep("confirm");
      return;
    }
    if (transferStep === "confirm") {
      if (otpRequired) {
        setTransferStep("otp");
        return;
      }
      setTransferStep("submitting");
      const ok = await onTransfer(event, undefined);
      setTransferStep(ok ? "complete" : "failed");
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
  const amountMinor = tryKrwToMinor(transferForm.amountKrw) ?? 0;
  const transferFeeMinor = transferPreview?.feeMinor ?? 0;
  const limitMinor = transferPreview?.singleTransferLimitMinor ?? 1_000_000_000;
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
        ? otpRequired
          ? "OTP 확인"
          : "이체 실행"
        : transferStep === "otp"
          ? "이체 실행"
          : transferStep === "failed"
            ? "입력 다시 확인"
            : "받는 분 확인";
  const sourceAccountError = transferForm.sourceAccountId ? "" : "출금계좌를 선택하세요.";
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
  const otpRequiredLabel = otpRequired ? "필요" : "미필요";
  const securityCheckBadgeLabel =
    transferStep === "complete" ? "확인 완료" : otpRequiredLabel;
  const showFailedReceipt = transferStep === "failed";
  const hasOwnedAccounts = Boolean(accounts && accounts.length > 0);

  return (
    <section className="task-section compact-work-section">
      <div className="section-title">
        <div>
          <p>이체</p>
          <h1>즉시이체</h1>
        </div>
      </div>
      <WorkTabs
        active={activeTab}
        items={[
          { id: "transfer", label: "즉시이체", onClick: () => setActiveTab("transfer") },
          { id: "scheduled", label: "예약·자동이체", onClick: () => setActiveTab("scheduled") },
          { id: "reversal", label: "이체 취소", onClick: () => setActiveTab("reversal") },
        ]}
        label="이체 업무 전환 탭"
      />
      <BankNoticeStrip
        items={[
          { label: "이용시간", value: "00:05-23:50" },
          { label: "수수료", value: formatMinorAmount(transferFeeMinor, transferForm.currencyCode) },
          { label: "잔여한도", value: formatMinorAmount(remainingLimitMinor, transferForm.currencyCode) },
          { label: "보안 확인", value: otpRequired ? "OTP 필요" : "추가 확인 없음" },
        ]}
      />
      <WorkStateGrid
        label="이체 진행 상태"
        items={[
          { label: "받는 분", value: transferPreview ? "확인" : "대기" },
          {
            label: "한도",
            value: limitExceededLabel,
            tone: isOverLimit ? "warn" : "success",
          },
          {
            label: "최종 확인",
            value: transferStep === "confirm" ? "진행" : "대기",
          },
          {
            label: "처리 결과",
            value: transferResult ? getTransactionStatusLabel(transferResult.status) : "대기",
          },
        ]}
      />

      {activeTab === "transfer" ? (
        <div className="two-column" role="tabpanel">
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
            {selectedSourceAccount ? (
              <HorizonAccountBanner
                accountNumber={selectedSourceAccount.accountNumber}
                balanceMinor={selectedSourceAccount.availableBalanceMinor}
                currencyCode={selectedSourceAccount.currencyCode}
                label={selectedSourceAccount.displayName}
                statusLabel="출금계좌"
              />
            ) : null}
            <div className="transfer-risk-grid transfer-process-grid" aria-label="이체 사전 확인">
              <div>
                <span>받는 분</span>
                <strong>
                  {transferPreview
                    ? `${transferPreview.targetAccount.displayName?.trim() || matchedRecipient?.nickname || "입금계좌"} ${transferPreview.targetAccount.maskedAccountNumber}`
                    : `계좌번호 ${transferForm.targetAccountNumber || "-"}`}
                </strong>
                <small>{recipientMismatchLabel} · {blockedReasonLabel}</small>
              </div>
              <div>
                <span>수수료</span>
                <strong className="tabular-nums">
                  {formatMinorAmount(transferFeeMinor, transferForm.currencyCode)}
                </strong>
                <small>이체 실행 전 최종 확인</small>
              </div>
              <div>
                <span>잔여한도</span>
                <strong className={`tabular-nums ${isOverLimit ? "danger-text" : ""}`.trim()}>
                  {formatMinorAmount(remainingLimitMinor, transferForm.currencyCode)}
                </strong>
                <small className="tabular-nums">
                  1회 한도 {formatMinorAmount(limitMinor, transferForm.currencyCode)}
                </small>
                {transferPreview ? (
                  <CategoryBar
                    colors={["amber", "emerald"]}
                    label="1일 한도 잔여 비중"
                    values={[
                      Math.max(0, transferPreview.dailyUsedMinor),
                      Math.max(1, transferPreview.dailyRemainingMinor),
                    ]}
                  />
                ) : null}
              </div>
              <div>
                <span>보안 확인</span>
                <strong>
                  <StatusBadge tone={otpRequired ? "warn" : "success"}>
                    {securityCheckBadgeLabel}
                  </StatusBadge>
                </strong>
                <small>{otpErrorLabel}</small>
              </div>
            </div>
            <div className="quick-amount-bar" aria-label="빠른 금액 입력">
              <span className="quick-amount-label">빠른 금액 입력</span>
              <div className="quick-amount-buttons">
                {[
                  { label: "+1만", value: 10_000 },
                  { label: "+5만", value: 50_000 },
                  { label: "+10만", value: 100_000 },
                  { label: "+100만", value: 1_000_000 },
                  { label: "정정", value: 0 },
                ].map((item) => (
                  <button
                    key={item.label}
                    onClick={() => handleQuickAmount(item.value)}
                    type="button"
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            </div>
            {recipients.length > 0 ? (
              <div className="recipient-chip-list" aria-label="자주 쓰는 계좌">
                {recipients.map((recipient) => (
                  <button
                    className={
                      transferForm.targetAccountNumber === recipient.accountNumber
                        ? "recipient-chip active"
                        : "recipient-chip"
                    }
                    key={recipient.recipientId}
                    onClick={() =>
                      updateTransferForm({
                        ...transferForm,
                        targetAccountNumber: recipient.accountNumber,
                        currencyCode: recipient.currencyCode,
                      })
                    }
                    type="button"
                  >
                    <span className="oss-recipient-chip-avatar" aria-hidden="true">
                      {recipient.nickname.slice(0, 1)}
                    </span>
                    {recipient.favorite ? <StarIcon filled size={12} /> : null}
                    <span>
                      {recipient.nickname} ({recipient.accountNumber})
                    </span>
                  </button>
                ))}
              </div>
            ) : null}
            <div className="form-grid">
              {hasOwnedAccounts && accounts ? (
                <label className="form-field">
                  <span>출금계좌 ID</span>
                  <select
                    onChange={(event) => {
                      const selectedId = event.target.value;
                      const found = accounts.find(
                        (item) => String(item.accountId) === selectedId,
                      );
                      updateTransferForm({
                        ...transferForm,
                        sourceAccountId: selectedId,
                        currencyCode: found?.currencyCode || transferForm.currencyCode,
                      });
                    }}
                    required
                    value={transferForm.sourceAccountId}
                  >
                    <option value="">출금계좌를 선택하세요</option>
                    {accounts.map((account) => (
                      <option key={account.accountId} value={String(account.accountId)}>
                        {account.displayName} ({account.accountNumber})
                      </option>
                    ))}
                  </select>
                  <FieldError message={submitAttempted ? sourceAccountError : undefined} />
                </label>
              ) : (
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
                  placeholder="계좌조회에서 계좌를 먼저 불러오세요"
                  required
                  submitAttempted={submitAttempted}
                  value={transferForm.sourceAccountId}
                />
              )}
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
                submitAttempted={submitAttempted}
                value={transferForm.targetAccountNumber}
              />
              <BankCurrencyInput
                availableBalanceMinor={selectedSourceAccount?.availableBalanceMinor}
                error={amountError}
                label="이체금액"
                onValueChange={(digits) =>
                  updateTransferForm({
                    ...transferForm,
                    amountKrw: digits,
                  })
                }
                required
                submitAttempted={submitAttempted}
                value={transferForm.amountKrw}
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
                submitAttempted={submitAttempted}
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
              submitAttempted={submitAttempted}
              value={transferForm.summary}
            />
            {transferStep === "confirm" ? (
              <div className="confirm-box" role="status">
                <strong>이체정보 확인</strong>
                <span>
                  출금계좌 {selectedSourceAccount?.accountNumber ?? transferForm.sourceAccountId}에서
                  입금계좌 {transferForm.targetAccountNumber}로{" "}
                  {formatMinorAmount(amountMinor, transferForm.currencyCode)}을 이체합니다.
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
              <OtpCodeInput
                error={otpError}
                hint="이체 실행을 위해 6자리 인증 코드를 입력하세요."
                label="OTP 확인"
                onValueChange={setOtpCode}
                required
                submitAttempted={submitAttempted}
                value={otpCode}
              />
            ) : null}
            {isOverLimit ? (
              <div className="confirm-box warning" role="alert">
                <strong>이체 사전 검증 실패</strong>
                <span>
                  {blockedReasonLabel}. 고객센터의 이체한도 메뉴에서 보안등급과 한도를 확인하세요.
                </span>
              </div>
            ) : null}
            <BankActionBar>
              <div className="mobile-sticky-actions">
                <button
                  disabled={isBusy || (isOverLimit && transferStep !== "failed")}
                  type="submit"
                >
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
                  {transferResult ? (
                    <CopyButton
                      label="거래번호"
                      value={transferResult.transactionReference}
                    />
                  ) : null}
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
                      ["상태", getTransactionStatusLabel(transferResult.status)],
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
            {transferResult && onSaveRecipient && transferForm.targetAccountNumber.trim() ? (
              <div className="bank-form passive compact">
                <div className="form-heading">
                  <strong>자주 쓰는 계좌 등록</strong>
                  <span>{transferForm.targetAccountNumber}</span>
                </div>
                <div className="button-row compact">
                  <input
                    aria-label="수취인 별칭"
                    maxLength={60}
                    onChange={(event) => setRecipientNickname(event.target.value)}
                    placeholder="별칭 입력 (예: 가족, 관리비)"
                    value={recipientNickname}
                  />
                  <button
                    disabled={isBusy}
                    onClick={() => {
                      const label = recipientNickname.trim() || "자주 쓰는 계좌";
                      void onSaveRecipient({
                        nickname: label,
                        institutionCode: "AQUILA",
                        accountNumber: transferForm.targetAccountNumber.trim(),
                        currencyCode: transferResult.currencyCode,
                        favorite: true,
                      }).then((ok) => {
                        if (ok) {
                          setRecipientNickname("");
                        }
                      });
                    }}
                    type="button"
                  >
                    수취인 등록
                  </button>
                </div>
              </div>
            ) : null}
            {onSaveRecipient ? (
              <div className="bank-form passive compact">
                <div className="form-heading">
                  <strong>수취인 주소록</strong>
                  <span className="tabular-nums">{recipients.length}명</span>
                </div>
                {recipients.length === 0 ? (
                  <p className="field-note">등록된 자주 쓰는 계좌가 없습니다.</p>
                ) : (
                  <ul className="application-status-list">
                    {recipients.map((item) => (
                      <li key={item.recipientId}>
                        <button
                          onClick={() =>
                            updateTransferForm({
                              ...transferForm,
                              targetAccountNumber: item.accountNumber,
                              currencyCode: item.currencyCode,
                            })
                          }
                          type="button"
                        >
                          <strong>{item.nickname}</strong>
                          <span>{item.accountNumber}</span>
                        </button>
                        {onDeleteRecipient ? (
                          <button
                            disabled={isBusy}
                            onClick={() => void onDeleteRecipient(item.recipientId)}
                            type="button"
                          >
                            삭제
                          </button>
                        ) : null}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            ) : null}
            <BankDialog
              confirmLabel="입력 다시 확인"
              onClose={() => setTransferStep("input")}
              onConfirm={() => setTransferStep("input")}
              open={showFailedReceipt}
              title="이체 실패 안내"
            >
              <dl className="detail-list">
                <div>
                  <dt>처리상태</dt>
                  <dd>이체 실패 안내</dd>
                </div>
                <div>
                  <dt>사유</dt>
                  <dd>{blockedReasonLabel}</dd>
                </div>
              </dl>
            </BankDialog>
          </div>
        </div>
      ) : activeTab === "scheduled" ? (
        <div className="two-column" role="tabpanel">
          <form
            className="bank-form"
            onSubmit={(event) => {
              event.preventDefault();
              if (!onCreateScheduledTransfer) {
                return;
              }
              const sourceAccountId = Number(
                scheduleForm.sourceAccountId || transferForm.sourceAccountId,
              );
              const amountNum = tryKrwToMinor(scheduleForm.amountKrw) ?? 0;
              const scheduledDay = Number(scheduleForm.scheduledDay || "25");
              if (!sourceAccountId || amountNum <= 0) {
                return;
              }
              void onCreateScheduledTransfer({
                sourceAccountId,
                targetAccountNumber: scheduleForm.targetAccountNumber.trim(),
                amountMinor: amountNum,
                currencyCode: scheduleForm.currencyCode,
                summary: scheduleForm.summary.trim() || "예약이체",
                scheduleType: scheduleForm.scheduleType,
                scheduledDay,
                nextExecutionAt: toIsoDateTime(scheduleForm.nextExecutionLocal),
                totpCode: scheduleForm.totpCode,
              }).then((ok) => {
                if (ok) {
                  setScheduleForm((prev) => ({
                    ...prev,
                    targetAccountNumber: "",
                    amountKrw: "",
                    summary: "",
                    totpCode: "",
                  }));
                }
              });
            }}
          >
            <div className="form-heading">
              <strong>예약·자동이체 등록</strong>
              <span>OTP 인증 필수</span>
            </div>
            <div className="form-grid">
              {hasOwnedAccounts && accounts ? (
                <label className="form-field">
                  <span>출금계좌</span>
                  <select
                    onChange={(event) =>
                      setScheduleForm((prev) => ({ ...prev, sourceAccountId: event.target.value }))
                    }
                    required
                    value={scheduleForm.sourceAccountId || transferForm.sourceAccountId}
                  >
                    <option value="">출금계좌 선택</option>
                    {accounts.map((acc) => (
                      <option key={acc.accountId} value={String(acc.accountId)}>
                        {acc.displayName} ({acc.accountNumber})
                      </option>
                    ))}
                  </select>
                </label>
              ) : (
                <label className="form-field">
                  <span>출금계좌 ID</span>
                  <input
                    inputMode="numeric"
                    onChange={(event) =>
                      setScheduleForm((prev) => ({ ...prev, sourceAccountId: event.target.value }))
                    }
                    required
                    value={scheduleForm.sourceAccountId}
                  />
                </label>
              )}
              <BankInput
                label="입금계좌번호"
                onChange={(event) =>
                  setScheduleForm((prev) => ({ ...prev, targetAccountNumber: event.target.value }))
                }
                required
                value={scheduleForm.targetAccountNumber}
              />
              <BankCurrencyInput
                label="예약 이체금액"
                onValueChange={(digits) =>
                  setScheduleForm((prev) => ({ ...prev, amountKrw: digits }))
                }
                required
                value={scheduleForm.amountKrw}
              />
              <label className="form-field">
                <span>반복 주기</span>
                <select
                  onChange={(event) =>
                    setScheduleForm((prev) => ({
                      ...prev,
                      scheduleType: event.target.value as ScheduledTransferFrequency,
                    }))
                  }
                  value={scheduleForm.scheduleType}
                >
                  <option value="ONCE">1회 예약</option>
                  <option value="WEEKLY">매주 반복</option>
                  <option value="MONTHLY">매월 반복</option>
                </select>
              </label>
              <label className="form-field">
                <span>이체 실행일(1~31)</span>
                <input
                  inputMode="numeric"
                  max={31}
                  min={1}
                  onChange={(event) =>
                    setScheduleForm((prev) => ({ ...prev, scheduledDay: event.target.value }))
                  }
                  required
                  type="number"
                  value={scheduleForm.scheduledDay}
                />
              </label>
              <label className="form-field">
                <span>최초 실행일시</span>
                <input
                  onChange={(event) =>
                    setScheduleForm((prev) => ({
                      ...prev,
                      nextExecutionLocal: event.target.value,
                    }))
                  }
                  required
                  type="datetime-local"
                  value={scheduleForm.nextExecutionLocal}
                />
              </label>
            </div>
            <BankInput
              label="받는 분 통장 표시"
              maxLength={120}
              onChange={(event) =>
                setScheduleForm((prev) => ({ ...prev, summary: event.target.value }))
              }
              required
              value={scheduleForm.summary}
            />
            <OtpCodeInput
              label="OTP 인증번호"
              onValueChange={(nextCode) =>
                setScheduleForm((prev) => ({ ...prev, totpCode: nextCode }))
              }
              required
              value={scheduleForm.totpCode}
            />
            <BankActionBar>
              <button disabled={isBusy || !onCreateScheduledTransfer} type="submit">
                예약이체 등록
              </button>
            </BankActionBar>
          </form>

          <div className="bank-form passive">
            <div className="form-heading">
              <strong>예약·자동이체 현황</strong>
              <span className="tabular-nums">{scheduledTransfers.length}건</span>
            </div>
            {scheduledTransfers.some((rule) => rule.ruleStatus === "PAUSED") ? (
              <OtpCodeInput
                hint="일시중지된 예약이체를 재개할 때 6자리 인증 코드를 입력하세요."
                label="예약이체 재개 OTP (재개 시 필요)"
                onValueChange={setScheduleResumeTotp}
                value={scheduleResumeTotp}
              />
            ) : null}
            {scheduledTransfers.length === 0 ? (
              <p className="field-note">등록된 예약·자동이체 규칙이 없습니다.</p>
            ) : (
              <div className="schedule-card-list">
                {scheduledTransfers.map((rule) => (
                  <article className="schedule-card" key={rule.scheduleId}>
                    <div className="form-heading">
                      <strong className="oss-inline-icon">
                        <CalendarClockIcon size={15} />
                        <span>
                          {rule.targetAccountNumber} ·{" "}
                          {formatMinorAmount(rule.amountMinor, rule.currencyCode)}
                        </span>
                      </strong>
                      <StatusBadge tone={rule.ruleStatus === "ACTIVE" ? "success" : "warn"}>
                        {getScheduledTransferStatusLabel(rule.ruleStatus)}
                      </StatusBadge>
                    </div>
                    <p className="field-note">
                      {getScheduledTransferFrequencyLabel(rule.scheduleType)} (매기 {rule.scheduledDay}일) ·{" "}
                      {rule.summary}
                    </p>
                    <small className="tabular-nums">
                      예정일시 {formatDateTime(rule.nextExecutionAt)}
                    </small>
                    {onUpdateScheduledTransferStatus ? (
                      <div className="button-row compact">
                        <button
                          disabled={isBusy}
                          onClick={() =>
                            void onUpdateScheduledTransferStatus(
                              rule.scheduleId,
                              rule.ruleStatus === "ACTIVE" ? "PAUSED" : "ACTIVE",
                              rule.ruleStatus === "ACTIVE" ? undefined : scheduleResumeTotp,
                            )
                          }
                          type="button"
                        >
                          {rule.ruleStatus === "ACTIVE" ? "일시중지" : "재개"}
                        </button>
                        <button
                          disabled={isBusy}
                          onClick={() =>
                            void onUpdateScheduledTransferStatus(rule.scheduleId, "CANCELLED")
                          }
                          type="button"
                        >
                          해지
                        </button>
                      </div>
                    ) : null}
                  </article>
                ))}
              </div>
            )}
          </div>
        </div>
      ) : (
        <div className="two-column" role="tabpanel">
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
              {hasOwnedAccounts && accounts ? (
                <label className="form-field">
                  <span>출금계좌 ID</span>
                  <select
                    onChange={(event) =>
                      onReversalChange({
                        ...reversalForm,
                        sourceAccountId: event.target.value,
                      })
                    }
                    required
                    value={reversalForm.sourceAccountId}
                  >
                    <option value="">출금계좌를 선택하세요</option>
                    {accounts.map((account) => (
                      <option key={account.accountId} value={String(account.accountId)}>
                        {account.displayName} ({account.accountNumber})
                      </option>
                    ))}
                  </select>
                </label>
              ) : (
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
              )}
              <BankCurrencyInput
                label="취소금액"
                onValueChange={(digits) =>
                  onReversalChange({
                    ...reversalForm,
                    amountKrw: digits,
                  })
                }
                required
                value={reversalForm.amountKrw}
              />
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
            <OtpCodeInput
              hint="보안 설정에 따라 필요할 때만 6자리 코드를 입력하세요."
              label="OTP 추가 인증 (선택)"
              onValueChange={(nextCode) =>
                onReversalChange({ ...reversalForm, totpCode: nextCode })
              }
              value={reversalForm.totpCode}
            />
            <BankActionBar label="이체 취소 실행">
              <button disabled={isBusy} type="submit">
                취소 실행
              </button>
            </BankActionBar>
          </form>

          <div className="transfer-receipt">
            <ResultPanel
              title="취소 결과"
              rows={
                reversalResult
                  ? [
                      ["원거래", reversalResult.originalTransactionReference],
                      ["취소거래", reversalResult.reversalTransactionReference],
                      ["상태", getTransactionStatusLabel(reversalResult.status)],
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
            {reversalResult ? (
              <div className="receipt-copy-bar">
                <CopyButton
                  label="취소거래번호"
                  value={reversalResult.reversalTransactionReference}
                />
              </div>
            ) : null}
          </div>
        </div>
      )}
    </section>
  );
}
