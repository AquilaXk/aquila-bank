import type { CustomerSession, LoginResponse } from "./types";

const STORAGE_KEY = "aquila-bank.customer-session";

function canUseStorage(): boolean {
  return typeof window !== "undefined" && typeof window.localStorage !== "undefined";
}

export function toCustomerSession(result: LoginResponse): CustomerSession | null {
  if (!result.accessToken || !result.refreshToken) {
    return null;
  }

  return {
    accessToken: result.accessToken,
    refreshToken: result.refreshToken,
    tokenType: result.tokenType ?? "Bearer",
    userId: result.userId,
    expiresAt: result.expiresAt,
    refreshExpiresAt: result.refreshExpiresAt,
  };
}

export function loadCustomerSession(): CustomerSession | null {
  if (!canUseStorage()) {
    return null;
  }

  const rawValue = window.localStorage.getItem(STORAGE_KEY);
  if (!rawValue) {
    return null;
  }

  try {
    return JSON.parse(rawValue) as CustomerSession;
  } catch {
    window.localStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

export function saveCustomerSession(session: CustomerSession): void {
  if (!canUseStorage()) {
    return;
  }

  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function clearCustomerSession(): void {
  if (!canUseStorage()) {
    return;
  }

  window.localStorage.removeItem(STORAGE_KEY);
}
