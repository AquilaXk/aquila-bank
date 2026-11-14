import type { FormEvent } from "react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ApiClientError,
  AquilaBankApiClient,
  createIdempotencyKey,
} from "@/lib/api/client";
import { clearCustomerSession, toCustomerSession } from "@/lib/api/session";
import type {
  AccountItem,
  AccountSummaryResponse,
  AuthSessionItem,
  BackupCodeIssueResponse,
  CustomerApplicationDetailsResponse,
  CustomerApplicationResponse,
  CustomerSession,
  LoginResponse,
  NotificationItem,
  NotificationPreferenceItem,
  NotificationQueryResponse,
  PasswordRecoveryRequestResult,
  TotpEnrollmentStartResponse,
  TransactionDetailResponse,
  TransactionItem,
  TransactionQueryResponse,
  TransferPreviewResponse,
  TransferResponse,
  TransferReversalResponse,
} from "@/lib/api/types";
import {
  krwToMinor,
  toOptionalMinorFromKrw,
} from "@/lib/customer-banking/currency";
import {
  toErrorMessage,
  toIsoDateTime,
  toLocalInputValue,
  toOptionalNumber,
} from "@/lib/customer-banking/format";
import type {
  AlertMessage,
  CustomerApplicationSubmitInput,
  MenuSection,
} from "@/lib/customer-banking/types";

export const BUSY_DOMAIN_AUTH = 1 << 0;
export const BUSY_DOMAIN_ACCOUNTS = 1 << 1;
export const BUSY_DOMAIN_TRANSFER = 1 << 2;
export const BUSY_DOMAIN_TRANSACTIONS = 1 << 3;
export const BUSY_DOMAIN_NOTIFICATIONS = 1 << 4;
export const BUSY_DOMAIN_APPLICATIONS = 1 << 5;

const SSE_DEDUP_RING_CAPACITY = 256;
const SSE_DEDUP_RING_MASK = SSE_DEDUP_RING_CAPACITY - 1;

export type SseConnectionState =
  | "DISCONNECTED"
  | "CONNECTING"
  | "CONNECTED"
  | "ERROR";

export type NotificationViewMode = "inbox" | "search" | "preferences";

function buildInitialTransactionFilters() {
  return {
    accountId: "",
    from: toLocalInputValue(new Date(Date.now() - 7 * 24 * 60 * 60 * 1000)),
    to: toLocalInputValue(new Date()),
    limit: "50",
    cursor: "",
    status: "",
    direction: "",
    minAmountKrw: "",
    maxAmountKrw: "",
    transactionReference: "",
    responseShape: "full" as "full" | "slim",
  };
}

function buildInitialNotificationFilters() {
  return {
    limit: "20",
    cursor: "",
    readStatus: "ALL",
    eventType: "",
    from: toLocalInputValue(new Date(Date.now() - 7 * 24 * 60 * 60 * 1000)),
    to: toLocalInputValue(new Date()),
  };
}

function parseSseNotificationPayload(rawData: string): NotificationItem | null {
  if (!rawData) {
    return null;
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(rawData);
  } catch {
    return null;
  }
  if (typeof parsed !== "object" || parsed === null) {
    return null;
  }
  const envelope = parsed as Record<string, unknown>;
  const candidate =
    typeof envelope.notification === "object" && envelope.notification !== null
      ? (envelope.notification as Record<string, unknown>)
      : envelope;
  const rawId = candidate.notificationId ?? candidate.id;
  const notificationId = Number(rawId);
  if (!Number.isSafeInteger(notificationId) || notificationId <= 0) {
    return null;
  }
  return {
    notificationId,
    accountId: Number(candidate.accountId ?? 0) || 0,
    eventType: String(candidate.eventType ?? "SYSTEM_NOTICE"),
    title: String(candidate.title ?? "실시간 알림"),
    message: String(candidate.message ?? ""),
    read: Boolean(candidate.read),
    createdAt:
      typeof candidate.createdAt === "string" && candidate.createdAt
        ? candidate.createdAt
        : new Date().toISOString(),
    readAt: typeof candidate.readAt === "string" ? candidate.readAt : null,
  };
}

function mergeAccountBalanceUpdate<T extends AccountItem>(
  account: T,
  accountId: number,
  availableBalanceAfterMinor: number,
  bookedAt: string,
): T {
  if (account.accountId !== accountId) {
    return account;
  }
  return {
    ...account,
    availableBalanceMinor: availableBalanceAfterMinor,
    balanceUpdatedAt: bookedAt || account.balanceUpdatedAt,
  };
}

function insertNotificationMonotonic(
  items: NotificationItem[],
  incoming: NotificationItem,
): NotificationItem[] {
  for (let i = 0; i < items.length; i += 1) {
    if (items[i].notificationId === incoming.notificationId) {
      return items;
    }
  }
  if (items.length === 0) {
    return [incoming];
  }
  const incomingTime = Date.parse(incoming.createdAt);
  const headTime = Date.parse(items[0].createdAt);
  if (
    !Number.isNaN(incomingTime) &&
    !Number.isNaN(headTime) &&
    incomingTime >= headTime
  ) {
    return [incoming, ...items];
  }
  const next = items.slice();
  let insertAt = next.length;
  for (let i = 0; i < next.length; i += 1) {
    const existingTime = Date.parse(next[i].createdAt);
    if (
      (!Number.isNaN(incomingTime) &&
        !Number.isNaN(existingTime) &&
        incomingTime > existingTime) ||
      (incomingTime === existingTime &&
        incoming.notificationId > next[i].notificationId)
    ) {
      insertAt = i;
      break;
    }
  }
  next.splice(insertAt, 0, incoming);
  return next;
}

export function useCustomerBanking() {
  const api = useMemo(() => new AquilaBankApiClient(), []);
  const [activeSection, setActiveSection] = useState<MenuSection>("dashboard");
  const [session, setSession] = useState<CustomerSession | null>(null);
  const [authGeneration, setAuthGeneration] = useState(0);
  const [alert, setAlert] = useState<AlertMessage>({
    type: "info",
    text: "안전한 웹뱅킹 이용을 위해 로그인 후 업무를 진행하세요.",
  });
  const [busyMask, setBusyMask] = useState(0);
  const domainLabelsRef = useRef<Map<number, string>>(new Map());
  const domainControllersRef = useRef<Map<number, AbortController>>(new Map());
  const domainSeqRef = useRef<Map<number, number>>(new Map());
  const [busyLabel, setBusyLabel] = useState<string | null>(null);

  const [loginForm, setLoginForm] = useState({ loginId: "", password: "" });
  const [challenge, setChallenge] = useState<LoginResponse | null>(null);
  const [totpChallengeForm, setTotpChallengeForm] = useState({
    totpCode: "",
    rememberDevice: false,
  });
  const [backupChallengeForm, setBackupChallengeForm] = useState({
    backupCode: "",
    rememberDevice: false,
  });
  const [passwordRecoveryForm, setPasswordRecoveryForm] = useState({
    loginId: "",
    recoveryToken: "",
    newPassword: "",
  });
  const [passwordResetForm, setPasswordResetForm] = useState({
    currentPassword: "",
    newPassword: "",
  });
  const [totpCode, setTotpCode] = useState("");
  const [sessions, setSessions] = useState<AuthSessionItem[]>([]);
  const [totpEnrollment, setTotpEnrollment] =
    useState<TotpEnrollmentStartResponse | null>(null);
  const [backupCodes, setBackupCodes] = useState<BackupCodeIssueResponse | null>(
    null,
  );
  const [passwordRecoveryResult, setPasswordRecoveryResult] =
    useState<PasswordRecoveryRequestResult | null>(null);

  const [accounts, setAccounts] = useState<AccountItem[]>([]);
  const [accountCursor, setAccountCursor] = useState("");
  const [accountLimit, setAccountLimit] = useState(20);
  const [selectedAccount, setSelectedAccount] =
    useState<AccountSummaryResponse | null>(null);

  const [transferForm, setTransferForm] = useState({
    sourceAccountId: "",
    targetAccountNumber: "",
    amountKrw: "",
    currencyCode: "KRW",
    summary: "",
  });
  const [transferResult, setTransferResult] = useState<TransferResponse | null>(
    null,
  );
  const [transferPreview, setTransferPreview] =
    useState<TransferPreviewResponse | null>(null);
  const [reversalForm, setReversalForm] = useState({
    transactionReference: "",
    sourceAccountId: "",
    amountKrw: "",
    reversalReason: "CUSTOMER_REQUEST",
    summary: "",
  });
  const [reversalResult, setReversalResult] =
    useState<TransferReversalResponse | null>(null);

  const transferIdempotencyKeyRef = useRef<string | null>(null);
  const reversalIdempotencyKeyRef = useRef<string | null>(null);
  const applicationIdempotencyKeyRef = useRef<string | null>(null);
  const applicationSignatureRef = useRef<string>("");

  const [customerApplicationResult, setCustomerApplicationResult] =
    useState<CustomerApplicationResponse | null>(null);
  const [customerApplications, setCustomerApplications] = useState<
    CustomerApplicationDetailsResponse[]
  >([]);
  const [selectedCustomerApplication, setSelectedCustomerApplication] =
    useState<CustomerApplicationDetailsResponse | null>(null);

  const [transactionMode, setTransactionMode] = useState<"active" | "archive">(
    "active",
  );
  const [transactionFilters, setTransactionFilters] = useState(
    buildInitialTransactionFilters,
  );
  const [transactionSlice, setTransactionSlice] =
    useState<TransactionQueryResponse | null>(null);
  const [transactions, setTransactions] = useState<TransactionItem[]>([]);
  const [transactionDetail, setTransactionDetail] =
    useState<TransactionDetailResponse | null>(null);

  const [notificationMode, setNotificationMode] =
    useState<NotificationViewMode>("inbox");
  const [notificationFilters, setNotificationFilters] = useState(
    buildInitialNotificationFilters,
  );
  const [notificationSlice, setNotificationSlice] =
    useState<NotificationQueryResponse | null>(null);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [selectedNotificationIds, setSelectedNotificationIds] = useState<
    Set<number>
  >(() => new Set());
  const [unreadCount, setUnreadCount] = useState<number | null>(null);
  const [preferences, setPreferences] = useState<NotificationPreferenceItem[]>([]);
  const [notificationCursorStack, setNotificationCursorStack] = useState<string[]>(
    [],
  );
  const activeNotificationCursorRef = useRef<string>("");

  const [sseStatus, setSseStatus] = useState<{
    state: SseConnectionState;
    lastEventAt: string;
    lastEventId: string;
  }>({
    state: "DISCONNECTED",
    lastEventAt: "",
    lastEventId: "",
  });
  const eventSourceRef = useRef<EventSource | null>(null);
  const sseDedupRingRef = useRef<Float64Array>(
    new Float64Array(SSE_DEDUP_RING_CAPACITY).fill(Number.NaN),
  );
  const sseDedupWriteIdxRef = useRef<number>(0);

  const sessionRef = useRef<CustomerSession | null>(null);
  sessionRef.current = session;

  const syncBusyDisplay = useCallback((nextMask: number) => {
    setBusyMask(nextMask);
    if (nextMask === 0) {
      setBusyLabel(null);
      return;
    }
    const labels = domainLabelsRef.current;
    for (const [domain, label] of labels.entries()) {
      if ((nextMask & domain) !== 0) {
        setBusyLabel(label);
        return;
      }
    }
    setBusyLabel("처리 중");
  }, []);

  const recordSseNotificationId = useCallback((notificationId: number): boolean => {
    if (!Number.isSafeInteger(notificationId) || notificationId <= 0) {
      return false;
    }
    const ring = sseDedupRingRef.current;
    for (let i = 0; i < SSE_DEDUP_RING_CAPACITY; i += 1) {
      if (ring[i] === notificationId) {
        return false;
      }
    }
    const writeIdx = sseDedupWriteIdxRef.current;
    ring[writeIdx] = notificationId;
    sseDedupWriteIdxRef.current = (writeIdx + 1) & SSE_DEDUP_RING_MASK;
    return true;
  }, []);

  const resetAllAuthenticatedState = useCallback(
    (nextAlert?: AlertMessage) => {
      eventSourceRef.current?.close();
      eventSourceRef.current = null;

      for (const controller of domainControllersRef.current.values()) {
        controller.abort();
      }
      domainControllersRef.current.clear();
      domainLabelsRef.current.clear();
      for (const domain of domainSeqRef.current.keys()) {
        domainSeqRef.current.set(domain, (domainSeqRef.current.get(domain) ?? 0) + 1);
      }
      syncBusyDisplay(0);

      clearCustomerSession();
      setSession(null);
      setChallenge(null);
      setLoginForm({ loginId: "", password: "" });
      setTotpChallengeForm({ totpCode: "", rememberDevice: false });
      setBackupChallengeForm({ backupCode: "", rememberDevice: false });
      setPasswordRecoveryForm({
        loginId: "",
        recoveryToken: "",
        newPassword: "",
      });
      setPasswordRecoveryResult(null);
      setPasswordResetForm({ currentPassword: "", newPassword: "" });
      setTotpCode("");
      setSessions([]);
      setTotpEnrollment(null);
      setBackupCodes(null);

      setAccounts([]);
      setAccountCursor("");
      setSelectedAccount(null);
      setTransferForm({
        sourceAccountId: "",
        targetAccountNumber: "",
        amountKrw: "",
        currencyCode: "KRW",
        summary: "",
      });
      setTransferPreview(null);
      setTransferResult(null);
      setReversalForm({
        transactionReference: "",
        sourceAccountId: "",
        amountKrw: "",
        reversalReason: "CUSTOMER_REQUEST",
        summary: "",
      });
      setReversalResult(null);

      transferIdempotencyKeyRef.current = null;
      reversalIdempotencyKeyRef.current = null;
      applicationIdempotencyKeyRef.current = null;
      applicationSignatureRef.current = "";

      setCustomerApplicationResult(null);
      setCustomerApplications([]);
      setSelectedCustomerApplication(null);

      setTransactionMode("active");
      setTransactionFilters(buildInitialTransactionFilters());
      setTransactionSlice(null);
      setTransactions([]);
      setTransactionDetail(null);

      setNotificationMode("inbox");
      setNotificationFilters(buildInitialNotificationFilters());
      setNotificationSlice(null);
      setNotifications([]);
      setSelectedNotificationIds(new Set());
      setUnreadCount(null);
      setPreferences([]);
      setNotificationCursorStack([]);
      activeNotificationCursorRef.current = "";

      setSseStatus({
        state: "DISCONNECTED",
        lastEventAt: "",
        lastEventId: "",
      });
      sseDedupRingRef.current.fill(Number.NaN);
      sseDedupWriteIdxRef.current = 0;

      setAuthGeneration((gen) => gen + 1);
      if (nextAlert) {
        setAlert(nextAlert);
      }
    },
    [syncBusyDisplay],
  );

  useEffect(() => {
    const controllers = domainControllersRef.current;
    return () => {
      eventSourceRef.current?.close();
      for (const controller of controllers.values()) {
        controller.abort();
      }
      controllers.clear();
    };
  }, []);

  async function runAction(
    label: string,
    action: (ctx: { signal: AbortSignal; isCurrent: () => boolean }) => Promise<void>,
    options: {
      domain?: number;
      abortPrevious?: boolean;
      requireAuthCheckOn401?: boolean;
    } = {},
  ): Promise<boolean> {
    const domain = options.domain ?? BUSY_DOMAIN_AUTH;
    if (options.abortPrevious) {
      domainControllersRef.current.get(domain)?.abort();
    }
    const controller = new AbortController();
    domainControllersRef.current.set(domain, controller);
    const seq = (domainSeqRef.current.get(domain) ?? 0) + 1;
    domainSeqRef.current.set(domain, seq);
    domainLabelsRef.current.set(domain, label);

    let activeMask = 0;
    for (const activeDomain of domainLabelsRef.current.keys()) {
      activeMask |= activeDomain;
    }
    syncBusyDisplay(activeMask);

    const isCurrent = () => domainSeqRef.current.get(domain) === seq;

    try {
      await action({ signal: controller.signal, isCurrent });
      return true;
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        return false;
      }
      if (
        options.requireAuthCheckOn401 !== false &&
        error instanceof ApiClientError &&
        error.status === 401 &&
        sessionRef.current !== null
      ) {
        resetAllAuthenticatedState({
          type: "error",
          text: "세션이 만료되었거나 인증이 해제되었습니다. 다시 로그인하세요.",
        });
        return false;
      }
      if (isCurrent()) {
        setAlert({ type: "error", text: toErrorMessage(error) });
      }
      return false;
    } finally {
      if (isCurrent()) {
        domainControllersRef.current.delete(domain);
        domainLabelsRef.current.delete(domain);
        let remainingMask = 0;
        for (const activeDomain of domainLabelsRef.current.keys()) {
          remainingMask |= activeDomain;
        }
        syncBusyDisplay(remainingMask);
      }
    }
  }

  function applyLoginResult(result: LoginResponse): void {
    if (result.status === "MFA_REQUIRED") {
      setChallenge(result);
      setAlert({
        type: "info",
        text: "추가 인증이 필요합니다. TOTP 또는 복구 코드를 입력하세요.",
      });
      return;
    }

    const nextSession = toCustomerSession(result);
    if (!nextSession) {
      setAlert({
        type: "error",
        text: "로그인 세션을 생성하지 못했습니다.",
      });
      return;
    }

    setSession(nextSession);
    setChallenge(null);
    setAlert({
      type: "success",
      text: "로그인되었습니다.",
    });

    // 로그인 직후 계좌 선택 드롭다운용 보유 계좌 목록을 조용히 채운다.
    const accountController = new AbortController();
    domainControllersRef.current.set(BUSY_DOMAIN_ACCOUNTS, accountController);
    void api
      .getAccounts({ limit: accountLimit }, { signal: accountController.signal })
      .then((res) => {
        if (!sessionRef.current || accountController.signal.aborted) {
          return;
        }
        setAccounts(res.items);
        setAccountCursor(res.nextCursor ?? "");
        if (res.items[0]) {
          const defaultId = String(res.items[0].accountId);
          setSelectedAccount((prev) => prev ?? res.items[0]);
          setTransferForm((form) =>
            form.sourceAccountId ? form : { ...form, sourceAccountId: defaultId },
          );
          setReversalForm((form) =>
            form.sourceAccountId ? form : { ...form, sourceAccountId: defaultId },
          );
          setTransactionFilters((form) =>
            form.accountId ? form : { ...form, accountId: defaultId },
          );
        }
      })
      .catch(() => undefined);
  }

  function requireSession(): CustomerSession | null {
    if (!sessionRef.current) {
      setAlert({ type: "error", text: "로그인이 필요한 업무입니다." });
      return null;
    }
    return sessionRef.current;
  }

  async function syncLinkedAccountStateAfterTransfer(
    accountId: number,
    availableBalanceAfterMinor: number,
    bookedAt: string,
  ): Promise<void> {
    setAccounts((prev) =>
      prev.map((item) =>
        mergeAccountBalanceUpdate(
          item,
          accountId,
          availableBalanceAfterMinor,
          bookedAt,
        ),
      ),
    );
    setSelectedAccount((prev) =>
      prev
        ? mergeAccountBalanceUpdate(
            prev,
            accountId,
            availableBalanceAfterMinor,
            bookedAt,
          )
        : prev,
    );

    try {
      const refreshed = await api.getAccount(accountId);
      setAccounts((prev) =>
        prev.map((item) => {
          if (item.accountId !== accountId) {
            return item;
          }
          const incomingTs = Date.parse(refreshed.balanceUpdatedAt);
          const currentTs = Date.parse(bookedAt);
          if (!Number.isNaN(incomingTs) && !Number.isNaN(currentTs) && incomingTs < currentTs) {
            return item;
          }
          return refreshed;
        }),
      );
      setSelectedAccount((prev) => {
        if (!prev || prev.accountId !== accountId) {
          return prev;
        }
        const incomingTs = Date.parse(refreshed.balanceUpdatedAt);
        const currentTs = Date.parse(bookedAt);
        if (!Number.isNaN(incomingTs) && !Number.isNaN(currentTs) && incomingTs < currentTs) {
          return prev;
        }
        return refreshed;
      });
    } catch {
      // 오프라인 목업 또는 일시 지연 시 응답 잔액 유지
    }
  }

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runAction(
      "로그인",
      async () => {
        const result = await api.login(loginForm);
        applyLoginResult(result);
      },
      { domain: BUSY_DOMAIN_AUTH, requireAuthCheckOn401: false },
    );
  }

  async function handleTotpChallenge(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!challenge?.challengeId) {
      setAlert({ type: "error", text: "인증 challenge가 없습니다." });
      return;
    }
    await runAction(
      "TOTP 인증",
      async () => {
        const result = await api.verifyTotpChallenge({
          challengeId: challenge.challengeId as string,
          totpCode: totpChallengeForm.totpCode,
          rememberDevice: totpChallengeForm.rememberDevice,
        });
        applyLoginResult(result);
      },
      { domain: BUSY_DOMAIN_AUTH, requireAuthCheckOn401: false },
    );
  }

  async function handleBackupChallenge(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!challenge?.challengeId) {
      setAlert({ type: "error", text: "인증 challenge가 없습니다." });
      return;
    }
    await runAction(
      "복구 코드 인증",
      async () => {
        const result = await api.verifyBackupCodeChallenge({
          challengeId: challenge.challengeId as string,
          backupCode: backupChallengeForm.backupCode,
          rememberDevice: backupChallengeForm.rememberDevice,
        });
        applyLoginResult(result);
      },
      { domain: BUSY_DOMAIN_AUTH, requireAuthCheckOn401: false },
    );
  }

  async function handleRefresh() {
    if (!requireSession()) {
      return;
    }
    await runAction(
      "세션 갱신",
      async () => {
        const result = await api.refresh();
        applyLoginResult(result);
      },
      { domain: BUSY_DOMAIN_AUTH },
    );
  }

  async function handleLogout() {
    if (!requireSession()) {
      return;
    }
    await runAction(
      "로그아웃",
      async () => {
        await api.logout();
        resetAllAuthenticatedState({
          type: "success",
          text: "로그아웃되었습니다.",
        });
      },
      { domain: BUSY_DOMAIN_AUTH },
    );
  }

  async function handlePasswordRecoveryRequest(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runAction(
      "비밀번호 찾기 요청",
      async () => {
        const result = await api.requestPasswordRecovery({
          loginId: passwordRecoveryForm.loginId,
        });
        setPasswordRecoveryResult(result);
        setAlert({
          type: "success",
          text: "비밀번호 복구 요청이 접수되었습니다.",
        });
      },
      { domain: BUSY_DOMAIN_AUTH, requireAuthCheckOn401: false },
    );
  }

  async function handlePasswordRecoveryConfirm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runAction(
      "비밀번호 복구 확정",
      async () => {
        await api.confirmPasswordRecovery({
          recoveryToken: passwordRecoveryForm.recoveryToken,
          newPassword: passwordRecoveryForm.newPassword,
        });
        setAlert({ type: "success", text: "비밀번호가 변경되었습니다." });
      },
      { domain: BUSY_DOMAIN_AUTH, requireAuthCheckOn401: false },
    );
  }

  async function handlePasswordReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!requireSession()) {
      return;
    }
    await runAction(
      "비밀번호 변경",
      async () => {
        await api.resetPassword(passwordResetForm);
        resetAllAuthenticatedState({
          type: "success",
          text: "비밀번호가 변경되었습니다. 다시 로그인하세요.",
        });
      },
      { domain: BUSY_DOMAIN_AUTH },
    );
  }

  async function handleLoadSessions() {
    if (!requireSession()) {
      return;
    }
    await runAction(
      "세션 조회",
      async ({ signal, isCurrent }) => {
        const result = await api.getSessions(20, { signal });
        if (!isCurrent()) {
          return;
        }
        setSessions(result.items);
        setAlert({ type: "success", text: "세션 목록을 불러왔습니다." });
      },
      { domain: BUSY_DOMAIN_AUTH, abortPrevious: true },
    );
  }

  async function handleRevokeSession(sessionId: number) {
    if (!requireSession()) {
      return;
    }
    await runAction(
      "세션 해지",
      async () => {
        await api.revokeSession(sessionId);
        setSessions((items) => items.filter((item) => item.sessionId !== sessionId));
        setAlert({ type: "success", text: "선택한 세션을 해지했습니다." });
      },
      { domain: BUSY_DOMAIN_AUTH },
    );
  }

  async function handleRevokeAllSessions() {
    if (!requireSession()) {
      return;
    }
    await runAction(
      "전체 세션 해지",
      async () => {
        await api.revokeAllSessions();
        resetAllAuthenticatedState({
          type: "success",
          text: "전체 세션을 해지했습니다. 다시 로그인하세요.",
        });
      },
      { domain: BUSY_DOMAIN_AUTH },
    );
  }

  async function handleStartTotpEnrollment() {
    if (!requireSession()) {
      return;
    }
    await runAction(
      "TOTP 등록 시작",
      async () => {
        const result = await api.startTotpEnrollment();
        setTotpEnrollment(result);
        setAlert({ type: "success", text: "TOTP 등록 정보가 발급되었습니다." });
      },
      { domain: BUSY_DOMAIN_AUTH },
    );
  }

  async function handleVerifyTotpEnrollment() {
    if (!requireSession()) {
      return;
    }
    await runAction(
      "TOTP 등록 확인",
      async () => {
        const result = await api.verifyTotpEnrollment({ totpCode });
        setAlert({ type: "success", text: `TOTP 상태: ${result.status}` });
      },
      { domain: BUSY_DOMAIN_AUTH },
    );
  }

  async function handleDisableTotp() {
    if (!requireSession()) {
      return;
    }
    await runAction(
      "TOTP 해지",
      async () => {
        await api.disableTotp({ totpCode });
        resetAllAuthenticatedState({
          type: "success",
          text: "TOTP가 해지되었습니다. 보안상 다시 로그인하세요.",
        });
      },
      { domain: BUSY_DOMAIN_AUTH },
    );
  }

  async function handleIssueBackupCodes() {
    if (!requireSession()) {
      return;
    }
    await runAction(
      "복구 코드 발급",
      async () => {
        const result = await api.issueBackupCodes({ totpCode });
        setBackupCodes(result);
        setAlert({ type: "success", text: "복구 코드가 발급되었습니다." });
      },
      { domain: BUSY_DOMAIN_AUTH },
    );
  }

  async function handleLoadAccounts(cursor = "", append = false) {
    if (!requireSession()) {
      return;
    }
    await runAction(
      "계좌 조회",
      async ({ signal, isCurrent }) => {
        const result = await api.getAccounts(
          { limit: accountLimit, cursor },
          { signal },
        );
        if (!isCurrent()) {
          return;
        }
        setAccounts((items) => (append ? [...items, ...result.items] : result.items));
        setAccountCursor(result.nextCursor ?? "");
        if (result.items[0] && !selectedAccount) {
          const defaultId = String(result.items[0].accountId);
          setSelectedAccount(result.items[0]);
          setTransferForm((form) =>
            form.sourceAccountId ? form : { ...form, sourceAccountId: defaultId },
          );
          setReversalForm((form) =>
            form.sourceAccountId ? form : { ...form, sourceAccountId: defaultId },
          );
          setTransactionFilters((form) =>
            form.accountId ? form : { ...form, accountId: defaultId },
          );
        }
        setAlert({ type: "success", text: "계좌 목록을 불러왔습니다." });
      },
      { domain: BUSY_DOMAIN_ACCOUNTS, abortPrevious: true },
    );
  }

  async function handleLoadAccountDetail(accountId: number) {
    if (!requireSession()) {
      return;
    }
    await runAction(
      "계좌 상세 조회",
      async ({ signal, isCurrent }) => {
        const result = await api.getAccount(accountId, { signal });
        if (!isCurrent()) {
          return;
        }
        setSelectedAccount(result);
        setAccounts((items) =>
          items.map((item) =>
            item.accountId === result.accountId ? result : item,
          ),
        );
        setTransferForm((form) => ({
          ...form,
          sourceAccountId: String(result.accountId),
        }));
        setReversalForm((form) =>
          form.sourceAccountId
            ? form
            : { ...form, sourceAccountId: String(result.accountId) },
        );
        setTransactionFilters((form) => ({
          ...form,
          accountId: String(result.accountId),
        }));
        setAlert({ type: "success", text: "계좌 상세를 불러왔습니다." });
      },
      { domain: BUSY_DOMAIN_ACCOUNTS, abortPrevious: true },
    );
  }

  async function handleTransfer(
    event: FormEvent<HTMLFormElement>,
    submitTotpCode = "",
  ) {
    event.preventDefault();
    if (!requireSession()) {
      return false;
    }
    return runAction(
      "이체",
      async () => {
        const sourceAccountId = Number(transferForm.sourceAccountId);
        if (!Number.isSafeInteger(sourceAccountId) || sourceAccountId <= 0) {
          throw new Error("출금계좌를 선택하세요.");
        }
        const amountMinor = krwToMinor(transferForm.amountKrw);
        if (!transferIdempotencyKeyRef.current) {
          transferIdempotencyKeyRef.current = createIdempotencyKey("transfer");
        }
        const result = await api.transfer(
          {
            sourceAccountId,
            targetAccountNumber: transferForm.targetAccountNumber,
            amountMinor,
            currencyCode: transferForm.currencyCode,
            summary: transferForm.summary,
            totpCode: submitTotpCode || undefined,
          },
          transferIdempotencyKeyRef.current,
        );
        transferIdempotencyKeyRef.current = null;
        setTransferResult(result);
        await syncLinkedAccountStateAfterTransfer(
          result.sourceAccountId,
          result.availableBalanceAfterMinor,
          result.bookedAt,
        );
        setAlert({
          type: "success",
          text: `이체가 접수되었습니다. 거래번호 ${result.transactionReference}`,
        });
      },
      { domain: BUSY_DOMAIN_TRANSFER },
    );
  }

  function handleTransferFormChange(value: typeof transferForm): void {
    transferIdempotencyKeyRef.current = null;
    setTransferPreview(null);
    setTransferResult(null);
    setTransferForm(value);
  }

  function handleReversalFormChange(value: typeof reversalForm): void {
    reversalIdempotencyKeyRef.current = null;
    setReversalResult(null);
    setReversalForm(value);
  }

  async function handlePreviewTransfer(): Promise<boolean> {
    if (!requireSession()) {
      return false;
    }
    return runAction(
      "받는 사람 검증",
      async ({ signal, isCurrent }) => {
        const sourceAccountId = Number(transferForm.sourceAccountId);
        if (!Number.isSafeInteger(sourceAccountId) || sourceAccountId <= 0) {
          throw new Error("출금계좌를 선택하세요.");
        }
        const amountMinor = krwToMinor(transferForm.amountKrw);
        const result = await api.previewTransfer(
          {
            sourceAccountId,
            targetAccountNumber: transferForm.targetAccountNumber,
            amountMinor,
            currencyCode: transferForm.currencyCode,
          },
          { signal },
        );
        if (!isCurrent()) {
          return;
        }
        setTransferPreview(result);
        if (!result.allowed) {
          throw new Error(`이체 사전 검증 실패: ${result.blockedReason}`);
        }
        setAlert({
          type: "success",
          text: `받는 사람 검증 완료: ${result.targetAccount.displayName}`,
        });
      },
      { domain: BUSY_DOMAIN_TRANSFER, abortPrevious: true },
    );
  }

  async function handleReversal(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!requireSession()) {
      return false;
    }
    return runAction(
      "이체 취소",
      async () => {
        const sourceAccountId = Number(reversalForm.sourceAccountId);
        if (!Number.isSafeInteger(sourceAccountId) || sourceAccountId <= 0) {
          throw new Error("출금계좌를 선택하세요.");
        }
        const amountMinor = krwToMinor(reversalForm.amountKrw);
        if (!reversalIdempotencyKeyRef.current) {
          reversalIdempotencyKeyRef.current = createIdempotencyKey("reversal");
        }
        const result = await api.reverseTransfer(
          reversalForm.transactionReference.trim(),
          {
            sourceAccountId,
            amountMinor,
            reversalReason: reversalForm.reversalReason,
            summary: reversalForm.summary,
          },
          reversalIdempotencyKeyRef.current,
        );
        reversalIdempotencyKeyRef.current = null;
        setReversalResult(result);
        await syncLinkedAccountStateAfterTransfer(
          result.sourceAccountId,
          result.availableBalanceAfterMinor,
          result.bookedAt,
        );
        setAlert({
          type: "success",
          text: `취소 거래가 접수되었습니다. 거래번호 ${result.reversalTransactionReference}`,
        });
      },
      { domain: BUSY_DOMAIN_TRANSFER },
    );
  }

  async function handleSubmitCustomerApplication(
    input: CustomerApplicationSubmitInput,
  ): Promise<boolean> {
    if (!requireSession()) {
      return false;
    }
    return runAction(
      "업무 신청 접수",
      async () => {
        const signature = JSON.stringify([
          input.applicationType,
          input.accountId ?? "",
          input.payload,
        ]);
        if (
          !applicationIdempotencyKeyRef.current ||
          applicationSignatureRef.current !== signature
        ) {
          applicationSignatureRef.current = signature;
          applicationIdempotencyKeyRef.current =
            createIdempotencyKey("application");
        }
        const result = await api.submitCustomerApplication(
          {
            applicationType: input.applicationType,
            accountId: toOptionalNumber(input.accountId ?? ""),
            totpCode: input.totpCode,
            payload: input.payload,
          },
          applicationIdempotencyKeyRef.current,
        );
        applicationIdempotencyKeyRef.current = null;
        applicationSignatureRef.current = "";
        setCustomerApplicationResult(result);
        await refreshCustomerApplications();
        setAlert({
          type: "success",
          text: `${input.successMessage} 접수번호 ${result.applicationReference}`,
        });
      },
      { domain: BUSY_DOMAIN_APPLICATIONS },
    );
  }

  async function refreshCustomerApplications(signal?: AbortSignal): Promise<void> {
    const result = await api.listCustomerApplications(20, { signal });
    if (signal?.aborted) {
      return;
    }
    setCustomerApplications(result.items);
    if (selectedCustomerApplication) {
      const selected = result.items.find(
        (item) =>
          item.applicationReference ===
          selectedCustomerApplication.applicationReference,
      );
      setSelectedCustomerApplication(selected ?? selectedCustomerApplication);
    }
  }

  async function handleLoadCustomerApplications(): Promise<void> {
    if (!requireSession()) {
      return;
    }
    await runAction(
      "신청 목록 조회",
      async ({ signal, isCurrent }) => {
        await refreshCustomerApplications(signal);
        if (!isCurrent()) {
          return;
        }
        setAlert({ type: "success", text: "신청 목록을 불러왔습니다." });
      },
      { domain: BUSY_DOMAIN_APPLICATIONS, abortPrevious: true },
    );
  }

  async function handleSelectCustomerApplication(
    applicationReference: string,
  ): Promise<void> {
    if (!requireSession()) {
      return;
    }
    await runAction(
      "신청 상세 조회",
      async ({ signal, isCurrent }) => {
        const result = await api.getCustomerApplication(applicationReference, {
          signal,
        });
        if (!isCurrent()) {
          return;
        }
        setSelectedCustomerApplication(result);
        setAlert({ type: "success", text: "신청 상세를 불러왔습니다." });
      },
      { domain: BUSY_DOMAIN_APPLICATIONS, abortPrevious: true },
    );
  }

  async function handleCancelCustomerApplication(
    applicationReference: string,
  ): Promise<void> {
    if (!requireSession()) {
      return;
    }
    await runAction(
      "신청 취소",
      async () => {
        const result = await api.cancelCustomerApplication(applicationReference);
        setSelectedCustomerApplication(result);
        await refreshCustomerApplications();
        setAlert({ type: "success", text: "신청 취소 요청이 처리되었습니다." });
      },
      { domain: BUSY_DOMAIN_APPLICATIONS },
    );
  }

  async function handleSearchTransactions(
    mode = transactionMode,
    cursor = "",
    append = false,
  ) {
    if (!requireSession()) {
      return;
    }
    await runAction(
      "거래내역 조회",
      async ({ signal, isCurrent }) => {
        const accountId = Number(transactionFilters.accountId);
        if (!Number.isSafeInteger(accountId) || accountId <= 0) {
          throw new Error("조회할 계좌를 선택하세요.");
        }
        const limit = Number(transactionFilters.limit || 50);
        if (!Number.isSafeInteger(limit) || limit <= 0) {
          throw new Error("유효한 조회 건수를 입력하세요.");
        }
        const params = {
          accountId,
          from: toIsoDateTime(transactionFilters.from),
          to: toIsoDateTime(transactionFilters.to),
          limit,
          cursor,
          status: transactionFilters.status || undefined,
          direction: transactionFilters.direction || undefined,
          minAmountMinor: toOptionalMinorFromKrw(transactionFilters.minAmountKrw),
          maxAmountMinor: toOptionalMinorFromKrw(transactionFilters.maxAmountKrw),
          transactionReference:
            transactionFilters.transactionReference.trim() || undefined,
          responseShape: transactionFilters.responseShape,
        };
        const result =
          mode === "archive"
            ? await api.getArchivedTransactions(params, { signal })
            : await api.getTransactions(params, { signal });
        if (!isCurrent()) {
          return;
        }
        setTransactionMode(mode);
        setTransactionSlice(result);
        setTransactions((items) =>
          append ? [...items, ...result.items] : result.items,
        );
        setTransactionFilters((form) => ({
          ...form,
          cursor: result.nextCursor ?? "",
        }));
        setAlert({ type: "success", text: "거래내역을 불러왔습니다." });
      },
      { domain: BUSY_DOMAIN_TRANSACTIONS, abortPrevious: true },
    );
  }

  function handleResetTransactionFilters() {
    setTransactionFilters({
      ...buildInitialTransactionFilters(),
      accountId: selectedAccount
        ? String(selectedAccount.accountId)
        : accounts[0]
          ? String(accounts[0].accountId)
          : "",
    });
    setTransactionSlice(null);
    setTransactions([]);
    setTransactionDetail(null);
    setAlert({ type: "info", text: "거래내역 조회 조건을 초기화했습니다." });
  }

  async function handleLoadTransactionDetail(transactionReference: string) {
    if (!requireSession()) {
      return;
    }
    const accountId = Number(
      transactionFilters.accountId || selectedAccount?.accountId,
    );
    if (!Number.isSafeInteger(accountId) || accountId <= 0) {
      setAlert({ type: "error", text: "거래 상세 조회 계좌를 선택하세요." });
      return;
    }
    await runAction(
      "거래 상세 조회",
      async ({ signal, isCurrent }) => {
        const result = await api.getTransactionDetail(
          transactionReference,
          accountId,
          { signal },
        );
        if (!isCurrent()) {
          return;
        }
        setTransactionDetail(result);
        setAlert({ type: "success", text: "거래 상세를 불러왔습니다." });
      },
      { domain: BUSY_DOMAIN_TRANSACTIONS, abortPrevious: true },
    );
  }

  async function loadNotificationsPage(
    mode: "inbox" | "search",
    cursor: string,
    append: boolean,
    navType: "reset" | "next" | "prev" | "append",
  ) {
    if (!requireSession()) {
      return;
    }
    await runAction(
      "알림 조회",
      async ({ signal, isCurrent }) => {
        const limit = Number(notificationFilters.limit || 20);
        if (!Number.isSafeInteger(limit) || limit <= 0) {
          throw new Error("유효한 알림 조회 건수를 입력하세요.");
        }
        const commonParams = {
          limit,
          cursor,
        };
        const result =
          mode === "search"
            ? await api.searchNotifications(
                {
                  ...commonParams,
                  readStatus: notificationFilters.readStatus,
                  eventType: notificationFilters.eventType || undefined,
                  from: toIsoDateTime(notificationFilters.from),
                  to: toIsoDateTime(notificationFilters.to),
                },
                { signal },
              )
            : await api.getNotifications(commonParams, { signal });
        if (!isCurrent()) {
          return;
        }
        for (let i = 0; i < result.items.length; i += 1) {
          recordSseNotificationId(result.items[i].notificationId);
        }
        if (navType === "reset") {
          setNotificationCursorStack([]);
          activeNotificationCursorRef.current = cursor;
        } else if (navType === "next") {
          setNotificationCursorStack((stack) => [
            ...stack,
            activeNotificationCursorRef.current,
          ]);
          activeNotificationCursorRef.current = cursor;
        } else if (navType === "prev") {
          setNotificationCursorStack((stack) => stack.slice(0, -1));
          activeNotificationCursorRef.current = cursor;
        }

        setNotificationMode(mode);
        setNotificationSlice(result);
        setNotifications((items) =>
          append ? [...items, ...result.items] : result.items,
        );
        setNotificationFilters((form) => ({
          ...form,
          cursor: result.nextCursor ?? "",
        }));
        setSelectedNotificationIds(new Set());
        setAlert({ type: "success", text: "알림을 불러왔습니다." });
      },
      { domain: BUSY_DOMAIN_NOTIFICATIONS, abortPrevious: true },
    );
  }

  async function handleLoadNotifications(
    mode: NotificationViewMode = notificationMode,
    cursor = "",
    append = false,
  ) {
    if (mode === "preferences") {
      setNotificationMode("preferences");
      await handleLoadPreferences();
      return;
    }
    const navType = append
      ? "append"
      : cursor
        ? "next"
        : "reset";
    await loadNotificationsPage(mode, cursor, append, navType);
  }

  async function handlePrevNotifications() {
    if (notificationMode === "preferences" || notificationCursorStack.length === 0) {
      return;
    }
    const prevCursor =
      notificationCursorStack[notificationCursorStack.length - 1] ?? "";
    await loadNotificationsPage(notificationMode, prevCursor, false, "prev");
  }

  async function handleFirstNotifications() {
    if (notificationMode === "preferences") {
      return;
    }
    await loadNotificationsPage(notificationMode, "", false, "reset");
  }

  async function handleLoadUnreadCount() {
    if (!requireSession()) {
      return;
    }
    await runAction(
      "미확인 알림 조회",
      async ({ signal, isCurrent }) => {
        const result = await api.getUnreadCount({ signal });
        if (!isCurrent()) {
          return;
        }
        setUnreadCount(result.unreadCount);
        setAlert({ type: "success", text: "미확인 알림 수를 불러왔습니다." });
      },
      { domain: BUSY_DOMAIN_NOTIFICATIONS, abortPrevious: true },
    );
  }

  async function handleLoadPreferences() {
    if (!requireSession()) {
      return;
    }
    await runAction(
      "알림 설정 조회",
      async ({ signal, isCurrent }) => {
        const result = await api.getNotificationPreferences({ signal });
        if (!isCurrent()) {
          return;
        }
        setPreferences(result.items);
        setAlert({ type: "success", text: "알림 설정을 불러왔습니다." });
      },
      { domain: BUSY_DOMAIN_NOTIFICATIONS, abortPrevious: true },
    );
  }

  async function handleUpdatePreferences() {
    if (!requireSession()) {
      return;
    }
    await runAction(
      "알림 설정 저장",
      async () => {
        await api.updateNotificationPreferences({ items: preferences });
        setAlert({ type: "success", text: "알림 설정을 저장했습니다." });
      },
      { domain: BUSY_DOMAIN_NOTIFICATIONS },
    );
  }

  function toggleNotificationId(notificationId: number): void {
    setSelectedNotificationIds((prev) => {
      const next = new Set(prev);
      if (next.has(notificationId)) {
        next.delete(notificationId);
      } else {
        next.add(notificationId);
      }
      return next;
    });
  }

  async function handleNotificationBulkAction(
    action: "read" | "archive" | "delete",
    notificationId?: number,
  ) {
    if (!requireSession()) {
      return;
    }
    const targetIds =
      notificationId === undefined
        ? Array.from(selectedNotificationIds)
        : [notificationId];
    if (targetIds.length === 0) {
      setAlert({ type: "error", text: "선택된 알림이 없습니다." });
      return;
    }
    await runAction(
      "알림 처리",
      async () => {
        if (action === "read" && notificationId !== undefined) {
          await api.markNotificationAsRead(notificationId);
        } else if (action === "read") {
          await api.markNotificationsAsRead({ notificationIds: targetIds });
        } else if (action === "archive") {
          await api.archiveNotifications({ notificationIds: targetIds });
        } else {
          await api.deleteNotifications({ notificationIds: targetIds });
        }

        const targetSet = new Set(targetIds);
        let unreadDelta = 0;
        for (let i = 0; i < notifications.length; i += 1) {
          const item = notifications[i];
          if (targetSet.has(item.notificationId) && !item.read) {
            unreadDelta += 1;
          }
        }
        if (unreadDelta > 0) {
          setUnreadCount((count) =>
            count === null ? null : Math.max(0, count - unreadDelta),
          );
        }

        setNotifications((items) =>
          action === "delete" || action === "archive"
            ? items.filter((item) => !targetSet.has(item.notificationId))
            : items.map((item) =>
                targetSet.has(item.notificationId)
                  ? { ...item, read: true, readAt: new Date().toISOString() }
                  : item,
              ),
        );
        setSelectedNotificationIds(new Set());
        setAlert({ type: "success", text: "알림 처리가 완료되었습니다." });
      },
      { domain: BUSY_DOMAIN_NOTIFICATIONS },
    );
  }

  function handleConnectNotifications() {
    if (!requireSession()) {
      return;
    }
    try {
      eventSourceRef.current?.close();
      const eventSource = new EventSource(
        api.streamUrl("/api/v1/notifications/stream"),
        { withCredentials: true },
      );
      eventSourceRef.current = eventSource;
      setSseStatus({
        state: "CONNECTING",
        lastEventAt: "",
        lastEventId: "",
      });

      const markConnectedEvent = (lastEventId?: string) => {
        setSseStatus((status) => ({
          state: "CONNECTED",
          lastEventAt: new Date().toISOString(),
          lastEventId: lastEventId || status.lastEventId,
        }));
      };

      const processNotificationEvent = (rawData: string, lastEventId: string) => {
        const incoming = parseSseNotificationPayload(rawData);
        markConnectedEvent(lastEventId);
        if (!incoming) {
          return;
        }
        if (!recordSseNotificationId(incoming.notificationId)) {
          return;
        }
        setNotifications((items) => insertNotificationMonotonic(items, incoming));
        if (!incoming.read) {
          setUnreadCount((count) => (count === null ? 1 : count + 1));
        }
      };

      eventSource.onopen = () => {
        markConnectedEvent();
        setAlert({ type: "success", text: "알림 스트림에 연결되었습니다." });
      };

      eventSource.addEventListener("connected", (event) => {
        const msg = event as MessageEvent<string>;
        markConnectedEvent(msg.lastEventId);
      });

      eventSource.addEventListener("heartbeat", (event) => {
        const msg = event as MessageEvent<string>;
        markConnectedEvent(msg.lastEventId);
      });

      eventSource.addEventListener("notification", (event) => {
        const msg = event as MessageEvent<string>;
        processNotificationEvent(msg.data, msg.lastEventId);
      });

      eventSource.onmessage = (event) => {
        processNotificationEvent(event.data, event.lastEventId);
      };

      eventSource.onerror = () => {
        setSseStatus((status) => ({ ...status, state: "ERROR" }));
      };
    } catch (error) {
      setAlert({ type: "error", text: toErrorMessage(error) });
    }
  }

  function handleDisconnectNotifications() {
    eventSourceRef.current?.close();
    eventSourceRef.current = null;
    setSseStatus((status) => ({ ...status, state: "DISCONNECTED" }));
  }

  const isBusy = busyMask !== 0;

  return {
    activeSection,
    setActiveSection,
    session,
    authGeneration,
    alert,
    busyMask,
    busyLabel,
    loginForm,
    challenge,
    totpChallengeForm,
    backupChallengeForm,
    passwordRecoveryForm,
    passwordResetForm,
    totpCode,
    sessions,
    totpEnrollment,
    backupCodes,
    passwordRecoveryResult,
    accounts,
    accountCursor,
    accountLimit,
    selectedAccount,
    transferForm,
    transferResult,
    transferPreview,
    reversalForm,
    reversalResult,
    customerApplicationResult,
    customerApplications,
    selectedCustomerApplication,
    transactionMode,
    transactionFilters,
    transactionSlice,
    transactions,
    transactionDetail,
    notificationMode,
    notificationFilters,
    notificationSlice,
    notifications,
    selectedNotificationIds,
    unreadCount,
    preferences,
    sseStatus,
    hasPrevNotificationPage: notificationCursorStack.length > 0,
    handleLogin,
    handleTotpChallenge,
    handleBackupChallenge,
    handleRefresh,
    handleLogout,
    handlePasswordRecoveryRequest,
    handlePasswordRecoveryConfirm,
    handlePasswordReset,
    handleLoadSessions,
    handleRevokeSession,
    handleRevokeAllSessions,
    handleStartTotpEnrollment,
    handleVerifyTotpEnrollment,
    handleDisableTotp,
    handleIssueBackupCodes,
    handleLoadAccounts,
    handleLoadAccountDetail,
    handleTransfer,
    handlePreviewTransfer,
    handleReversal,
    handleSubmitCustomerApplication,
    handleLoadCustomerApplications,
    handleSelectCustomerApplication,
    handleCancelCustomerApplication,
    handleSearchTransactions,
    handleResetTransactionFilters,
    handleLoadTransactionDetail,
    handleLoadNotifications,
    handlePrevNotifications,
    handleFirstNotifications,
    handleLoadUnreadCount,
    handleLoadPreferences,
    handleUpdatePreferences,
    toggleNotificationId,
    handleNotificationBulkAction,
    handleConnectNotifications,
    handleDisconnectNotifications,
    isBusy,
    setLoginForm,
    setTotpChallengeForm,
    setBackupChallengeForm,
    setPasswordRecoveryForm,
    setPasswordResetForm,
    setTotpCode,
    setAccountLimit,
    setTransferForm: handleTransferFormChange,
    setReversalForm: handleReversalFormChange,
    setTransactionMode,
    setTransactionFilters,
    setNotificationMode,
    setNotificationFilters,
    setPreferences,
  };
}
