/*
 * First-Party Financial Analytics Panels & Single-Pass SVG Charts
 */

import { useId, useState, type ReactNode } from "react";
import { formatDateTime, formatMinorAmount } from "@/lib/customer-banking/format";
import { getDirectionLabel, getTransactionCategoryLabel } from "@/lib/customer-banking/labels";
import { CategoryBadge } from "./category-badge";
import { ArrowDownLeftIcon, ArrowUpRightIcon } from "./icons";
import { type MetricTone, SegmentedRatioBar } from "./metric-primitives";

const MASKED_TEXT = "••••••";

const CHART_PALETTE: ReadonlyArray<{ stroke: string; tone: MetricTone }> = [
  { stroke: "var(--chart-1)", tone: "slate" },
  { stroke: "var(--chart-2)", tone: "blue" },
  { stroke: "var(--chart-3)", tone: "emerald" },
  { stroke: "var(--chart-4)", tone: "amber" },
  { stroke: "var(--chart-5)", tone: "indigo" },
];

export function OverviewKpiStrip({
  accountCount,
  activeSchedulesCount,
  balanceMasked = false,
  currencyEntries,
  primaryCurrency,
  totalInflowMinor,
  totalOutflowMinor,
  transactionCount,
}: {
  accountCount: number;
  activeSchedulesCount: number;
  balanceMasked?: boolean;
  currencyEntries: Array<[string, number]>;
  primaryCurrency: string;
  totalInflowMinor: number;
  totalOutflowMinor: number;
  transactionCount: number;
}) {
  let krwTotal = 0;
  let primaryFx: [string, number] | undefined;
  for (let i = 0; i < currencyEntries.length; i += 1) {
    const entry = currencyEntries[i];
    if (entry[0] === "KRW") {
      krwTotal = entry[1];
    } else if (!primaryFx) {
      primaryFx = entry;
    }
  }

  return (
    <div aria-label="핵심 자산 지표" className="ab-kpi-strip" role="group">
      <div className="ab-kpi-cell">
        <div className="ab-kpi-cell-head">
          <span>원화 보유자산</span>
          <span className="ab-kpi-tag tabular-nums">보유 {accountCount}계좌</span>
        </div>
        <strong className="ab-kpi-number tabular-nums font-mono">
          {balanceMasked ? MASKED_TEXT : formatMinorAmount(krwTotal, "KRW")}
        </strong>
        <small>출금 가능 원화 잔액 합계</small>
      </div>

      <div className="ab-kpi-cell">
        <div className="ab-kpi-cell-head">
          <span>외화·예약현황</span>
          <span className="ab-kpi-tag tabular-nums">예약 {activeSchedulesCount}건</span>
        </div>
        <strong className="ab-kpi-number tabular-nums font-mono">
          {balanceMasked
            ? MASKED_TEXT
            : primaryFx
              ? formatMinorAmount(primaryFx[1], primaryFx[0])
              : "₩0"}
        </strong>
        <small>{primaryFx ? `${primaryFx[0]} 외화 출금가능` : "외화 예치금 없음"}</small>
      </div>

      <div className="ab-kpi-cell">
        <div className="ab-kpi-cell-head">
          <span>최근 입금 합계</span>
          <span className="ab-kpi-tag positive">입금</span>
        </div>
        <strong className="ab-kpi-number positive tabular-nums font-mono">
          {balanceMasked
            ? MASKED_TEXT
            : `+${formatMinorAmount(totalInflowMinor, primaryCurrency)}`}
        </strong>
        <small className="tabular-nums">최근 {transactionCount}건 기준</small>
      </div>

      <div className="ab-kpi-cell">
        <div className="ab-kpi-cell-head">
          <span>최근 출금 합계</span>
          <span className="ab-kpi-tag negative">출금</span>
        </div>
        <strong className="ab-kpi-number negative tabular-nums font-mono">
          {balanceMasked
            ? MASKED_TEXT
            : `-${formatMinorAmount(totalOutflowMinor, primaryCurrency)}`}
        </strong>
        <small className="tabular-nums">최근 {transactionCount}건 기준</small>
      </div>
    </div>
  );
}

export function PortfolioDonutBox({
  accounts,
  activeSchedulesCount,
  balanceMasked = false,
  currencyEntries,
}: {
  accounts: Array<{
    accountId: number;
    displayName: string;
    availableBalanceMinor: number;
    currencyCode: string;
  }>;
  activeSchedulesCount: number;
  balanceMasked?: boolean;
  currencyEntries: Array<[string, number]>;
}) {
  const radius = 36;
  const circumference = 2 * Math.PI * radius;

  // Single pass computes weights and total weight without intermediate chain allocations.
  const weights = new Array<number>(accounts.length);
  const barColors = new Array<MetricTone>(accounts.length);
  let totalWeight = 0;
  for (let i = 0; i < accounts.length; i += 1) {
    const w = Math.max(1, Math.abs(accounts[i].availableBalanceMinor));
    weights[i] = w;
    totalWeight += w;
    barColors[i] = CHART_PALETTE[i % CHART_PALETTE.length].tone;
  }
  const safeTotal = totalWeight || 1;
  let accumulatedOffset = 0;

  return (
    <div className="ab-donut-box">
      <div className="ab-donut-visual">
        <svg
          aria-label="계좌별 자산 비중 차트"
          height="96"
          role="img"
          viewBox="0 0 96 96"
          width="96"
        >
          <circle
            cx="48"
            cy="48"
            fill="transparent"
            r={radius}
            stroke="var(--line)"
            strokeWidth="10"
          />
          {accounts.map((acc, idx) => {
            const segLength = (weights[idx] / safeTotal) * circumference;
            const dashOffset = -accumulatedOffset;
            accumulatedOffset += segLength;
            const color = CHART_PALETTE[idx % CHART_PALETTE.length].stroke;
            return (
              <circle
                cx="48"
                cy="48"
                fill="transparent"
                key={acc.accountId}
                r={radius}
                stroke={color}
                strokeDasharray={`${Math.max(4, segLength - 2)} ${circumference}`}
                strokeDashoffset={dashOffset}
                strokeLinecap="butt"
                strokeWidth="10"
                transform="rotate(-90 48 48)"
              >
                <title>
                  {balanceMasked
                    ? `${acc.displayName}: ${MASKED_TEXT}`
                    : `${acc.displayName}: ${formatMinorAmount(acc.availableBalanceMinor, acc.currencyCode)}`}
                </title>
              </circle>
            );
          })}
        </svg>
        <div className="ab-donut-center">
          <strong className="tabular-nums">{accounts.length}</strong>
          <span>계좌</span>
        </div>
      </div>

      <div className="ab-donut-meta">
        <div className="form-heading">
          <strong>통화별 보유 자산</strong>
          <span>
            {activeSchedulesCount > 0 ? `예약이체 ${activeSchedulesCount}건` : `${accounts.length}계좌`}
          </span>
        </div>
        {currencyEntries.length === 0 ? (
          <p className="field-note">계좌조회 후 통화별 잔액 합계가 표시됩니다.</p>
        ) : (
          <dl className="detail-list compact-list">
            {currencyEntries.map(([currencyCode, total]) => (
              <div key={currencyCode}>
                <dt>{currencyCode} 합계</dt>
                <dd className="font-mono tabular-nums">
                  {balanceMasked ? MASKED_TEXT : formatMinorAmount(total, currencyCode)}
                </dd>
              </div>
            ))}
          </dl>
        )}
        {accounts.length > 0 ? (
          <SegmentedRatioBar colors={barColors} label="계좌별 보유 비중" values={weights} />
        ) : null}
      </div>
    </div>
  );
}

const FLOW_SERIES_OPTIONS = [
  ["ALL", "전체"],
  ["CREDIT", "입금"],
  ["DEBIT", "출금"],
] as const;

export function CashflowAreaChart({
  balanceMasked = false,
  currencyCode,
  flowBars,
  inflowMinor,
  onViewAll,
  outflowMinor,
  transactionCount,
}: {
  balanceMasked?: boolean;
  currencyCode: string;
  flowBars: Array<{ ref: string; amount: number; direction: string }>;
  inflowMinor: number;
  onViewAll: () => void;
  outflowMinor: number;
  transactionCount: number;
}) {
  const [seriesMode, setSeriesMode] = useState<"ALL" | "CREDIT" | "DEBIT">("ALL");
  const gradientId = useId();
  const netMinor = inflowMinor - outflowMinor;

  // Backward single pass reverses chronological order, filters mode, and finds max scale at once.
  const series: Array<{ ref: string; amount: number; direction: string }> = [];
  let maxAmount = 1;
  for (let i = flowBars.length - 1; i >= 0; i -= 1) {
    const item = flowBars[i];
    if (seriesMode === "ALL" || item.direction === seriesMode) {
      series.push(item);
      if (item.amount > maxAmount) {
        maxAmount = item.amount;
      }
    }
  }

  const width = 520;
  const height = 148;
  const padX = 16;
  const padTop = 14;
  const padBottom = 22;
  const innerW = width - padX * 2;
  const innerH = height - padTop - padBottom;
  const baselineY = padTop + innerH;

  let linePath = "";
  let firstX = 0;
  let lastX = 0;
  const pointElements: ReactNode[] = [];
  const count = series.length;

  for (let idx = 0; idx < count; idx += 1) {
    const item = series[idx];
    const x =
      count <= 1 ? width / 2 : Math.round(padX + (idx / (count - 1)) * innerW);
    const y = Math.round(padTop + innerH - (item.amount / maxAmount) * innerH);
    if (idx === 0) {
      firstX = x;
      linePath = `M ${x} ${y}`;
    } else {
      linePath += ` L ${x} ${y}`;
    }
    lastX = x;
    pointElements.push(
      <g key={`${item.ref}-${idx}`}>
        <circle
          cx={x}
          cy={y}
          fill={item.direction === "CREDIT" ? "var(--credit)" : "var(--danger)"}
          r="4"
          stroke="var(--surface)"
          strokeWidth="1.5"
        >
          <title>
            {balanceMasked
              ? `${getDirectionLabel(item.direction)} ${MASKED_TEXT}`
              : `${getDirectionLabel(item.direction)} ${formatMinorAmount(item.amount, currencyCode)}`}
          </title>
        </circle>
        <text
          fill="var(--muted)"
          fontSize="10"
          textAnchor="middle"
          x={x}
          y={height - 4}
        >
          {item.ref.slice(-4)}
        </text>
      </g>,
    );
  }

  const areaPath =
    count > 1 ? `${linePath} L ${lastX} ${baselineY} L ${firstX} ${baselineY} Z` : "";

  return (
    <div className="ab-money-movement">
      <div className="form-heading">
        <div>
          <strong>입출금 추이</strong>
          <span className="ab-sub-meta">최근 {transactionCount}건 기준</span>
        </div>
        <div className="ab-seg-control" role="group" aria-label="입출금 구분 필터">
          {FLOW_SERIES_OPTIONS.map(([mode, label]) => (
            <button
              aria-pressed={seriesMode === mode}
              className={seriesMode === mode ? "active" : undefined}
              key={mode}
              onClick={() => setSeriesMode(mode)}
              type="button"
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {transactionCount === 0 ? (
        <div className="button-row compact">
          <span className="field-note">거래내역 조회 후 입출금 추이 그래프가 표시됩니다.</span>
          <button onClick={onViewAll} type="button">
            거래내역 보기
          </button>
        </div>
      ) : (
        <>
          <div className="ab-movement-metrics">
            <div>
              <span>총 입금</span>
              <strong className="tabular-nums font-mono positive">
                {balanceMasked ? MASKED_TEXT : formatMinorAmount(inflowMinor, currencyCode)}
              </strong>
            </div>
            <div>
              <span>총 출금</span>
              <strong className="tabular-nums font-mono negative">
                {balanceMasked ? MASKED_TEXT : formatMinorAmount(outflowMinor, currencyCode)}
              </strong>
            </div>
            <div>
              <span>순입출금</span>
              <strong
                className={`tabular-nums font-mono ${netMinor >= 0 ? "positive" : "negative"}`}
              >
                {balanceMasked
                  ? MASKED_TEXT
                  : `${netMinor >= 0 ? "+" : "-"}${formatMinorAmount(Math.abs(netMinor), currencyCode)}`}
              </strong>
            </div>
          </div>

          <svg
            aria-label="최근 입출금 추이 영역 차트"
            className="ab-movement-svg"
            role="img"
            viewBox={`0 0 ${width} ${height}`}
          >
            <defs>
              <linearGradient id={gradientId} x1="0" x2="0" y1="0" y2="1">
                <stop offset="5%" stopColor="var(--primary)" stopOpacity="0.24" />
                <stop offset="95%" stopColor="var(--primary)" stopOpacity="0.02" />
              </linearGradient>
            </defs>
            <line
              stroke="var(--line)"
              strokeDasharray="3 3"
              x1={padX}
              x2={width - padX}
              y1={padTop + Math.round(innerH * 0.2)}
              y2={padTop + Math.round(innerH * 0.2)}
            />
            <line
              stroke="var(--line)"
              strokeDasharray="3 3"
              x1={padX}
              x2={width - padX}
              y1={padTop + Math.round(innerH * 0.55)}
              y2={padTop + Math.round(innerH * 0.55)}
            />
            <line
              stroke="var(--line)"
              x1={padX}
              x2={width - padX}
              y1={baselineY}
              y2={baselineY}
            />
            {count > 1 ? (
              <>
                <path d={areaPath} fill={`url(#${gradientId})`} />
                <path
                  d={linePath}
                  fill="none"
                  stroke="var(--primary)"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth="2.2"
                />
              </>
            ) : null}
            {pointElements}
          </svg>
        </>
      )}
    </div>
  );
}

export function RecentTransactionsFeed({
  annotationsMap,
  balanceMasked = false,
  items,
  onSelect,
  onViewAll,
}: {
  annotationsMap?: ReadonlyMap<string, { category?: string }>;
  balanceMasked?: boolean;
  items: Array<{
    transactionReference: string;
    bookedAt: string;
    counterpartyMaskedName?: string | null;
    summary?: string | null;
    direction: string;
    amountMinor: number;
    currencyCode: string;
  }>;
  onSelect: () => void;
  onViewAll: () => void;
}) {
  return (
    <div className="ab-recent-feed">
      <div className="form-heading">
        <div>
          <strong>최근 거래 내역</strong>
          <span className="ab-sub-meta">최근 {items.length}건</span>
        </div>
        <button onClick={onViewAll} type="button">
          전체보기
        </button>
      </div>
      {items.length === 0 ? (
        <p className="field-note">최근 조회된 거래가 없습니다.</p>
      ) : (
        <ul className="ab-recent-sales-list" role="list">
          {items.map((tx) => {
            const cat = annotationsMap?.get(tx.transactionReference)?.category;
            const isCredit = tx.direction === "CREDIT";
            return (
              <li key={tx.transactionReference}>
                <button onClick={onSelect} type="button">
                  <span
                    aria-hidden="true"
                    className={`ab-sales-avatar ${isCredit ? "credit" : "debit"}`}
                  >
                    {isCredit ? (
                      <ArrowDownLeftIcon size={15} />
                    ) : (
                      <ArrowUpRightIcon size={15} />
                    )}
                  </span>
                  <div className="ab-sales-meta">
                    <strong>
                      {tx.counterpartyMaskedName ||
                        tx.summary ||
                        getDirectionLabel(tx.direction)}
                    </strong>
                    <small className="tabular-nums">
                      {formatDateTime(tx.bookedAt)} · {tx.transactionReference}
                    </small>
                  </div>
                  <div className="ab-sales-right">
                    {cat ? <CategoryBadge category={cat} /> : null}
                    <strong
                      className={`tabular-nums font-mono ${isCredit ? "credit" : "debit"}`}
                    >
                      {balanceMasked
                        ? MASKED_TEXT
                        : `${isCredit ? "+" : "-"}${formatMinorAmount(tx.amountMinor, tx.currencyCode)}`}
                    </strong>
                  </div>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

export function CategorySpendingDonut({
  balanceMasked = false,
  categories,
  currencyCode,
}: {
  balanceMasked?: boolean;
  categories: Array<{ category: string; amountMinor: number; count: number }>;
  currencyCode: string;
}) {
  let total = 0;
  for (let i = 0; i < categories.length; i += 1) {
    total += categories[i].amountMinor;
  }

  if (categories.length === 0 || total <= 0) {
    return null;
  }

  const radius = 34;
  const circumference = 2 * Math.PI * radius;
  let offset = 0;
  const legendLimit = Math.min(4, categories.length);
  const legendItems: ReactNode[] = [];
  for (let idx = 0; idx < legendLimit; idx += 1) {
    const item = categories[idx];
    legendItems.push(
      <li key={item.category}>
        <span
          className="ab-legend-dot"
          style={{ background: CHART_PALETTE[idx % CHART_PALETTE.length].stroke }}
        />
        <span className="ab-legend-name">{getTransactionCategoryLabel(item.category)}</span>
        <strong className="tabular-nums font-mono">
          {balanceMasked ? MASKED_TEXT : formatMinorAmount(item.amountMinor, currencyCode)}
        </strong>
      </li>,
    );
  }

  return (
    <div className="ab-category-panel">
      <div className="form-heading">
        <strong>분류별 지출</strong>
        <span>{categories.length}개 항목</span>
      </div>
      <div className="ab-category-donut-card">
        <div className="ab-donut-visual small">
          <svg
            aria-label="분류별 지출 요약 차트"
            height="92"
            role="img"
            viewBox="0 0 92 92"
            width="92"
          >
            <circle
              cx="46"
              cy="46"
              fill="transparent"
              r={radius}
              stroke="var(--line)"
              strokeWidth="10"
            />
            {categories.map((item, idx) => {
              const seg = (item.amountMinor / total) * circumference;
              const dashOffset = -offset;
              offset += seg;
              return (
                <circle
                  cx="46"
                  cy="46"
                  fill="transparent"
                  key={item.category}
                  r={radius}
                  stroke={CHART_PALETTE[idx % CHART_PALETTE.length].stroke}
                  strokeDasharray={`${Math.max(3, seg - 1.5)} ${circumference}`}
                  strokeDashoffset={dashOffset}
                  strokeWidth="10"
                  transform="rotate(-90 46 46)"
                />
              );
            })}
          </svg>
          <div className="ab-donut-center">
            <strong className="tabular-nums">{categories.length}</strong>
            <span>분류</span>
          </div>
        </div>

        <ul className="ab-category-legend" role="list">
          {legendItems}
        </ul>
      </div>
    </div>
  );
}
