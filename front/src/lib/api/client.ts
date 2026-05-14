import type {
  AccountListResponse,
  AccountSummaryResponse,
  AuthSessionListResponse,
  BackupCodeChallengeVerifyRequest,
  BackupCodeIssueResponse,
  CustomerApplicationDetailsResponse,
  CustomerApplicationListResponse,
  CustomerApplicationRequest,
  CustomerApplicationResponse,
  LoginRequest,
  LoginResponse,
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
  TotpChallengeVerifyRequest,
  TotpCodeRequest,
  TotpEnrollmentStartResponse,
  TotpEnrollmentVerifyResponse,
  TransactionDetailResponse,
  TransactionQueryParams,
  TransactionQueryResponse,
  TransferPreviewRequest,
  TransferPreviewResponse,
  TransferRequest,
  TransferResponse,
  TransferReversalRequest,
  TransferReversalResponse,
} from "./types";

type RequestOptions = {
  method?: "GET" | "POST" | "DELETE";
  body?: unknown;
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

  streamUrl(path: string): string {
    if (!this.baseUrl) {
      throw new ApiConfigurationError();
    }

    const url = new URL(`${this.baseUrl}${path}`);
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

  startTotpEnrollment(): Promise<TotpEnrollmentStartResponse> {
    return this.request("/api/v1/auth/mfa/totp/enroll", {
      method: "POST",
    });
  }

  verifyTotpEnrollment(
    request: TotpCodeRequest,
  ): Promise<TotpEnrollmentVerifyResponse> {
    return this.request("/api/v1/auth/mfa/totp/enroll/verify", {
      method: "POST",
      body: request,
    });
  }

  disableTotp(request: TotpCodeRequest): Promise<void> {
    return this.request("/api/v1/auth/mfa/totp/disable", {
      method: "POST",
      body: request,
    });
  }

  issueBackupCodes(
    request: TotpCodeRequest,
  ): Promise<BackupCodeIssueResponse> {
    return this.request("/api/v1/auth/mfa/backup-codes", {
      method: "POST",
      body: request,
    });
  }

  getSessions(size = 20): Promise<AuthSessionListResponse> {
    return this.request(appendSearchParams("/api/v1/auth/sessions", { size }));
  }

  revokeSession(sessionId: number): Promise<void> {
    return this.request(`/api/v1/auth/sessions/${sessionId}`, {
      method: "DELETE",
    });
  }

  revokeAllSessions(): Promise<void> {
    return this.request("/api/v1/auth/sessions", {
      method: "DELETE",
    });
  }

  refresh(): Promise<LoginResponse> {
    return this.request("/api/v1/auth/refresh", {
      method: "POST",
      body: {},
    });
  }

  logout(): Promise<void> {
    return this.request("/api/v1/auth/logout", {
      method: "POST",
      body: {},
    });
  }

  resetPassword(request: PasswordResetRequest): Promise<void> {
    return this.request("/api/v1/auth/password-reset", {
      method: "POST",
      body: request,
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
    params: { limit?: number; cursor?: string } = {},
  ): Promise<AccountListResponse> {
    return this.request(appendSearchParams("/api/v1/accounts", params));
  }

  getAccount(accountId: number): Promise<AccountSummaryResponse> {
    return this.request(`/api/v1/accounts/${accountId}`);
  }

  previewTransfer(
    params: TransferPreviewRequest,
  ): Promise<TransferPreviewResponse> {
    return this.request(appendSearchParams("/api/v1/transfers/preview", params));
  }

  transfer(
    request: TransferRequest,
    idempotencyKey = createIdempotencyKey("transfer"),
  ): Promise<TransferResponse> {
    return this.request("/api/v1/transfers", {
      method: "POST",
      body: request,
      headers: { "Idempotency-Key": idempotencyKey },
    });
  }

  reverseTransfer(
    transactionReference: string,
    request: TransferReversalRequest,
    idempotencyKey = createIdempotencyKey("reversal"),
  ): Promise<TransferReversalResponse> {
    return this.request(`/api/v1/transfers/${transactionReference}/reversal`, {
      method: "POST",
      body: request,
      headers: { "Idempotency-Key": idempotencyKey },
    });
  }

  submitCustomerApplication(
    request: CustomerApplicationRequest,
    idempotencyKey = createIdempotencyKey("application"),
  ): Promise<CustomerApplicationResponse> {
    return this.request("/api/v1/customer-service/applications", {
      method: "POST",
      body: request,
      headers: { "Idempotency-Key": idempotencyKey },
    });
  }

  listCustomerApplications(limit = 20): Promise<CustomerApplicationListResponse> {
    return this.request(
      appendSearchParams("/api/v1/customer-service/applications", { limit }),
    );
  }

  getCustomerApplication(
    applicationReference: string,
  ): Promise<CustomerApplicationDetailsResponse> {
    return this.request(
      `/api/v1/customer-service/applications/${encodeURIComponent(applicationReference)}`,
    );
  }

  cancelCustomerApplication(
    applicationReference: string,
  ): Promise<CustomerApplicationDetailsResponse> {
    return this.request(
      `/api/v1/customer-service/applications/${encodeURIComponent(
        applicationReference,
      )}/cancel`,
      {
        method: "POST",
      },
    );
  }

  getTransactions(params: TransactionQueryParams): Promise<TransactionQueryResponse> {
    return this.request(appendSearchParams("/api/v1/transactions", params));
  }

  getArchivedTransactions(
    params: TransactionQueryParams,
  ): Promise<TransactionQueryResponse> {
    return this.request(appendSearchParams("/api/v1/transactions/archive", params));
  }

  getTransactionDetail(
    transactionReference: string,
    accountId: number,
  ): Promise<TransactionDetailResponse> {
    return this.request(
      appendSearchParams(`/api/v1/transactions/${transactionReference}`, { accountId }),
    );
  }

  getNotifications(params: NotificationQueryParams): Promise<NotificationQueryResponse> {
    return this.request(appendSearchParams("/api/v1/notifications", params));
  }

  searchNotifications(
    params: NotificationSearchParams,
  ): Promise<NotificationSearchResponse> {
    return this.request(appendSearchParams("/api/v1/notifications/search", params));
  }

  getUnreadCount(): Promise<NotificationUnreadCountResponse> {
    return this.request("/api/v1/notifications/unread-count");
  }

  getNotificationPreferences(): Promise<NotificationPreferenceResponse> {
    return this.request("/api/v1/notifications/preferences");
  }

  updateNotificationPreferences(
    request: NotificationPreferenceUpdateRequest,
  ): Promise<void> {
    return this.request("/api/v1/notifications/preferences", {
      method: "POST",
      body: request,
    });
  }

  markNotificationAsRead(notificationId: number): Promise<void> {
    return this.request(`/api/v1/notifications/${notificationId}/read`, {
      method: "POST",
    });
  }

  markNotificationsAsRead(request: NotificationBulkActionRequest): Promise<void> {
    return this.request("/api/v1/notifications/read", {
      method: "POST",
      body: request,
    });
  }

  archiveNotifications(request: NotificationBulkActionRequest): Promise<void> {
    return this.request("/api/v1/notifications/archive", {
      method: "POST",
      body: request,
    });
  }

  deleteNotifications(request: NotificationBulkActionRequest): Promise<void> {
    return this.request("/api/v1/notifications/delete", {
      method: "POST",
      body: request,
    });
  }
}
