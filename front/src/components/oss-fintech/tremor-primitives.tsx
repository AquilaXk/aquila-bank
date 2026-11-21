/*
 * Tremor Data Visualization Primitives (Zero-dependency React/CSS adaptation)
 *
 * 참조 구현: tremorlabs/tremor (Apache License 2.0, Copyright (c) Tremor Labs)
 * - src/components/CategoryBar/CategoryBar.tsx
 * - src/components/BarList/BarList.tsx
 * - src/components/ProgressCircle/ProgressCircle.tsx
 * - src/components/Tracker/Tracker.tsx
 * https://github.com/tremorlabs/tremor
 */

import type { ReactNode } from "react";

export type TremorColor = "blue" | "emerald" | "amber" | "rose" | "slate" | "indigo";

const SEGMENT_COLOR_VAR: Record<TremorColor, string> = {
  blue: "var(--primary)",
  emerald: "var(--success)",
  amber: "var(--warning)",
  rose: "var(--danger)",
  slate: "var(--muted)",
  indigo: "#4f46e5",
};

function sumValues(values: ReadonlyArray<number>): number {
  let sum = 0;
  for (let i = 0; i < values.length; i += 1) {
    if (values[i] > 0) {
      sum += values[i];
    }
  }
  return sum;
}

export function CategoryBar({
  colors = ["blue", "emerald", "amber", "indigo", "slate"],
  label = "비율 막대",
  markerValue,
  values,
}: {
  colors?: TremorColor[];
  label?: string;
  markerValue?: number;
  values: number[];
}) {
  const total = sumValues(values);
  const safeTotal = total > 0 ? total : 1;
  const clampedMarker =
    typeof markerValue === "number"
      ? Math.max(0, Math.min(100, (markerValue / safeTotal) * 100))
      : undefined;

  return (
    <div
      aria-label={label}
      className="oss-category-bar"
      role="group"
    >
      <div className="oss-category-bar-track">
        {values.map((rawValue, idx) => {
          const val = Math.max(0, rawValue);
          if (val === 0) {
            return null;
          }
          const pct = (val / safeTotal) * 100;
          const colorKey = colors[idx % colors.length];
          return (
            <div
              className="oss-category-bar-seg"
              key={`${idx}-${colorKey}`}
              style={{
                background: SEGMENT_COLOR_VAR[colorKey],
                width: `${Math.max(2, pct)}%`,
              }}
            />
          );
        })}
      </div>
      {typeof clampedMarker === "number" ? (
        <div
          aria-hidden="true"
          className="oss-category-bar-marker"
          style={{ left: `${clampedMarker}%` }}
        />
      ) : null}
    </div>
  );
}

export function ProgressCircle({
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
  variant?: TremorColor;
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
      className="oss-progress-circle"
      role="progressbar"
      style={{ height: size, width: size }}
    >
      <svg
        aria-hidden="true"
        className="oss-progress-circle-svg"
        focusable="false"
        height={size}
        viewBox={`0 0 ${size} ${size}`}
        width={size}
      >
        <circle
          className="oss-progress-circle-bg"
          cx={radius}
          cy={radius}
          fill="transparent"
          r={normalizedRadius}
          strokeWidth={strokeWidth}
        />
        <circle
          className="oss-progress-circle-fg"
          cx={radius}
          cy={radius}
          fill="transparent"
          r={normalizedRadius}
          stroke={SEGMENT_COLOR_VAR[variant]}
          strokeDasharray={`${circumference} ${circumference}`}
          strokeDashoffset={offset}
          strokeLinecap="round"
          strokeWidth={strokeWidth}
        />
      </svg>
      <div className="oss-progress-circle-center">{children}</div>
    </div>
  );
}

export type BarListItem = {
  key?: string;
  name: string;
  value: number;
  formattedValue?: string;
  color?: TremorColor;
};

export function BarList({
  data,
}: {
  data: BarListItem[];
}) {
  let maxVal = 0;
  for (let i = 0; i < data.length; i += 1) {
    if (data[i].value > maxVal) {
      maxVal = data[i].value;
    }
  }
  const safeMax = maxVal > 0 ? maxVal : 1;

  return (
    <div className="oss-bar-list">
      {data.map((item, idx) => {
        const pct = item.value <= 0 ? 0 : Math.max(4, Math.round((item.value / safeMax) * 100));
        return (
          <div className="oss-bar-list-row" key={item.key ?? `${item.name}-${idx}`}>
            <div className="oss-bar-list-bar-wrap">
              <div
                className="oss-bar-list-bar"
                style={{
                  width: `${pct}%`,
                  background: item.color ? SEGMENT_COLOR_VAR[item.color] : undefined,
                }}
              />
              <span className="oss-bar-list-name">{item.name}</span>
            </div>
            <strong className="oss-bar-list-val tabular-nums font-mono">
              {item.formattedValue ?? String(item.value)}
            </strong>
          </div>
        );
      })}
    </div>
  );
}

export function ActivityTracker({
  items,
  label = "상태 트래커",
}: {
  items: Array<{ key: string; tone: "success" | "warn" | "danger" | "muted"; title: string }>;
  label?: string;
}) {
  return (
    <div aria-label={label} className="oss-tracker" role="group">
      {items.map((item) => (
        <span
          className={`oss-tracker-block tone-${item.tone}`}
          key={item.key}
          title={item.title}
        />
      ))}
    </div>
  );
}
