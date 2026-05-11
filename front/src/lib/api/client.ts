import type {
  AccountListResponse,
  AccountSummaryResponse,
  AuthSessionListResponse,
  BackupCodeChallengeVerifyRequest,
  BackupCodeIssueResponse,
  LoginRequest,
  LoginResponse,
  LogoutRequest,
  NotificationBulkActionRequest,
  NotificationPreferenceResponse,
  NotificationPreferenceUpdateRequest,
  NotificationQueryParams,
  NotificationQueryResponse,
  NotificationSearchParams,
  NotificationSearchResponse,
  NotificationUnreadCountResponse,
  PasswordRecoveryConfirmRequest,
  PasswordRecoveryRequest,
  PasswordRecoveryRequestResult,
  PasswordResetRequest,
  RefreshRequest,
  TotpChallengeVerifyRequest,
  TotpCodeRequest,
  TotpEnrollmentStartResponse,
  TotpEnrollmentVerifyResponse,
  TransactionDetailResponse,
  TransactionQueryParams,
  TransactionQueryResponse,
  TransferRequest,
  TransferResponse,
  TransferReversalRequest,
  TransferReversalResponse,
} from "./types";

type RequestOptions = {
  method?: "GET" | "POST" | "DELETE";
  body?: unknown;
  accessToken?: string;
  headers?: Record<string, string>;
};

type ResponseWithHeaders<T> = {
  data: T;
  headers: Headers;
};

export class ApiClientError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly body: unknown,
  ) {
    super(message);
    this.name = "ApiClientError";
  }
}

export class ApiConfigurationError extends Error {
  constructor() {
    super("NEXT_PUBLIC_API_BASE_URL is not configured.");
    this.name = "ApiConfigurationError";
  }
}

export function resolveApiBaseUrl(): string {
  return (process.env.NEXT_PUBLIC_API_BASE_URL ?? "").replace(/\/+$/, "");
}

function appendSearchParams(path: string, params: Record<string, unknown>): string {
  const searchParams = new URLSearchParams();

  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") {
      return;
    }
    searchParams.set(key, String(value));
  });

  const query = searchParams.toString();
  return query ? `${path}?${query}` : path;
}

function defaultHeaders(options: RequestOptions): HeadersInit {
  const headers: Record<string, string> = {
    Accept: "application/json",
    ...options.headers,
  };

  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  if (options.accessToken) {
    headers.Authorization = `Bearer ${options.accessToken}`;
  }

  return headers;
}

async function parseResponseBody(response: Response): Promise<unknown> {
  const contentType = response.headers.get("content-type") ?? "";
  if (response.status === 204) {
    return undefined;
  }
  if (contentType.includes("application/json")) {
    return response.json();
  }
  return response.text();
}

export function createIdempotencyKey(prefix = "web"): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return `${prefix}-${crypto.randomUUID()}`;
  }
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export class AquilaBankApiClient {
  constructor(private readonly baseUrl = resolveApiBaseUrl()) {}

  streamUrl(path: string, accessToken?: string): string {
    if (!this.baseUrl) {
      throw new ApiConfigurationError();
    }

    const url = new URL(`${this.baseUrl}${path}`);
    if (accessToken) {
      // EventSource는 custom header를 지원하지 않아 MVP에서는 query fallback으로 제한합니다.
      url.searchParams.set("accessToken", accessToken);
    }
    return url.toString();
  }

  private async requestWithHeaders<T>(
    path: string,
    options: RequestOptions = {},
  ): Promise<ResponseWithHeaders<T>> {
    if (!this.baseUrl) {
      throw new ApiConfigurationError();
    }

    const response = await fetch(`${this.baseUrl}${path}`, {
      method: options.method ?? "GET",
      headers: defaultHeaders(options),
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      credentials: "include",
    });
    const body = await parseResponseBody(response);

    if (!response.ok) {
      const message =
        typeof body === "object" && body !== null && "message" in body
          ? String((body as { message?: unknown }).message)
          : response.statusText;
      throw new ApiClientError(message, response.status, body);
    }

    return {
      data: body as T,
      headers: response.headers,
    };
  }

  private async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const response = await this.requestWithHeaders<T>(path, options);
    return response.data;
  }

  login(request: LoginRequest): Promise<LoginResponse> {
    return this.request("/api/v1/auth/login", { method: "POST", body: request });
  }

  verifyTotpChallenge(request: TotpChallengeVerifyRequest): Promise<LoginResponse> {
    return this.request("/api/v1/auth/mfa/totp/challenge/verify", {
      method: "POST",
      body: request,
    });
  }

  verifyBackupCodeChallenge(
    request: BackupCodeChallengeVerifyRequest,
  ): Promise<LoginResponse> {
    return this.request("/api/v1/auth/mfa/backup-codes/challenge/verify", {
      method: "POST",
      body: request,
    });
  }

  startTotpEnrollment(accessToken: string): Promise<TotpEnrollmentStartResponse> {
    return this.request("/api/v1/auth/mfa/totp/enroll", {
      method: "POST",
      accessToken,
    });
  }

  verifyTotpEnrollment(
    request: TotpCodeRequest,
    accessToken: string,
  ): Promise<TotpEnrollmentVerifyResponse> {
    return this.request("/api/v1/auth/mfa/totp/enroll/verify", {
      method: "POST",
      body: request,
      accessToken,
    });
  }

  disableTotp(request: TotpCodeRequest, accessToken: string): Promise<void> {
    return this.request("/api/v1/auth/mfa/totp/disable", {
      method: "POST",
      body: request,
      accessToken,
    });
  }

  issueBackupCodes(
    request: TotpCodeRequest,
    accessToken: string,
  ): Promise<BackupCodeIssueResponse> {
    return this.request("/api/v1/auth/mfa/backup-codes", {
      method: "POST",
      body: request,
      accessToken,
    });
  }

  getSessions(accessToken: string, size = 20): Promise<AuthSessionListResponse> {
    return this.request(appendSearchParams("/api/v1/auth/sessions", { size }), {
      accessToken,
    });
  }

  revokeSession(sessionId: number, accessToken: string): Promise<void> {
    return this.request(`/api/v1/auth/sessions/${sessionId}`, {
      method: "DELETE",
      accessToken,
    });
  }

  revokeAllSessions(accessToken: string): Promise<void> {
    return this.request("/api/v1/auth/sessions", {
      method: "DELETE",
      accessToken,
    });
  }

  refresh(request: RefreshRequest): Promise<LoginResponse> {
    return this.request("/api/v1/auth/refresh", {
      method: "POST",
      body: request,
    });
  }

  logout(request: LogoutRequest, accessToken: string): Promise<void> {
    return this.request("/api/v1/auth/logout", {
      method: "POST",
      body: request,
      accessToken,
    });
  }

  resetPassword(request: PasswordResetRequest, accessToken: string): Promise<void> {
    return this.request("/api/v1/auth/password-reset", {
      method: "POST",
      body: request,
      accessToken,
    });
  }

  async requestPasswordRecovery(
    request: PasswordRecoveryRequest,
  ): Promise<PasswordRecoveryRequestResult> {
    const response = await this.requestWithHeaders<void>(
      "/api/v1/auth/password-recovery/request",
      {
        method: "POST",
        body: request,
      },
    );
    return {
      handoffRequestId: response.headers.get("X-Password-Recovery-Request-Id"),
    };
  }

  confirmPasswordRecovery(request: PasswordRecoveryConfirmRequest): Promise<void> {
    return this.request("/api/v1/auth/password-recovery/confirm", {
      method: "POST",
      body: request,
    });
  }

  getAccounts(
    accessToken: string,
    params: { limit?: number; cursor?: string } = {},
  ): Promise<AccountListResponse> {
    return this.request(appendSearchParams("/api/v1/accounts", params), {
      accessToken,
    });
  }

  getAccount(accountId: number, accessToken: string): Promise<AccountSummaryResponse> {
    return this.request(`/api/v1/accounts/${accountId}`, { accessToken });
  }

  transfer(
    request: TransferRequest,
    accessToken: string,
    idempotencyKey = createIdempotencyKey("transfer"),
  ): Promise<TransferResponse> {
    return this.request("/api/v1/transfers", {
      method: "POST",
      body: request,
      accessToken,
      headers: { "Idempotency-Key": idempotencyKey },
    });
  }

  reverseTransfer(
    transactionReference: string,
    request: TransferReversalRequest,
    accessToken: string,
    idempotencyKey = createIdempotencyKey("reversal"),
  ): Promise<TransferReversalResponse> {
    return this.request(`/api/v1/transfers/${transactionReference}/reversal`, {
      method: "POST",
      body: request,
      accessToken,
      headers: { "Idempotency-Key": idempotencyKey },
    });
  }

  getTransactions(
    params: TransactionQueryParams,
    accessToken: string,
  ): Promise<TransactionQueryResponse> {
    return this.request(appendSearchParams("/api/v1/transactions", params), {
      accessToken,
    });
  }

  getArchivedTransactions(
    params: TransactionQueryParams,
    accessToken: string,
  ): Promise<TransactionQueryResponse> {
    return this.request(appendSearchParams("/api/v1/transactions/archive", params), {
      accessToken,
    });
  }

  getTransactionDetail(
    transactionReference: string,
    accountId: number,
    accessToken: string,
  ): Promise<TransactionDetailResponse> {
    return this.request(
      appendSearchParams(`/api/v1/transactions/${transactionReference}`, { accountId }),
      { accessToken },
    );
  }

  getNotifications(
    params: NotificationQueryParams,
    accessToken: string,
  ): Promise<NotificationQueryResponse> {
    return this.request(appendSearchParams("/api/v1/notifications", params), {
      accessToken,
    });
  }

  searchNotifications(
    params: NotificationSearchParams,
    accessToken: string,
  ): Promise<NotificationSearchResponse> {
    return this.request(appendSearchParams("/api/v1/notifications/search", params), {
      accessToken,
    });
  }

  getUnreadCount(accessToken: string): Promise<NotificationUnreadCountResponse> {
    return this.request("/api/v1/notifications/unread-count", { accessToken });
  }

  getNotificationPreferences(
    accessToken: string,
  ): Promise<NotificationPreferenceResponse> {
    return this.request("/api/v1/notifications/preferences", { accessToken });
  }

  updateNotificationPreferences(
    request: NotificationPreferenceUpdateRequest,
    accessToken: string,
  ): Promise<void> {
    return this.request("/api/v1/notifications/preferences", {
      method: "POST",
      body: request,
      accessToken,
    });
  }

  markNotificationAsRead(notificationId: number, accessToken: string): Promise<void> {
    return this.request(`/api/v1/notifications/${notificationId}/read`, {
      method: "POST",
      accessToken,
    });
  }

  markNotificationsAsRead(
    request: NotificationBulkActionRequest,
    accessToken: string,
  ): Promise<void> {
    return this.request("/api/v1/notifications/read", {
      method: "POST",
      body: request,
      accessToken,
    });
  }

  archiveNotifications(
    request: NotificationBulkActionRequest,
    accessToken: string,
  ): Promise<void> {
    return this.request("/api/v1/notifications/archive", {
      method: "POST",
      body: request,
      accessToken,
    });
  }

  deleteNotifications(
    request: NotificationBulkActionRequest,
    accessToken: string,
  ): Promise<void> {
    return this.request("/api/v1/notifications/delete", {
      method: "POST",
      body: request,
      accessToken,
    });
  }
}
