/*
 * shadcn/ui Dashboard-01 & Finance Charts (Zero-dependency SVG adaptation)
 *
 * 참조 구현:
 * - shadcn-ui/ui (MIT License, Copyright (c) 2023 shadcn) — dashboard-01 section-cards & chart-area-interactive
 * - satnaing/shadcn-admin (MIT License, Copyright (c) 2024 Sat Naing) — dashboard recent-sales list
 * - arhamkhnz/next-shadcn-admin-dashboard (MIT License, Copyright (c) 2025 Arham Khan) — finance overview-kpis & cash-flow
 * - adrianhajdin/banking (MIT License, Copyright (c) 2024 Adrian Hajdin - JS Mastery)
 */

import { useState } from "react";
import { formatDateTime, formatMinorAmount } from "@/lib/customer-banking/format";
import { getDirectionLabel, getTransactionCategoryLabel } from "@/lib/customer-banking/labels";
import { HorizonCategoryBadge } from "./category-badge";
import { ArrowDownLeftIcon, ArrowUpRightIcon } from "./icons";
import { CategoryBar, type TremorColor } from "./tremor-primitives";

const MASKED_TEXT = "••••••";
const RING_PALETTE: Array<{ stroke: string; tremor: TremorColor }> = [
  { stroke: "var(--chart-1)", tremor: "slate" },
  { stroke: "var(--chart-2)", tremor: "blue" },
  { stroke: "var(--chart-3)", tremor: "emerald" },
  { stroke: "var(--chart-4)", tremor: "amber" },
  { stroke: "var(--chart-5)", tremor: "indigo" },
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
  const krwTotal = currencyEntries.find(([code]) => code === "KRW")?.[1] ?? 0;
  const fxEntries = currencyEntries.filter(([code]) => code !== "KRW");
  const primaryFx = fxEntries[0];

  return (
    <section aria-label="핵심 자산 지표" className="oss-kpi-strip">
      <div className="oss-kpi-cell">
        <div className="oss-kpi-cell-head">
          <span>원화 보유자산</span>
          <span className="oss-kpi-tag tabular-nums">보유 {accountCount}계좌</span>
        </div>
        <strong className="oss-kpi-number tabular-nums font-mono">
          {balanceMasked ? MASKED_TEXT : formatMinorAmount(krwTotal, "KRW")}
        </strong>
        <small>출금 가능 원화 잔액 합계</small>
      </div>

      <div className="oss-kpi-cell">
        <div className="oss-kpi-cell-head">
          <span>외화·예약현황</span>
          <span className="oss-kpi-tag tabular-nums">예약 {activeSchedulesCount}건</span>
        </div>
        <strong className="oss-kpi-number tabular-nums font-mono">
          {balanceMasked
            ? MASKED_TEXT
            : primaryFx
              ? formatMinorAmount(primaryFx[1], primaryFx[0])
              : "₩0"}
        </strong>
        <small>{primaryFx ? `${primaryFx[0]} 외화 출금가능` : "외화 예치금 없음"}</small>
      </div>

      <div className="oss-kpi-cell">
        <div className="oss-kpi-cell-head">
          <span>최근 입금 합계</span>
          <span className="oss-kpi-tag positive">입금</span>
        </div>
        <strong className="oss-kpi-number positive tabular-nums font-mono">
          {balanceMasked
            ? MASKED_TEXT
            : `+${formatMinorAmount(totalInflowMinor, primaryCurrency)}`}
        </strong>
        <small className="tabular-nums">최근 {transactionCount}건 기준</small>
      </div>

      <div className="oss-kpi-cell">
        <div className="oss-kpi-cell-head">
          <span>최근 출금 합계</span>
          <span className="oss-kpi-tag negative">출금</span>
        </div>
        <strong className="oss-kpi-number negative tabular-nums font-mono">
          {balanceMasked
            ? MASKED_TEXT
            : `-${formatMinorAmount(totalOutflowMinor, primaryCurrency)}`}
        </strong>
        <small className="tabular-nums">최근 {transactionCount}건 기준</small>
      </div>
    </section>
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
  const weights = accounts.map((acc) => Math.max(1, Math.abs(acc.availableBalanceMinor)));
  const totalWeight = weights.reduce((acc, w) => acc + w, 0) || 1;
  let accumulatedOffset = 0;

  return (
    <section aria-label="총 보유자산 도넛 차트" className="oss-donut-box">
      <div className="oss-donut-visual">
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
            const segLength = (weights[idx] / totalWeight) * circumference;
            const dashOffset = -accumulatedOffset;
            accumulatedOffset += segLength;
            const color = RING_PALETTE[idx % RING_PALETTE.length].stroke;
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
        <div className="oss-donut-center">
          <strong className="tabular-nums">{accounts.length}</strong>
          <span>계좌</span>
        </div>
      </div>

      <div className="oss-donut-meta">
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
          <CategoryBar
            colors={accounts.map((_, idx) => RING_PALETTE[idx % RING_PALETTE.length].tremor)}
            label="계좌별 보유 비중"
            values={weights}
          />
        ) : null}
      </div>
    </section>
  );
}

export function MoneyMovementChart({
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
  const netMinor = inflowMinor - outflowMinor;
  const visibleBars =
    seriesMode === "ALL"
      ? flowBars
      : flowBars.filter((item) => item.direction === seriesMode);
  const series = visibleBars.slice().reverse();
  const maxAmount = series.reduce((max, item) => Math.max(max, item.amount), 1);

  const width = 520;
  const height = 148;
  const padX = 16;
  const padTop = 14;
  const padBottom = 22;
  const innerW = width - padX * 2;
  const innerH = height - padTop - padBottom;

  const points = series.map((item, idx) => {
    const x =
      series.length <= 1
        ? width / 2
        : Math.round(padX + (idx / (series.length - 1)) * innerW);
    const y = Math.round(padTop + innerH - (item.amount / maxAmount) * innerH);
    return { ...item, x, y };
  });

  const linePath = points
    .map((pt, idx) => `${idx === 0 ? "M" : "L"} ${pt.x} ${pt.y}`)
    .join(" ");
  const areaPath =
    points.length > 0
      ? `${linePath} L ${points[points.length - 1].x} ${padTop + innerH} L ${points[0].x} ${padTop + innerH} Z`
      : "";

  return (
    <section aria-label="입출금 추이" className="oss-money-movement">
      <div className="form-heading">
        <div>
          <strong>입출금 추이</strong>
          <span className="oss-sub-meta">최근 {transactionCount}건 기준</span>
        </div>
        <div className="oss-seg-control" role="group" aria-label="입출금 구분 필터">
          {(
            [
              ["ALL", "전체"],
              ["CREDIT", "입금"],
              ["DEBIT", "출금"],
            ] as const
          ).map(([mode, label]) => (
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
          <div className="oss-movement-metrics">
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
            className="oss-movement-svg"
            role="img"
            viewBox={`0 0 ${width} ${height}`}
          >
            <defs>
              <linearGradient id="shadcnAreaFill" x1="0" x2="0" y1="0" y2="1">
                <stop offset="5%" stopColor="var(--accent)" stopOpacity="0.28" />
                <stop offset="95%" stopColor="var(--accent)" stopOpacity="0.02" />
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
              y1={padTop + innerH}
              y2={padTop + innerH}
            />
            {points.length > 1 ? (
              <>
                <path d={areaPath} fill="url(#shadcnAreaFill)" />
                <path
                  d={linePath}
                  fill="none"
                  stroke="var(--accent)"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth="2.2"
                />
              </>
            ) : null}
            {points.map((pt, idx) => (
              <g key={`${pt.ref}-${idx}`}>
                <circle
                  cx={pt.x}
                  cy={pt.y}
                  fill={pt.direction === "CREDIT" ? "var(--success)" : "var(--accent)"}
                  r="4"
                  stroke="var(--surface)"
                  strokeWidth="1.5"
                >
                  <title>
                    {balanceMasked
                      ? `${getDirectionLabel(pt.direction)} ${MASKED_TEXT}`
                      : `${getDirectionLabel(pt.direction)} ${formatMinorAmount(pt.amount, currencyCode)}`}
                  </title>
                </circle>
                <text
                  fill="var(--muted)"
                  fontSize="10"
                  textAnchor="middle"
                  x={pt.x}
                  y={height - 4}
                >
                  {pt.ref.slice(-4)}
                </text>
              </g>
            ))}
          </svg>
        </>
      )}
    </section>
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
    <section aria-label="최근 거래 요약" className="oss-recent-feed">
      <div className="form-heading">
        <div>
          <strong>최근 거래 내역</strong>
          <span className="oss-sub-meta">최근 {items.length}건</span>
        </div>
        <button onClick={onViewAll} type="button">
          전체보기
        </button>
      </div>
      {items.length === 0 ? (
        <p className="field-note">최근 조회된 거래가 없습니다.</p>
      ) : (
        <ul className="oss-recent-sales-list">
          {items.map((tx) => {
            const cat = annotationsMap?.get(tx.transactionReference)?.category;
            const isCredit = tx.direction === "CREDIT";
            return (
              <li key={tx.transactionReference}>
                <button onClick={onSelect} type="button">
                  <span
                    aria-hidden="true"
                    className={`oss-sales-avatar ${isCredit ? "credit" : "debit"}`}
                  >
                    {isCredit ? (
                      <ArrowDownLeftIcon size={15} />
                    ) : (
                      <ArrowUpRightIcon size={15} />
                    )}
                  </span>
                  <div className="oss-sales-meta">
                    <strong>
                      {tx.counterpartyMaskedName ||
                        tx.summary ||
                        getDirectionLabel(tx.direction)}
                    </strong>
                    <small className="tabular-nums">
                      {formatDateTime(tx.bookedAt)} · {tx.transactionReference}
                    </small>
                  </div>
                  <div className="oss-sales-right">
                    {cat ? <HorizonCategoryBadge category={cat} /> : null}
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
    </section>
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
  const total = categories.reduce((acc, item) => acc + item.amountMinor, 0);
  const radius = 34;
  const circumference = 2 * Math.PI * radius;
  let offset = 0;

  if (categories.length === 0 || total <= 0) {
    return null;
  }

  return (
    <section aria-label="분류별 지출 현황" className="oss-category-panel">
      <div className="form-heading">
        <strong>분류별 지출</strong>
        <span>{categories.length}개 항목</span>
      </div>
      <div className="oss-category-donut-card">
        <div className="oss-donut-visual small">
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
                  stroke={RING_PALETTE[idx % RING_PALETTE.length].stroke}
                  strokeDasharray={`${Math.max(3, seg - 1.5)} ${circumference}`}
                  strokeDashoffset={dashOffset}
                  strokeWidth="10"
                  transform="rotate(-90 46 46)"
                />
              );
            })}
          </svg>
          <div className="oss-donut-center">
            <strong className="tabular-nums">{categories.length}</strong>
            <span>분류</span>
          </div>
        </div>

        <ul className="oss-category-legend">
          {categories.slice(0, 4).map((item, idx) => (
            <li key={item.category}>
              <span
                className="oss-legend-dot"
                style={{ background: RING_PALETTE[idx % RING_PALETTE.length].stroke }}
              />
              <span className="oss-legend-name">{getTransactionCategoryLabel(item.category)}</span>
              <strong className="tabular-nums font-mono">
                {balanceMasked ? MASKED_TEXT : formatMinorAmount(item.amountMinor, currencyCode)}
              </strong>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
