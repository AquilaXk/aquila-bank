import { ApiClientError, ApiConfigurationError, ApiNetworkError } from "@/lib/api/client";

export function toLocalInputValue(date: Date): string {
  const offset = date.getTimezoneOffset();
  const localDate = new Date(date.getTime() - offset * 60_000);
  return localDate.toISOString().slice(0, 16);
}

export function toIsoDateTime(value: string): string {
  return new Date(value).toISOString();
}

export function toOptionalNumber(value: string): number | undefined {
  if (!value.trim()) {
    return undefined;
  }
  return Number(value);
}

export function formatMinorAmount(value: number, currencyCode = "KRW"): string {
  return new Intl.NumberFormat("ko-KR", {
    style: "currency",
    currency: currencyCode,
    maximumFractionDigits: 0,
  }).format(value / 100);
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
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}

export function maskToken(value: string): string {
  if (value.length <= 16) {
    return value;
  }
  return `${value.slice(0, 10)}...${value.slice(-6)}`;
}
