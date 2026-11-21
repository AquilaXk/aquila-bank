import type { TransactionAnnotationItem, TransactionItem } from "@/lib/api/types";
import { formatDateTime, formatMinorAmount } from "./format";
import {
  getDirectionLabel,
  getTransactionStatusLabel,
} from "./labels";

export const MAX_CSV_EXPORT_ROWS = 5000;

export type CsvTransactionAnnotationLookup = ReadonlyMap<string, TransactionAnnotationItem>;

function sanitizeFormulaCell(raw: string): string {
  if (!raw) {
    return "";
  }
  const first = raw.charCodeAt(0);
  const trimmed = raw.trimStart();
  const trimmedFirst = trimmed.length > 0 ? trimmed.charCodeAt(0) : 0;
  if (
    first === 61 || // '='
    first === 43 || // '+'
    first === 45 || // '-'
    first === 64 || // '@'
    first === 9 || // '\t'
    first === 13 || // '\r'
    trimmedFirst === 61 ||
    trimmedFirst === 43 ||
    trimmedFirst === 45 ||
    trimmedFirst === 64
  ) {
    return `'${raw}`;
  }
  return raw;
}

function escapeCsvCell(value: unknown): string {
  const safe = sanitizeFormulaCell(String(value ?? ""));
  if (
    safe.includes('"') ||
    safe.includes(",") ||
    safe.includes("\n") ||
    safe.includes("\r")
  ) {
    return `"${safe.replaceAll('"', '""')}"`;
  }
  return safe;
}

export function serializeTransactionsCsv(
  rows: ReadonlyArray<TransactionItem>,
  options: {
    annotations?: CsvTransactionAnnotationLookup;
    categoryLabelResolver?: (category?: string | null) => string;
  } = {},
): string {
  if (rows.length > MAX_CSV_EXPORT_ROWS) {
    throw new RangeError(`CSV export supports at most ${MAX_CSV_EXPORT_ROWS} rows per file.`);
  }

  const header = [
    "거래번호",
    "발생시각",
    "입출금",
    "금액",
    "통화",
    "거래후잔액",
    "적요/상대방",
    "분류",
    "메모",
    "상태",
  ].map(escapeCsvCell);

  const lines: string[] = [header.join(",")];
  for (let i = 0; i < rows.length; i += 1) {
    const item = rows[i];
    const annotation = options.annotations?.get(item.transactionReference);
    const categoryLabel = annotation
      ? (options.categoryLabelResolver?.(annotation.category) ?? annotation.category)
      : "미분류";
    const counterparty =
      item.counterpartyMaskedName?.trim() ||
      item.summary?.trim() ||
      "-";

    lines.push(
      [
        item.transactionReference,
        formatDateTime(item.bookedAt),
        getDirectionLabel(item.direction),
        formatMinorAmount(item.amountMinor, item.currencyCode),
        item.currencyCode,
        item.balanceAfterMinor != null
          ? formatMinorAmount(item.balanceAfterMinor, item.currencyCode)
          : "-",
        counterparty,
        categoryLabel,
        annotation?.memo ?? "",
        getTransactionStatusLabel(item.status),
      ]
        .map(escapeCsvCell)
        .join(","),
    );
  }

  return `\uFEFF${lines.join("\r\n")}\r\n`;
}

export function downloadTransactionsCsv(
  rows: ReadonlyArray<TransactionItem>,
  options: {
    annotations?: CsvTransactionAnnotationLookup;
    categoryLabelResolver?: (category?: string | null) => string;
    filename?: string;
  } = {},
): void {
  const csvContent = serializeTransactionsCsv(rows, options);
  const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = options.filename ?? "aquila-bank-transactions.csv";
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
