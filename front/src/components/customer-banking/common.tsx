import type { ReactNode } from "react";

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

export function BankTable({
  caption,
  children,
}: {
  caption: string;
  children: ReactNode;
}) {
  return (
    <div className="bank-table-wrap">
      <table className="bank-table">
        <caption>{caption}</caption>
        {children}
      </table>
    </div>
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
}: {
  active: string;
  items: Array<{
    id: string;
    label: string;
    onClick: () => void;
    disabled?: boolean;
  }>;
}) {
  return (
    <div className="work-tabs">
      {items.map((item) => (
        <button
          aria-current={active === item.id ? "page" : undefined}
          className={active === item.id ? "active" : undefined}
          disabled={item.disabled}
          key={item.id}
          onClick={item.onClick}
          type="button"
        >
          {item.label}
        </button>
      ))}
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
