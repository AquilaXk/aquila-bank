import {
  useEffect,
  useId,
  useRef,
  useState,
  type ChangeEvent,
  type ChangeEventHandler,
  type FocusEventHandler,
  type FormEventHandler,
  type KeyboardEvent,
  type PointerEvent,
  type ReactNode,
} from "react";
import {
  countDigitsBeforeCaret,
  findCaretForDigitCount,
  formatKoreanCurrencyUnits,
  formatKrwDisplay,
  parseKrwDigits,
  tryKrwToMinor,
} from "@/lib/customer-banking/currency";
import { formatMinorAmount } from "@/lib/customer-banking/format";

export function ResultPanel({ title, rows }: { title: string; rows: string[][] }) {
  return (
    <div className="result-panel">
      <div className="form-heading">
        <strong>{title}</strong>
        <span>{rows.length > 0 ? "완료" : "대기"}</span>
      </div>
      {rows.length === 0 ? (
        <p className="rail-copy">처리 결과 없음</p>
      ) : (
        <dl className="detail-list">
          {rows.map(([key, value]) => (
            <div key={key}>
              <dt>{key}</dt>
              <dd>{value}</dd>
            </div>
          ))}
        </dl>
      )}
    </div>
  );
}

export function WorkPanel({
  children,
  className = "",
  title,
  meta,
}: {
  children: ReactNode;
  className?: string;
  title: string;
  meta?: string;
}) {
  return (
    <section className={`table-panel bank-work-panel ${className}`.trim()}>
      <div className="panel-toolbar">
        <div>
          <strong>{title}</strong>
          {meta ? <span>{meta}</span> : null}
        </div>
      </div>
      {children}
    </section>
  );
}

export function BankForm({
  children,
  className = "",
  meta,
  onSubmit,
  passive = false,
  title,
}: {
  children: ReactNode;
  className?: string;
  meta?: string;
  onSubmit?: FormEventHandler<HTMLFormElement>;
  passive?: boolean;
  title: string;
}) {
  return (
    <form
      className={`bank-form ${passive ? "passive" : ""} ${className}`.trim()}
      onSubmit={onSubmit}
    >
      <div className="form-heading">
        <strong>{title}</strong>
        {meta ? <span>{meta}</span> : null}
      </div>
      {children}
    </form>
  );
}

export function BankTable({
  caption,
  children,
  className = "",
  dense = false,
}: {
  caption: string;
  children: ReactNode;
  className?: string;
  dense?: boolean;
}) {
  return (
    <div aria-label={caption} className="bank-table-wrap" role="region" tabIndex={0}>
      <table className={`bank-table ${dense ? "dense-bank-table" : ""} ${className}`.trim()}>
        <caption>{caption}</caption>
        {children}
      </table>
    </div>
  );
}

export function BankSelect({
  label,
  onChange,
  options,
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  options: Array<{ label: string; value: string | number }>;
  value: string | number;
}) {
  return (
    <label className="inline-control">
      <span>{label}</span>
      <select onChange={(event) => onChange(event.target.value)} value={value}>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}

export function BankInput({
  autoComplete,
  error,
  inputMode,
  label,
  maxLength,
  onBlur,
  onChange,
  placeholder,
  required,
  submitAttempted = false,
  type = "text",
  value,
}: {
  autoComplete?: string;
  error?: string;
  inputMode?: "none" | "text" | "tel" | "url" | "email" | "numeric" | "decimal" | "search";
  label: string;
  maxLength?: number;
  onBlur?: FocusEventHandler<HTMLInputElement>;
  onChange: ChangeEventHandler<HTMLInputElement>;
  placeholder?: string;
  required?: boolean;
  submitAttempted?: boolean;
  type?: string;
  value: string | number;
}) {
  const [touched, setTouched] = useState(false);

  return (
    <label className="form-field bank-input">
      <span>{label}</span>
      <input
        autoComplete={autoComplete}
        inputMode={inputMode}
        maxLength={maxLength}
        onBlur={(event) => {
          setTouched(true);
          onBlur?.(event);
        }}
        onChange={(event) => {
          setTouched(true);
          onChange(event);
        }}
        placeholder={placeholder}
        required={required}
        type={type}
        value={value}
      />
      <FieldError message={touched || submitAttempted ? error : undefined} />
    </label>
  );
}

export function BankCurrencyInput({
  availableBalanceMinor,
  error,
  label,
  onValueChange,
  placeholder = "금액 입력 (원)",
  required,
  submitAttempted = false,
  value,
}: {
  availableBalanceMinor?: number;
  error?: string;
  label: string;
  onValueChange: (digits: string) => void;
  placeholder?: string;
  required?: boolean;
  submitAttempted?: boolean;
  value: string;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [touched, setTouched] = useState(false);
  const displayValue = formatKrwDisplay(value);
  const koreanUnits = formatKoreanCurrencyUnits(value);
  const minor = tryKrwToMinor(value);
  const exceedsBalance =
    typeof availableBalanceMinor === "number" &&
    minor !== undefined &&
    minor > availableBalanceMinor;

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    setTouched(true);
    const input = event.target;
    const rawValue = input.value;
    const caretPos = input.selectionStart ?? rawValue.length;
    const digitsBeforeCaret = countDigitsBeforeCaret(rawValue, caretPos);
    const nextDigits = parseKrwDigits(rawValue);
    onValueChange(nextDigits);

    const nextFormatted = formatKrwDisplay(nextDigits);
    const targetCaret = findCaretForDigitCount(nextFormatted, digitsBeforeCaret);
    if (typeof window !== "undefined" && typeof window.requestAnimationFrame === "function") {
      window.requestAnimationFrame(() => {
        if (inputRef.current && document.activeElement === inputRef.current) {
          inputRef.current.setSelectionRange(targetCaret, targetCaret);
        }
      });
    }
  }

  return (
    <label className="form-field bank-input bank-currency-field">
      <span>{label}</span>
      <div className="currency-input-wrap">
        <input
          ref={inputRef}
          className="currency-input"
          inputMode="numeric"
          onBlur={() => setTouched(true)}
          onChange={handleChange}
          placeholder={placeholder}
          required={required}
          type="text"
          value={displayValue}
        />
        <span aria-hidden="true" className="currency-unit-suffix">
          원
        </span>
      </div>
      {koreanUnits ? (
        <small className="field-note currency-readout">
          {koreanUnits}
          {typeof availableBalanceMinor === "number"
            ? ` · 출금 가능 잔액 ${formatMinorAmount(availableBalanceMinor)}`
            : ""}
        </small>
      ) : null}
      {exceedsBalance ? (
        <small className="field-error currency-limit-warn" role="alert">
          출금 가능 잔액({formatMinorAmount(availableBalanceMinor as number)})을 초과했습니다.
        </small>
      ) : null}
      <FieldError message={touched || submitAttempted ? error : undefined} />
    </label>
  );
}

function extractSixDigits(raw: string): string {
  let out = "";
  for (let i = 0; i < raw.length; i += 1) {
    const code = raw.charCodeAt(i);
    if (code >= 48 && code <= 57) {
      out += raw[i];
      if (out.length === 6) {
        break;
      }
    }
  }
  return out;
}

export function OtpCodeInput({
  disabled = false,
  error,
  hint = "인증 앱의 6자리 숫자를 입력하세요.",
  label,
  onValueChange,
  required = false,
  submitAttempted = false,
  value,
}: {
  disabled?: boolean;
  error?: string;
  hint?: string;
  label: string;
  onValueChange: (nextCode: string) => void;
  required?: boolean;
  submitAttempted?: boolean;
  value: string;
}) {
  const inputId = useId();
  const [touched, setTouched] = useState(false);
  const [focused, setFocused] = useState(false);
  const cleanCode = extractSixDigits(value);
  const activeSlot = Math.min(cleanCode.length, 5);

  return (
    <div className="form-field bank-input otp-code-field">
      <label className="otp-field-label" htmlFor={inputId}>
        {label}
      </label>
      <small className="field-note otp-field-hint">{hint}</small>
      <div className={`otp-code-frame ${focused ? "focused" : ""}`.trim()}>
        <div aria-hidden="true" className="otp-cells">
          {[0, 1, 2, 3, 4, 5].map((index) => {
            const digit = cleanCode[index] ?? "";
            const isActive = focused && index === activeSlot;
            return (
              <span
                className={`otp-cell ${digit ? "filled" : ""} ${isActive ? "active" : ""}`.trim()}
                key={index}
              >
                {digit || "·"}
              </span>
            );
          })}
        </div>
        <input
          autoComplete="one-time-code"
          className="otp-native-overlay"
          disabled={disabled}
          id={inputId}
          inputMode="numeric"
          maxLength={6}
          onBlur={() => {
            setFocused(false);
            setTouched(true);
          }}
          onChange={(event) => {
            setTouched(true);
            onValueChange(extractSixDigits(event.target.value));
          }}
          onFocus={() => setFocused(true)}
          pattern="\d{6}"
          required={required}
          type="text"
          value={cleanCode}
        />
      </div>
      <FieldError message={touched || submitAttempted ? error : undefined} />
    </div>
  );
}

export function CopyButton({
  className = "",
  label = "복사",
  value,
}: {
  className?: string;
  label?: string;
  value: string;
}) {
  const [copied, setCopied] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    };
  }, []);

  async function handleCopy() {
    if (!value) {
      return;
    }
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
      timerRef.current = setTimeout(() => setCopied(false), 1800);
    } catch {
      setCopied(false);
    }
  }

  return (
    <button
      aria-label={`${label} 복사`}
      className={`copy-action-button ${className}`.trim()}
      disabled={!value}
      onClick={() => void handleCopy()}
      type="button"
    >
      {copied ? "복사됨" : label}
    </button>
  );
}

export function BankDateRange({
  from,
  onFromChange,
  onToChange,
  to,
}: {
  from: string;
  onFromChange: (value: string) => void;
  onToChange: (value: string) => void;
  to: string;
}) {
  return (
    <div className="bank-date-range" aria-label="기간 선택">
      <label>
        <span>시작일시</span>
        <input
          onChange={(event) => onFromChange(event.target.value)}
          required
          type="datetime-local"
          value={from}
        />
      </label>
      <label>
        <span>종료일시</span>
        <input
          onChange={(event) => onToChange(event.target.value)}
          required
          type="datetime-local"
          value={to}
        />
      </label>
    </div>
  );
}

export function BankActionBar({
  children,
  align = "end",
  label,
}: {
  children: ReactNode;
  align?: "start" | "end" | "between";
  label?: string;
}) {
  return (
    <div aria-label={label} className={`bank-action-bar align-${align}`}>
      {children}
    </div>
  );
}

export function BankToolbar({
  children,
  label,
}: {
  children: ReactNode;
  label: string;
}) {
  return (
    <div className="bank-toolbar" aria-label={label}>
      {children}
    </div>
  );
}

export function BankDrawer({
  children,
  open,
  title,
}: {
  children: ReactNode;
  open: boolean;
  title: string;
}) {
  const asideRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (asideRef.current) {
      asideRef.current.inert = !open;
    }
  }, [open]);

  return (
    <aside
      ref={asideRef}
      aria-hidden={!open}
      aria-label={title}
      className={open ? "bank-drawer open" : "bank-drawer"}
      hidden={!open}
    >
      <div className="form-heading">
        <strong>{title}</strong>
        <span>{open ? "열림" : "닫힘"}</span>
      </div>
      {children}
    </aside>
  );
}

export function BankDialog({
  busy = false,
  cancelLabel = "취소",
  children,
  confirmLabel,
  confirmTone = "primary",
  dismissOnBackdrop = true,
  onClose,
  onConfirm,
  open,
  title,
}: {
  busy?: boolean;
  cancelLabel?: string;
  children: ReactNode;
  confirmLabel?: string;
  confirmTone?: "danger" | "primary";
  dismissOnBackdrop?: boolean;
  onClose?: () => void;
  onConfirm?: () => void;
  open: boolean;
  title: string;
}) {
  const titleId = useId();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const pointerDownOutsideRef = useRef(false);
  const previousFocusRef = useRef<HTMLElement | null>(null);
  const allowBackdropClose = !busy && dismissOnBackdrop && confirmTone !== "danger";

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) {
      return;
    }
    dialog.setAttribute("closedby", busy ? "none" : allowBackdropClose ? "any" : "closerequest");
    if (open) {
      if (!dialog.open) {
        previousFocusRef.current =
          document.activeElement instanceof HTMLElement ? document.activeElement : null;
        if (typeof dialog.showModal === "function") {
          dialog.showModal();
        } else {
          dialog.setAttribute("open", "");
        }
      }
    } else if (dialog.open) {
      if (typeof dialog.close === "function") {
        dialog.close();
      } else {
        dialog.removeAttribute("open");
      }
      previousFocusRef.current?.focus();
    }
  }, [allowBackdropClose, busy, open]);

  function isPointOutsidePanel(clientX: number, clientY: number): boolean {
    const panel = panelRef.current;
    if (!panel) {
      return false;
    }
    const rect = panel.getBoundingClientRect();
    return (
      clientX < rect.left ||
      clientX > rect.right ||
      clientY < rect.top ||
      clientY > rect.bottom
    );
  }

  function handlePointerDown(event: PointerEvent<HTMLDialogElement>) {
    pointerDownOutsideRef.current =
      allowBackdropClose &&
      event.target === dialogRef.current &&
      isPointOutsidePanel(event.clientX, event.clientY);
  }

  function handlePointerUp(event: PointerEvent<HTMLDialogElement>) {
    const shouldClose =
      allowBackdropClose &&
      pointerDownOutsideRef.current &&
      event.target === dialogRef.current &&
      isPointOutsidePanel(event.clientX, event.clientY);
    pointerDownOutsideRef.current = false;
    if (shouldClose) {
      onClose?.();
    }
  }

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      className="bank-dialog"
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) {
          onClose?.();
        }
      }}
      onClose={() => {
        previousFocusRef.current?.focus();
      }}
      onPointerDown={handlePointerDown}
      onPointerUp={handlePointerUp}
    >
      <div ref={panelRef} className="bank-dialog-panel">
        <div className="form-heading">
          <strong id={titleId}>{title}</strong>
          {onClose ? (
            <button
              aria-label={`${title} 닫기`}
              className="dialog-close-button"
              disabled={busy}
              onClick={onClose}
              type="button"
            >
              닫기
            </button>
          ) : null}
        </div>
        <div className="bank-dialog-body">{children}</div>
        {onConfirm ? (
          <div className="bank-dialog-actions">
            <button
              autoFocus={confirmTone === "danger"}
              disabled={busy}
              onClick={onClose}
              type="button"
            >
              {cancelLabel}
            </button>
            <button
              className={confirmTone === "danger" ? "danger-button" : "primary-confirm-button"}
              disabled={busy}
              onClick={onConfirm}
              type="button"
            >
              {confirmLabel ?? "확인"}
            </button>
          </div>
        ) : null}
      </div>
    </dialog>
  );
}

export function FormField({
  children,
  label,
  note,
}: {
  children: ReactNode;
  label: string;
  note?: string;
}) {
  return (
    <label className="form-field">
      <span>{label}</span>
      {children}
      {note ? <small className="field-note">{note}</small> : null}
    </label>
  );
}

export function FieldError({ message }: { message?: string }) {
  return message ? (
    <small className="field-error" role="alert">
      {message}
    </small>
  ) : null;
}

export function StatusBadge({
  children,
  className = "",
  tone = "normal",
}: {
  children: ReactNode;
  className?: string;
  tone?: "normal" | "success" | "warn" | "danger" | "muted" | "reversed";
}) {
  return <span className={`status-badge ${tone} ${className}`.trim()}>{children}</span>;
}

export function ReceiptPanel({
  action,
  className = "",
  label,
  rows,
  title,
}: {
  action?: ReactNode;
  className?: string;
  label: string;
  rows: string[][];
  title: string;
}) {
  return (
    <div
      aria-label={label}
      className={`receipt-panel transfer-print-receipt receipt-print-view ${className}`.trim()}
    >
      {action ? <div className="receipt-action-row">{action}</div> : null}
      <ResultPanel rows={rows} title={title} />
    </div>
  );
}

export function PaginationBar({
  disabled,
  hasNext,
  hasPrev = false,
  label,
  onFirst,
  onNext,
  onPrev,
}: {
  disabled?: boolean;
  hasNext: boolean;
  hasPrev?: boolean;
  label: string;
  onFirst?: () => void;
  onNext: () => void;
  onPrev?: () => void;
}) {
  return (
    <div className="pagination-bar" aria-label={label}>
      <span>페이지 탐색</span>
      <strong>{hasNext ? "추가 내역 조회 가능" : "마지막 페이지"}</strong>
      <div className="pagination-actions">
        {onFirst ? (
          <button disabled={!hasPrev || disabled} onClick={onFirst} type="button">
            처음으로
          </button>
        ) : null}
        {onPrev ? (
          <button disabled={!hasPrev || disabled} onClick={onPrev} type="button">
            이전 페이지
          </button>
        ) : null}
        <button disabled={!hasNext || disabled} onClick={onNext} type="button">
          다음 페이지
        </button>
      </div>
    </div>
  );
}

export function WorkStateGrid({
  label,
  items,
}: {
  label: string;
  items: Array<{ label: string; value: string; tone?: "normal" | "warn" | "success" }>;
}) {
  return (
    <div className="work-state-grid" aria-label={label}>
      {items.map((item) => (
        <div className={item.tone ? `tone-${item.tone}` : undefined} key={item.label}>
          <span>{item.label}</span>
          <strong>{item.value}</strong>
        </div>
      ))}
    </div>
  );
}

export function EmptyState({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  return (
    <div className="empty-state-panel" role="status">
      <strong>{title}</strong>
      <span>{description}</span>
    </div>
  );
}

export function WorkTabs({
  active,
  items,
  label = "업무 전환 탭",
}: {
  active: string;
  items: Array<{
    id: string;
    label: string;
    onClick: () => void;
    disabled?: boolean;
  }>;
  label?: string;
}) {
  const tabRefs = useRef<Array<HTMLButtonElement | null>>([]);

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>, currentIndex: number) {
    const enabledIndexes: number[] = [];
    for (let i = 0; i < items.length; i += 1) {
      if (!items[i].disabled) {
        enabledIndexes.push(i);
      }
    }
    if (enabledIndexes.length === 0) {
      return;
    }

    let nextIndex: number | null = null;
    if (event.key === "ArrowRight") {
      const pos = enabledIndexes.indexOf(currentIndex);
      nextIndex = enabledIndexes[(pos + 1) % enabledIndexes.length];
    } else if (event.key === "ArrowLeft") {
      const pos = enabledIndexes.indexOf(currentIndex);
      nextIndex = enabledIndexes[(pos - 1 + enabledIndexes.length) % enabledIndexes.length];
    } else if (event.key === "Home") {
      nextIndex = enabledIndexes[0];
    } else if (event.key === "End") {
      nextIndex = enabledIndexes[enabledIndexes.length - 1];
    }

    if (nextIndex !== null) {
      event.preventDefault();
      items[nextIndex].onClick();
      tabRefs.current[nextIndex]?.focus();
    }
  }

  return (
    <div aria-label={label} className="work-tabs" role="tablist">
      {items.map((item, index) => {
        const selected = active === item.id;
        return (
          <button
            ref={(el) => {
              tabRefs.current[index] = el;
            }}
            aria-selected={selected}
            className={selected ? "active" : undefined}
            disabled={item.disabled}
            key={item.id}
            onClick={item.onClick}
            onKeyDown={(event) => handleKeyDown(event, index)}
            role="tab"
            tabIndex={selected ? 0 : -1}
            type="button"
          >
            {item.label}
          </button>
        );
      })}
    </div>
  );
}

export function BankNoticeStrip({
  items,
}: {
  items: Array<{ label: string; value: string }>;
}) {
  return (
    <dl className="bank-notice-strip">
      {items.map((item) => (
        <div key={item.label}>
          <dt>{item.label}</dt>
          <dd>{item.value}</dd>
        </div>
      ))}
    </dl>
  );
}

const HANGUL_CHOSEONG = [
  "ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ", "ㅂ", "ㅃ",
  "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅉ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ",
];

export function toChoseongSearchKey(text: string): string {
  let out = "";
  for (let i = 0; i < text.length; i += 1) {
    const code = text.charCodeAt(i);
    if (code >= 0xac00 && code <= 0xd7a3) {
      out += HANGUL_CHOSEONG[Math.floor((code - 0xac00) / 588)];
    } else {
      out += text[i].toLowerCase();
    }
  }
  return out;
}

export function matchesKoreanQuery(target: string, rawQuery: string): boolean {
  const needle = rawQuery.trim().toLowerCase();
  if (!needle) {
    return true;
  }
  if (target.toLowerCase().includes(needle)) {
    return true;
  }
  return toChoseongSearchKey(target).includes(toChoseongSearchKey(needle));
}

export type BankToastItem = {
  id: string;
  title: string;
  message: string;
  tone?: "info" | "success" | "error";
};

export function NotificationToastRegion({
  items,
  onDismiss,
}: {
  items: BankToastItem[];
  onDismiss: (id: string) => void;
}) {
  const regionRef = useRef<HTMLDivElement>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [paused, setPaused] = useState(false);

  useEffect(() => {
    if (paused || modalOpen || items.length === 0) {
      return;
    }
    const oldestId = items[0].id;
    const timer = setTimeout(() => {
      onDismiss(oldestId);
    }, 6000);
    return () => clearTimeout(timer);
  }, [items, modalOpen, onDismiss, paused]);

  useEffect(() => {
    if (typeof document === "undefined") {
      return;
    }
    const updateModalState = () => {
      setModalOpen(Boolean(document.querySelector("dialog[open]")));
    };
    updateModalState();
    const observer = new MutationObserver(updateModalState);
    observer.observe(document.body, {
      attributes: true,
      attributeFilter: ["open"],
      subtree: true,
      childList: true,
    });
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const el = regionRef.current;
    if (!el || typeof el.showPopover !== "function") {
      return;
    }
    try {
      if (items.length > 0 && !modalOpen) {
        if (!el.matches(":popover-open")) {
          el.showPopover();
        }
      } else if (el.matches(":popover-open")) {
        el.hidePopover?.();
      }
    } catch {
      // popover API 미지원 환경에서는 고정 컨테이너로 안전하게 동작
    }
  }, [items.length, modalOpen]);

  if (items.length === 0 || modalOpen) {
    return null;
  }

  return (
    <div
      ref={regionRef}
      aria-live="polite"
      className="bank-toast-region"
      onBlur={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      popover="manual"
      role="status"
    >
      {items.map((item) => (
        <div className={`bank-toast ${item.tone ?? "info"}`} key={item.id}>
          <div>
            <strong>{item.title}</strong>
            <p>{item.message}</p>
          </div>
          <button
            aria-label={`${item.title} 알림 닫기`}
            onClick={() => onDismiss(item.id)}
            type="button"
          >
            닫기
          </button>
        </div>
      ))}
    </div>
  );
}

export type CommandPaletteEntry = {
  id: string;
  label: string;
  group: string;
  description: string;
  keywordsText: string;
  onSelect: () => void;
};

export function CommandPaletteDialog({
  entries,
  onClose,
  open,
}: {
  entries: CommandPaletteEntry[];
  onClose: () => void;
  open: boolean;
}) {
  const titleId = useId();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const pointerDownOutsideRef = useRef(false);
  const previousFocusRef = useRef<HTMLElement | null>(null);
  const [query, setQuery] = useState("");
  const [selectedIndex, setSelectedIndex] = useState(0);

  const filtered: CommandPaletteEntry[] = [];
  for (let i = 0; i < entries.length; i += 1) {
    const entry = entries[i];
    if (matchesKoreanQuery(`${entry.label} ${entry.group} ${entry.keywordsText}`, query)) {
      filtered.push(entry);
      if (filtered.length >= 16) {
        break;
      }
    }
  }

  const clampedIndex = filtered.length > 0 ? Math.min(selectedIndex, filtered.length - 1) : 0;

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) {
      return;
    }
    dialog.setAttribute("closedby", "any");
    if (open) {
      setQuery("");
      setSelectedIndex(0);
      if (!dialog.open) {
        previousFocusRef.current =
          document.activeElement instanceof HTMLElement ? document.activeElement : null;
        if (typeof dialog.showModal === "function") {
          dialog.showModal();
        } else {
          dialog.setAttribute("open", "");
        }
      }
      requestAnimationFrame(() => inputRef.current?.focus());
    } else if (dialog.open) {
      if (typeof dialog.close === "function") {
        dialog.close();
      } else {
        dialog.removeAttribute("open");
      }
      previousFocusRef.current?.focus();
    }
  }, [open]);

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      if (filtered.length > 0) {
        setSelectedIndex((prev) => (prev + 1) % filtered.length);
      }
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      if (filtered.length > 0) {
        setSelectedIndex((prev) => (prev - 1 + filtered.length) % filtered.length);
      }
    } else if (event.key === "Home") {
      event.preventDefault();
      setSelectedIndex(0);
    } else if (event.key === "End" && filtered.length > 0) {
      event.preventDefault();
      setSelectedIndex(filtered.length - 1);
    } else if (event.key === "Enter" && filtered[clampedIndex]) {
      event.preventDefault();
      const target = filtered[clampedIndex];
      onClose();
      target.onSelect();
    }
  }

  function isOutside(clientX: number, clientY: number): boolean {
    const rect = panelRef.current?.getBoundingClientRect();
    if (!rect) {
      return false;
    }
    return (
      clientX < rect.left ||
      clientX > rect.right ||
      clientY < rect.top ||
      clientY > rect.bottom
    );
  }

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      className="cmd-palette-dialog"
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onClose={() => {
        previousFocusRef.current?.focus();
      }}
      onPointerDown={(event) => {
        pointerDownOutsideRef.current =
          event.target === dialogRef.current && isOutside(event.clientX, event.clientY);
      }}
      onPointerUp={(event) => {
        const shouldClose =
          pointerDownOutsideRef.current &&
          event.target === dialogRef.current &&
          isOutside(event.clientX, event.clientY);
        pointerDownOutsideRef.current = false;
        if (shouldClose) {
          onClose();
        }
      }}
    >
      <div ref={panelRef} className="cmd-palette-panel">
        <div className="cmd-palette-input-row">
          <input
            ref={inputRef}
            aria-label="빠른 메뉴 및 계좌 검색"
            id={titleId}
            onChange={(event) => {
              setQuery(event.target.value);
              setSelectedIndex(0);
            }}
            onKeyDown={handleKeyDown}
            placeholder="메뉴, 계좌, 받는 사람 검색 (초성 ㅇㅊ 지원)"
            type="search"
            value={query}
          />
          <button onClick={onClose} type="button">
            닫기
          </button>
        </div>
        <div className="cmd-palette-list" role="listbox">
          {filtered.length === 0 ? (
            <EmptyState
              title="일치하는 항목이 없습니다."
              description="메뉴 이름이나 초성으로 다시 검색하세요."
            />
          ) : (
            filtered.map((entry, idx) => (
              <button
                aria-selected={idx === clampedIndex}
                className={`cmd-palette-item ${idx === clampedIndex ? "selected" : ""}`.trim()}
                key={entry.id}
                onClick={() => {
                  onClose();
                  entry.onSelect();
                }}
                role="option"
                type="button"
              >
                <div>
                  <strong>{entry.label}</strong>
                  <small> · {entry.description}</small>
                </div>
                <StatusBadge tone="muted">{entry.group}</StatusBadge>
              </button>
            ))
          )}
        </div>
      </div>
    </dialog>
  );
}
