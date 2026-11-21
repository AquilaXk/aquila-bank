/*
 * Open-Source Fintech Charts & Analytics Cards (Zero-dependency SVG adaptation)
 *
 * 참조 구현:
 * - adrianhajdin/banking (MIT License, Copyright (c) 2024 Adrian Hajdin - JS Mastery)
 *   components/TotalBalanceBox.tsx, components/DoughnutChart.tsx
 * - abderrahimghazali/shadcn-fintech (MIT License, Copyright (c) 2026 Abderrahim Ghazali)
 *   src/components/dashboard/financial-overview.tsx,
 *   src/components/dashboard/money-movement.tsx,
 *   src/components/analytics/category-donut.tsx
 */

import { formatMinorAmount } from "@/lib/customer-banking/format";
import { getDirectionLabel, getTransactionCategoryLabel } from "@/lib/customer-banking/labels";
import { ArrowDownLeftIcon, ArrowUpRightIcon } from "./icons";
import { CategoryBar, type TremorColor } from "./tremor-primitives";

const MASKED_TEXT = "••••••";
const RING_PALETTE: Array<{ stroke: string; tremor: TremorColor }> = [
  { stroke: "#0179fe", tremor: "blue" },
  { stroke: "#039855", tremor: "emerald" },
  { stroke: "#4f46e5", tremor: "indigo" },
  { stroke: "#d97706", tremor: "amber" },
  { stroke: "#667085", tremor: "slate" },
];

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
  const radius = 38;
  const circumference = 2 * Math.PI * radius;
  const weights = accounts.map((acc) => Math.max(1, Math.abs(acc.availableBalanceMinor)));
  const totalWeight = weights.reduce((acc, w) => acc + w, 0) || 1;
  let accumulatedOffset = 0;

  return (
    <section aria-label="총 보유자산 도넛 차트" className="oss-donut-box">
      <div className="oss-donut-visual">
        <svg
          aria-label="계좌별 자산 비중 차트"
          height="104"
          role="img"
          viewBox="0 0 104 104"
          width="104"
        >
          <circle
            cx="52"
            cy="52"
            fill="transparent"
            r={radius}
            stroke="var(--line)"
            strokeWidth="12"
          />
          {accounts.map((acc, idx) => {
            const segLength = (weights[idx] / totalWeight) * circumference;
            const dashOffset = -accumulatedOffset;
            accumulatedOffset += segLength;
            const color = RING_PALETTE[idx % RING_PALETTE.length].stroke;
            return (
              <circle
                cx="52"
                cy="52"
                fill="transparent"
                key={acc.accountId}
                r={radius}
                stroke={color}
                strokeDasharray={`${Math.max(4, segLength - 2)} ${circumference}`}
                strokeDashoffset={dashOffset}
                strokeLinecap="butt"
                strokeWidth="12"
                transform="rotate(-90 52 52)"
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
  const netMinor = inflowMinor - outflowMinor;
  const maxAmount = flowBars.reduce((max, b) => Math.max(max, b.amount), 1);

  return (
    <section aria-label="입출금 추이" className="oss-money-movement">
      <div className="form-heading">
        <strong>입출금 추이</strong>
        <span>최근 {transactionCount}건 기준</span>
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
          <div className="oss-kpi-duo">
            <div className="oss-kpi-pill income">
              <span className="oss-kpi-icon">
                <ArrowDownLeftIcon size={16} />
              </span>
              <div>
                <small>총 입금</small>
                <strong className="tabular-nums font-mono">
                  {balanceMasked ? MASKED_TEXT : formatMinorAmount(inflowMinor, currencyCode)}
                </strong>
              </div>
            </div>
            <div className="oss-kpi-pill expense">
              <span className="oss-kpi-icon">
                <ArrowUpRightIcon size={16} />
              </span>
              <div>
                <small>총 출금</small>
                <strong className="tabular-nums font-mono">
                  {balanceMasked ? MASKED_TEXT : formatMinorAmount(outflowMinor, currencyCode)}
                </strong>
              </div>
            </div>
          </div>

          <div className="oss-net-strip">
            <span>순입출금</span>
            <strong
              className={`tabular-nums font-mono ${netMinor >= 0 ? "positive" : "negative"}`}
            >
              {balanceMasked
                ? MASKED_TEXT
                : `${netMinor >= 0 ? "+" : "-"}${formatMinorAmount(Math.abs(netMinor), currencyCode)}`}
            </strong>
          </div>

          <svg
            aria-label="최근 입출금 추이 막대"
            className="oss-movement-svg"
            role="img"
            viewBox="0 0 320 112"
          >
            <line stroke="var(--line)" strokeDasharray="3 3" x1="0" x2="320" y1="22" y2="22" />
            <line stroke="var(--line)" strokeDasharray="3 3" x1="0" x2="320" y1="56" y2="56" />
            <line stroke="var(--line)" strokeDasharray="3 3" x1="0" x2="320" y1="90" y2="90" />
            {flowBars.map((bar, idx) => {
              const height = Math.max(10, Math.round((bar.amount / maxAmount) * 82));
              const stepWidth = 320 / Math.max(6, flowBars.length);
              const barWidth = Math.min(20, Math.max(8, Math.floor(stepWidth * 0.56)));
              const x = Math.round(idx * stepWidth + (stepWidth - barWidth) / 2);
              const isCredit = bar.direction === "CREDIT";
              return (
                <rect
                  fill={isCredit ? "var(--success)" : "var(--primary)"}
                  height={height}
                  key={`${bar.ref}-${idx}`}
                  opacity={isCredit ? 0.92 : 0.88}
                  rx="4"
                  width={barWidth}
                  x={x}
                  y={96 - height}
                >
                  <title>
                    {balanceMasked
                      ? `${getDirectionLabel(bar.direction)} ${MASKED_TEXT}`
                      : `${getDirectionLabel(bar.direction)} ${formatMinorAmount(bar.amount, currencyCode)}`}
                  </title>
                </rect>
              );
            })}
          </svg>
        </>
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
  );
}
