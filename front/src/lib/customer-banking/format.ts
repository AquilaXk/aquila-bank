import { ApiClientError, ApiConfigurationError, ApiNetworkError } from "@/lib/api/client";

const MAX_CURRENCY_FORMATTER_CACHE = 16;

const DEFAULT_KRW_FORMATTER = new Intl.NumberFormat("ko-KR", {
  style: "currency",
  currency: "KRW",
  maximumFractionDigits: 0,
});

const CURRENCY_FORMATTER_CACHE = new Map<string, Intl.NumberFormat>([
  ["KRW", DEFAULT_KRW_FORMATTER],
]);

const DEFAULT_DATE_TIME_FORMATTER = new Intl.DateTimeFormat("ko-KR", {
  dateStyle: "short",
  timeStyle: "short",
});

function getCurrencyFormatter(currencyCode: string): Intl.NumberFormat {
  const normalized = currencyCode.trim().toUpperCase() || "KRW";
  const cached = CURRENCY_FORMATTER_CACHE.get(normalized);
  if (cached) {
    return cached;
  }
  if (CURRENCY_FORMATTER_CACHE.size >= MAX_CURRENCY_FORMATTER_CACHE) {
    for (const key of CURRENCY_FORMATTER_CACHE.keys()) {
      if (key !== "KRW") {
        CURRENCY_FORMATTER_CACHE.delete(key);
        break;
      }
    }
  }
  const formatter = new Intl.NumberFormat("ko-KR", {
    style: "currency",
    currency: normalized,
    maximumFractionDigits: 0,
  });
  CURRENCY_FORMATTER_CACHE.set(normalized, formatter);
  return formatter;
}

export function toLocalInputValue(date: Date): string {
  const offset = date.getTimezoneOffset();
  const localDate = new Date(date.getTime() - offset * 60_000);
  return localDate.toISOString().slice(0, 16);
}

export function toIsoDateTime(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) {
    throw new Error("유효한 날짜/시간을 입력하세요.");
  }
  const parsed = new Date(trimmed);
  if (Number.isNaN(parsed.getTime())) {
    throw new Error("유효한 날짜/시간을 입력하세요.");
  }
  return parsed.toISOString();
}

export function tryToIsoDateTime(value: string): string | undefined {
  if (!value.trim()) {
    return undefined;
  }
  try {
    return toIsoDateTime(value);
  } catch {
    return undefined;
  }
}

export function toOptionalNumber(value: string): number | undefined {
  const trimmed = value.trim();
  if (!trimmed) {
    return undefined;
  }
  const parsed = Number(trimmed);
  if (!Number.isFinite(parsed)) {
    throw new Error("유효한 숫자를 입력하세요.");
  }
  return parsed;
}

export function formatMinorAmount(value: number, currencyCode = "KRW"): string {
  if (!Number.isFinite(value)) {
    return "-";
  }
  return getCurrencyFormatter(currencyCode).format(value / 100);
}

export function toErrorMessage(error: unknown): string {
  if (error instanceof ApiConfigurationError) {
    return "백엔드 API 주소가 설정되지 않았습니다. NEXT_PUBLIC_API_BASE_URL을 확인하세요.";
  }
  if (error instanceof ApiNetworkError) {
    return "백엔드 서버에 연결하지 못했습니다. 백엔드 실행 상태와 API 주소를 확인하세요.";
  }
  if (error instanceof ApiClientError) {
    return `[${error.status}] ${error.message}`;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "요청 처리 중 오류가 발생했습니다.";
}

export function formatDateTime(value?: string | null): string {
  if (!value) {
    return "-";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return "-";
  }
  return DEFAULT_DATE_TIME_FORMATTER.format(parsed);
}

export function maskToken(value: string): string {
  if (value.length <= 16) {
    return value;
  }
  return `${value.slice(0, 10)}...${value.slice(-6)}`;
}
