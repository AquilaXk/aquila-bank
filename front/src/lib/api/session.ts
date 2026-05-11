import type { CustomerSession, LoginResponse } from "./types";

export function toCustomerSession(result: LoginResponse): CustomerSession | null {
  if (result.status !== "SUCCESS") {
    return null;
  }

  return {
    tokenType: result.tokenType ?? "Bearer",
    userId: result.userId,
    expiresAt: result.expiresAt,
    refreshExpiresAt: result.refreshExpiresAt,
  };
}

export function loadCustomerSession(): CustomerSession | null {
  return null;
}

export function saveCustomerSession(session: CustomerSession): void {
  void session;
}

export function clearCustomerSession(): void {}
