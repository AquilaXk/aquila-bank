/*
 * First-Party Metric Visualization Primitives (Single-pass SVG & CSS components)
 */

import type { ReactNode } from "react";

export type MetricTone = "blue" | "emerald" | "amber" | "rose" | "slate" | "indigo";

const TONE_CSS_VAR: Record<MetricTone, string> = {
  blue: "var(--primary)",
  emerald: "var(--success)",
  amber: "var(--warning)",
  rose: "var(--danger)",
  slate: "var(--muted)",
  indigo: "#4f46e5",
};

export function SegmentedRatioBar({
  colors = ["blue", "emerald", "amber", "indigo", "slate"],
  label = "비율 막대",
  markerValue,
  values,
}: {
  colors?: MetricTone[];
  label?: string;
  markerValue?: number;
  values: number[];
}) {
  let total = 0;
  for (let i = 0; i < values.length; i += 1) {
    if (values[i] > 0) {
      total += values[i];
    }
  }
  const safeTotal = total > 0 ? total : 1;
  const clampedMarker =
    typeof markerValue === "number"
      ? Math.max(0, Math.min(100, (markerValue / safeTotal) * 100))
      : undefined;

  const segments: ReactNode[] = [];
  for (let idx = 0; idx < values.length; idx += 1) {
    const val = values[idx];
    if (val <= 0) {
      continue;
    }
    const pct = (val / safeTotal) * 100;
    const colorKey = colors[idx % colors.length];
    segments.push(
      <div
        className="ab-category-bar-seg"
        key={`${idx}-${colorKey}`}
        style={{
          background: TONE_CSS_VAR[colorKey],
          width: `${Math.max(2, pct)}%`,
        }}
      />,
    );
  }

  return (
    <div aria-label={label} className="ab-category-bar" role="group">
      <div className="ab-category-bar-track">{segments}</div>
      {typeof clampedMarker === "number" ? (
        <div
          aria-hidden="true"
          className="ab-category-bar-marker"
          style={{ left: `${clampedMarker}%` }}
        />
      ) : null}
    </div>
  );
}

export function RadialGauge({
  children,
  label = "진행률",
  max = 100,
  radius = 30,
  strokeWidth = 6,
  value = 0,
  variant = "blue",
}: {
  children?: ReactNode;
  label?: string;
  max?: number;
  radius?: number;
  strokeWidth?: number;
  value?: number;
  variant?: MetricTone;
}) {
  const safeMax = max > 0 ? max : 100;
  const safeValue = Math.min(safeMax, Math.max(0, value));
  const normalizedRadius = radius - strokeWidth / 2;
  const circumference = normalizedRadius * 2 * Math.PI;
  const offset = circumference - (safeValue / safeMax) * circumference;
  const size = radius * 2;

  return (
    <div
      aria-label={label}
      aria-valuemax={safeMax}
      aria-valuemin={0}
      aria-valuenow={Math.round(safeValue)}
      className="ab-progress-circle"
      role="progressbar"
      style={{ height: size, width: size }}
    >
      <svg
        aria-hidden="true"
        className="ab-progress-circle-svg"
        focusable="false"
        height={size}
        style={{ pointerEvents: "none" }}
        viewBox={`0 0 ${size} ${size}`}
        width={size}
      >
        <circle
          className="ab-progress-circle-bg"
          cx={radius}
          cy={radius}
          fill="transparent"
          r={normalizedRadius}
          strokeWidth={strokeWidth}
        />
        <circle
          className="ab-progress-circle-fg"
          cx={radius}
          cy={radius}
          fill="transparent"
          r={normalizedRadius}
          stroke={TONE_CSS_VAR[variant]}
          strokeDasharray={`${circumference} ${circumference}`}
          strokeDashoffset={offset}
          strokeLinecap="round"
          strokeWidth={strokeWidth}
        />
      </svg>
      <div className="ab-progress-circle-center">{children}</div>
    </div>
  );
}

export type RankedMetricItem = {
  key?: string;
  name: string;
  value: number;
  formattedValue?: string;
  color?: MetricTone;
};

export function RankedMetricList({ data }: { data: RankedMetricItem[] }) {
  let maxVal = 0;
  for (let i = 0; i < data.length; i += 1) {
    if (data[i].value > maxVal) {
      maxVal = data[i].value;
    }
  }
  const safeMax = maxVal > 0 ? maxVal : 1;

  return (
    <div className="ab-bar-list">
      {data.map((item, idx) => {
        const pct = item.value <= 0 ? 0 : Math.max(4, Math.round((item.value / safeMax) * 100));
        return (
          <div className="ab-bar-list-row" key={item.key ?? `${item.name}-${idx}`}>
            <div className="ab-bar-list-bar-wrap">
              <div
                className="ab-bar-list-bar"
                style={{
                  width: `${pct}%`,
                  background: item.color ? TONE_CSS_VAR[item.color] : undefined,
                }}
              />
              <span className="ab-bar-list-name">{item.name}</span>
            </div>
            <strong className="ab-bar-list-val tabular-nums font-mono">
              {item.formattedValue ?? String(item.value)}
            </strong>
          </div>
        );
      })}
    </div>
  );
}

export function StatusBlockStrip({
  items,
  label = "상태 트래커",
}: {
  items: Array<{ key: string; tone: "success" | "warn" | "danger" | "muted"; title: string }>;
  label?: string;
}) {
  return (
    <div aria-label={label} className="ab-tracker" role="group">
      {items.map((item) => (
        <span
          className={`ab-tracker-block tone-${item.tone}`}
          key={item.key}
          title={item.title}
        />
      ))}
    </div>
  );
}
