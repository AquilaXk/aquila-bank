export type Nullable<T> = T | null;

export type ApiValidationDetail = {
  field?: string;
  message?: string;
};

export type ApiErrorBody = {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
  requestId?: string;
  details?: ApiValidationDetail[];
};

export type LoginStatus = "SUCCESS" | "MFA_REQUIRED";

export type LoginResponse = {
  status: LoginStatus | string;
  accessToken: Nullable<string>;
  refreshToken: Nullable<string>;
  tokenType: Nullable<string>;
  expiresAt: Nullable<string>;
  refreshExpiresAt: Nullable<string>;
  userId: Nullable<number>;
  challengeId: Nullable<string>;
  challengeType: Nullable<string>;
  challengeExpiresAt: Nullable<string>;
};

export type LoginRequest = {
  loginId: string;
  password: string;
};

export type TotpChallengeVerifyRequest = {
  challengeId: string;
  totpCode: string;
  rememberDevice?: boolean;
};

export type BackupCodeChallengeVerifyRequest = {
  challengeId: string;
  backupCode: string;
  rememberDevice?: boolean;
};

export type TotpCodeRequest = {
  totpCode: string;
};

export type TotpEnrollmentStartResponse = {
  status: string;
  secretKey: string;
  otpauthUri: string;
  expiresAt: string;
};

export type TotpEnrollmentVerifyResponse = {
  status: string;
  verifiedAt: string;
};

export type BackupCodeIssueResponse = {
  codeCount: number;
  backupCodes: string[];
};

export type AuthSessionItem = {
  sessionId: number;
  sessionStatus: string;
  expiresAt: string;
  lastUsedAt: Nullable<string>;
  createdAt: string;
  deviceName: Nullable<string>;
  ipAddress: Nullable<string>;
  currentSession: boolean;
};

export type AuthSessionListResponse = {
  items: AuthSessionItem[];
};

export type RefreshRequest = {
  refreshToken?: string;
};

export type LogoutRequest = {
  refreshToken?: string;
};

export type PasswordResetRequest = {
  currentPassword: string;
  newPassword: string;
};

export type PasswordRecoveryRequest = {
  loginId: string;
};

export type PasswordRecoveryConfirmRequest = {
  recoveryToken: string;
  newPassword: string;
};

export type PasswordRecoveryRequestResult = {
  handoffRequestId: Nullable<string>;
};

export type AccountItem = {
  accountId: number;
  accountNumber: string;
  displayName: string;
  accountStatus: string;
  currencyCode: string;
  availableBalanceMinor: number;
  pendingBalanceMinor: number;
  createdAt: string;
  balanceUpdatedAt: string;
};

export type AccountListResponse = {
  items: AccountItem[];
  nextCursor: Nullable<string>;
};

export type AccountSummaryResponse = AccountItem;

export type TransferRequest = {
  sourceAccountId: number;
  targetAccountId: number;
  amountMinor: number;
  currencyCode: string;
  summary: string;
};

export type TransferPreviewRequest = {
  sourceAccountId: number;
  targetAccountId: number;
  amountMinor: number;
  currencyCode: string;
};

export type TransferPreviewAccount = {
  accountId: number;
  maskedAccountNumber: string;
  displayName: string;
  accountStatus: string;
  currencyCode: string;
};

export type TransferPreviewResponse = {
  sourceAccountId: number;
  targetAccount: TransferPreviewAccount;
  amountMinor: number;
  currencyCode: string;
  feeMinor: number;
  feePolicy: string;
  totalDebitMinor: number;
  singleTransferLimitMinor: number;
  dailyTransferLimitMinor: number;
  dailyUsedMinor: number;
  dailyRemainingMinor: number;
  allowed: boolean;
  blockedReason: string;
  otpRequired: boolean;
};

export type TransferResponse = {
  transactionReference: string;
  sourceAccountId: number;
  targetAccountId: number;
  amountMinor: number;
  currencyCode: string;
  availableBalanceAfterMinor: number;
  bookedAt: string;
  status: string;
};

export type TransferReversalReason =
  | "CUSTOMER_REQUEST"
  | "DUPLICATE"
  | "WRONG_AMOUNT"
  | "WRONG_TARGET"
  | "FRAUD_REPORTED"
  | string;

export type TransferReversalRequest = {
  sourceAccountId: number;
  amountMinor: number;
  reversalReason: TransferReversalReason;
  summary: string;
};

export type TransferReversalResponse = {
  originalTransactionReference: string;
  reversalTransactionReference: string;
  sourceAccountId: number;
  targetAccountId: number;
  amountMinor: number;
  currencyCode: string;
  availableBalanceAfterMinor: number;
  bookedAt: string;
  status: string;
};

export type CustomerApplicationType =
  | "BILL_PAYMENT"
  | "OPEN_BANKING_CONNECTION"
  | "DEPOSIT_PRODUCT_APPLICATION"
  | "LOAN_APPLICATION"
  | "FOREIGN_EXCHANGE_APPLICATION"
  | "CERTIFICATE_ISSUANCE"
  | "CERTIFICATE_REGISTRATION"
  | "SECURITY_MEDIA_APPLICATION"
  | "TRANSFER_LIMIT_CHANGE"
  | "INCIDENT_REPORT";

export type CustomerApplicationRequest = {
  applicationType: CustomerApplicationType;
  accountId?: number;
  totpCode: string;
  payload: Record<string, unknown>;
};

export type CustomerApplicationResponse = {
  applicationReference: string;
  accountId: Nullable<number>;
  applicationType: CustomerApplicationType | string;
  status: string;
  mfaVerified: boolean;
  mfaVerifiedAt: Nullable<string>;
  submittedAt: string;
  updatedAt: string;
};

export type TransactionStatus =
  | "PENDING"
  | "BOOKED"
  | "REVERSED"
  | "FAILED"
  | string;

export type TransactionDirection = "DEBIT" | "CREDIT" | string;

export type TransactionQueryParams = {
  accountId: number;
  from: string;
  to: string;
  limit?: number;
  cursor?: string;
  status?: string;
  direction?: string;
  minAmountMinor?: number;
  maxAmountMinor?: number;
  transactionReference?: string;
  responseShape?: "full" | "slim";
};

export type TransactionItem = {
  id: number;
  accountId: number;
  transactionReference: string;
  direction: TransactionDirection;
  status: TransactionStatus;
  amountMinor: number;
  balanceAfterMinor?: Nullable<number>;
  currencyCode: string;
  summary?: Nullable<string>;
  counterpartyMaskedName?: Nullable<string>;
  bookedAt: string;
};

export type TransactionQueryResponse = {
  items: TransactionItem[];
  nextCursor: Nullable<string>;
  hasNext: boolean;
  limit: number;
};

export type TransactionDetailResponse = {
  accountId: number;
  transactionReference: string;
  direction: TransactionDirection;
  transactionStatus: string;
  amountMinor: number;
  balanceAfterMinor: number;
  currencyCode: string;
  summary: string;
  counterpartyMaskedName: Nullable<string>;
  bookedAt: string;
  entryReference: string;
  entryStatus: string;
  occurredAt: string;
  description: Nullable<string>;
};

export type NotificationReadStatusFilter = "ALL" | "READ" | "UNREAD" | string;

export type NotificationQueryParams = {
  limit?: number;
  cursor?: string;
};

export type NotificationSearchParams = NotificationQueryParams & {
  readStatus?: NotificationReadStatusFilter;
  eventType?: string;
  from?: string;
  to?: string;
};

export type NotificationItem = {
  notificationId: number;
  accountId: number;
  eventType: string;
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
  readAt: Nullable<string>;
};

export type NotificationQueryResponse = {
  items: NotificationItem[];
  nextCursor: Nullable<string>;
  hasNext: boolean;
  limit: number;
};

export type NotificationSearchResponse = NotificationQueryResponse & {
  appliedFrom?: string;
  appliedTo?: string;
};

export type NotificationUnreadCountResponse = {
  unreadCount: number;
};

export type NotificationBulkActionRequest = {
  notificationIds: number[];
};

export type NotificationPreferenceItem = {
  category: string;
  channel: string;
  enabled: boolean;
};

export type NotificationPreferenceResponse = {
  items: NotificationPreferenceItem[];
};

export type NotificationPreferenceUpdateRequest = {
  items: Array<{
    category: string;
    channel: string;
    enabled: boolean;
  }>;
};

export type CustomerSession = {
  tokenType: string;
  userId: Nullable<number>;
  expiresAt: Nullable<string>;
  refreshExpiresAt: Nullable<string>;
};
