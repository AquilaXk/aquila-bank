import type { FormEvent } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { AquilaBankApiClient } from '@/lib/api/client';
import { clearCustomerSession, toCustomerSession } from '@/lib/api/session';
import type { AccountItem, AccountSummaryResponse, AuthSessionItem, BackupCodeIssueResponse, CustomerApplicationResponse, CustomerSession, LoginResponse, NotificationItem, NotificationPreferenceItem, NotificationQueryResponse, PasswordRecoveryRequestResult, TransactionDetailResponse, TransactionItem, TransactionQueryResponse, TotpEnrollmentStartResponse, TransferPreviewResponse, TransferResponse, TransferReversalResponse } from '@/lib/api/types';
import { formatMinorAmount, toErrorMessage, toIsoDateTime, toLocalInputValue, toOptionalNumber } from '@/lib/customer-banking/format';
import type { AlertMessage, CustomerApplicationSubmitInput, MenuSection } from '@/lib/customer-banking/types';

export function useCustomerBanking() {
  const api = useMemo(() => new AquilaBankApiClient(), []);
  const [activeSection, setActiveSection] = useState<MenuSection>("dashboard");
  const [session, setSession] = useState<CustomerSession | null>(null);
  const [alert, setAlert] = useState<AlertMessage>({
    type: "info",
    text: "안전한 웹뱅킹 이용을 위해 로그인 후 업무를 진행하세요.",
  });
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
    amountMinor: "",
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
    amountMinor: "",
    reversalReason: "CUSTOMER_REQUEST",
    summary: "",
  });
  const [reversalResult, setReversalResult] =
    useState<TransferReversalResponse | null>(null);
  const [customerApplicationResult, setCustomerApplicationResult] =
    useState<CustomerApplicationResponse | null>(null);
  const [transactionMode, setTransactionMode] = useState<"active" | "archive">(
    "active",
  );
  const initialTransactionFilters = useMemo(() => ({
    accountId: "",
    from: toLocalInputValue(new Date(Date.now() - 7 * 24 * 60 * 60 * 1000)),
    to: toLocalInputValue(new Date()),
    limit: "50",
    cursor: "",
    status: "",
    direction: "",
    minAmountMinor: "",
    maxAmountMinor: "",
    transactionReference: "",
    responseShape: "full" as "full" | "slim",
  }), []);
  const [transactionFilters, setTransactionFilters] = useState({
    ...initialTransactionFilters,
  });
  const [transactionSlice, setTransactionSlice] =
    useState<TransactionQueryResponse | null>(null);
  const [transactions, setTransactions] = useState<TransactionItem[]>([]);
  const [transactionDetail, setTransactionDetail] =
    useState<TransactionDetailResponse | null>(null);
  const [notificationMode, setNotificationMode] = useState<"inbox" | "search">(
    "inbox",
  );
  const [notificationFilters, setNotificationFilters] = useState({
    limit: "20",
    cursor: "",
    readStatus: "ALL",
    eventType: "",
    from: toLocalInputValue(new Date(Date.now() - 7 * 24 * 60 * 60 * 1000)),
    to: toLocalInputValue(new Date()),
  });
  const [notificationSlice, setNotificationSlice] =
    useState<NotificationQueryResponse | null>(null);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [selectedNotificationIds, setSelectedNotificationIds] = useState<number[]>(
    [],
  );
  const [unreadCount, setUnreadCount] = useState<number | null>(null);
  const [preferences, setPreferences] = useState<NotificationPreferenceItem[]>([]);
  const [sseStatus, setSseStatus] = useState({
    state: "disconnected",
    lastEventAt: "",
    lastEventId: "",
  });
  const eventSourceRef = useRef<EventSource | null>(null);

  useEffect(() => {
    return () => {
      eventSourceRef.current?.close();
    };
  }, []);

  function applyLoginResult(result: LoginResponse): void {
    if (result.status === "MFA_REQUIRED") {
      setChallenge(result);
      setAlert({
        type: "info",
        text: "추가 인증이 필요합니다. TOTP 또는 backup code를 입력하세요.",
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
  }

  async function runAction(label: string, action: () => Promise<void>): Promise<boolean> {
    setBusyLabel(label);
    try {
      await action();
      return true;
    } catch (error) {
      setAlert({ type: "error", text: toErrorMessage(error) });
      return false;
    } finally {
      setBusyLabel(null);
    }
  }

  function requireSession(): CustomerSession | null {
    if (!session) {
      setAlert({ type: "error", text: "로그인이 필요한 업무입니다." });
      return null;
    }
    return session;
  }

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runAction("로그인", async () => {
      const result = await api.login(loginForm);
      applyLoginResult(result);
    });
  }

  async function handleTotpChallenge(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!challenge?.challengeId) {
      setAlert({ type: "error", text: "인증 challenge가 없습니다." });
      return;
    }
    await runAction("TOTP 인증", async () => {
      const result = await api.verifyTotpChallenge({
        challengeId: challenge.challengeId as string,
        totpCode: totpChallengeForm.totpCode,
        rememberDevice: totpChallengeForm.rememberDevice,
      });
      applyLoginResult(result);
    });
  }

  async function handleBackupChallenge(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!challenge?.challengeId) {
      setAlert({ type: "error", text: "인증 challenge가 없습니다." });
      return;
    }
    await runAction("Backup code 인증", async () => {
      const result = await api.verifyBackupCodeChallenge({
        challengeId: challenge.challengeId as string,
        backupCode: backupChallengeForm.backupCode,
        rememberDevice: backupChallengeForm.rememberDevice,
      });
      applyLoginResult(result);
    });
  }

  async function handleRefresh() {
    if (!requireSession()) {
      return;
    }
    await runAction("토큰 재발급", async () => {
      const result = await api.refresh();
      applyLoginResult(result);
    });
  }

  async function handleLogout() {
    if (!requireSession()) {
      return;
    }
    await runAction("로그아웃", async () => {
      await api.logout();
      clearCustomerSession();
      setSession(null);
      setSessions([]);
      setAlert({ type: "success", text: "로그아웃되었습니다." });
    });
  }

  async function handlePasswordRecoveryRequest(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runAction("비밀번호 찾기 요청", async () => {
      const result = await api.requestPasswordRecovery({
        loginId: passwordRecoveryForm.loginId,
      });
      setPasswordRecoveryResult(result);
      setAlert({
        type: "success",
        text: "비밀번호 복구 요청이 접수되었습니다.",
      });
    });
  }

  async function handlePasswordRecoveryConfirm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await runAction("비밀번호 복구 확정", async () => {
      await api.confirmPasswordRecovery({
        recoveryToken: passwordRecoveryForm.recoveryToken,
        newPassword: passwordRecoveryForm.newPassword,
      });
      setAlert({ type: "success", text: "비밀번호가 변경되었습니다." });
    });
  }

  async function handlePasswordReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!requireSession()) {
      return;
    }
    await runAction("비밀번호 변경", async () => {
      await api.resetPassword(passwordResetForm);
      clearCustomerSession();
      setSession(null);
      setAlert({
        type: "success",
        text: "비밀번호가 변경되었습니다. 다시 로그인하세요.",
      });
    });
  }

  async function handleLoadSessions() {
    if (!requireSession()) {
      return;
    }
    await runAction("세션 조회", async () => {
      const result = await api.getSessions();
      setSessions(result.items);
      setAlert({ type: "success", text: "세션 목록을 불러왔습니다." });
    });
  }

  async function handleRevokeSession(sessionId: number) {
    if (!requireSession()) {
      return;
    }
    await runAction("세션 해지", async () => {
      await api.revokeSession(sessionId);
      setSessions((items) => items.filter((item) => item.sessionId !== sessionId));
      setAlert({ type: "success", text: "선택한 세션을 해지했습니다." });
    });
  }

  async function handleRevokeAllSessions() {
    if (!requireSession()) {
      return;
    }
    await runAction("전체 세션 해지", async () => {
      await api.revokeAllSessions();
      clearCustomerSession();
      setSession(null);
      setSessions([]);
      setAlert({ type: "success", text: "전체 세션을 해지했습니다." });
    });
  }

  async function handleStartTotpEnrollment() {
    if (!requireSession()) {
      return;
    }
    await runAction("TOTP 등록 시작", async () => {
      const result = await api.startTotpEnrollment();
      setTotpEnrollment(result);
      setAlert({ type: "success", text: "TOTP 등록 정보가 발급되었습니다." });
    });
  }

  async function handleVerifyTotpEnrollment() {
    if (!requireSession()) {
      return;
    }
    await runAction("TOTP 등록 확인", async () => {
      const result = await api.verifyTotpEnrollment({ totpCode });
      setAlert({ type: "success", text: `TOTP 상태: ${result.status}` });
    });
  }

  async function handleDisableTotp() {
    if (!requireSession()) {
      return;
    }
    await runAction("TOTP 해지", async () => {
      await api.disableTotp({ totpCode });
      clearCustomerSession();
      setSession(null);
      setAlert({ type: "success", text: "TOTP가 해지되었습니다." });
    });
  }

  async function handleIssueBackupCodes() {
    if (!requireSession()) {
      return;
    }
    await runAction("Backup code 발급", async () => {
      const result = await api.issueBackupCodes({ totpCode });
      setBackupCodes(result);
      setAlert({ type: "success", text: "Backup code가 발급되었습니다." });
    });
  }

  async function handleLoadAccounts(cursor = "", append = false) {
    if (!requireSession()) {
      return;
    }
    await runAction("계좌 조회", async () => {
      const result = await api.getAccounts({
        limit: accountLimit,
        cursor,
      });
      setAccounts((items) => (append ? [...items, ...result.items] : result.items));
      setAccountCursor(result.nextCursor ?? "");
      if (result.items[0] && !selectedAccount) {
        setSelectedAccount(result.items[0]);
        setTransferForm((form) => ({
          ...form,
          sourceAccountId: String(result.items[0].accountId),
        }));
        setTransactionFilters((form) => ({
          ...form,
          accountId: String(result.items[0].accountId),
        }));
      }
      setAlert({ type: "success", text: "계좌 목록을 불러왔습니다." });
    });
  }

  async function handleLoadAccountDetail(accountId: number) {
    if (!requireSession()) {
      return;
    }
    await runAction("계좌 상세 조회", async () => {
      const result = await api.getAccount(accountId);
      setSelectedAccount(result);
      setTransferForm((form) => ({
        ...form,
        sourceAccountId: String(result.accountId),
      }));
      setTransactionFilters((form) => ({
        ...form,
        accountId: String(result.accountId),
      }));
      setAlert({ type: "success", text: "계좌 상세를 불러왔습니다." });
    });
  }

  async function handleTransfer(event: FormEvent<HTMLFormElement>, totpCode = "") {
    event.preventDefault();
    if (!requireSession()) {
      return false;
    }
    return runAction("이체", async () => {
      const result = await api.transfer(
        {
          sourceAccountId: Number(transferForm.sourceAccountId),
          targetAccountNumber: transferForm.targetAccountNumber,
          amountMinor: Number(transferForm.amountMinor),
          currencyCode: transferForm.currencyCode,
          summary: transferForm.summary,
          totpCode: totpCode || undefined,
        },
      );
      setTransferResult(result);
      setAlert({
        type: "success",
        text: `이체가 접수되었습니다. 거래번호 ${result.transactionReference}`,
      });
    });
  }

  function handleTransferFormChange(value: typeof transferForm): void {
    setTransferPreview(null);
    setTransferResult(null);
    setTransferForm(value);
  }

  async function handlePreviewTransfer(): Promise<boolean> {
    if (!requireSession()) {
      return false;
    }
    return runAction("받는 사람 검증", async () => {
      const result = await api.previewTransfer({
        sourceAccountId: Number(transferForm.sourceAccountId),
        targetAccountNumber: transferForm.targetAccountNumber,
        amountMinor: Number(transferForm.amountMinor),
        currencyCode: transferForm.currencyCode,
      });
      setTransferPreview(result);
      if (!result.allowed) {
        throw new Error(`이체 사전 검증 실패: ${result.blockedReason}`);
      }
      setAlert({
        type: "success",
        text: `받는 사람 검증 완료: ${result.targetAccount.displayName}`,
      });
    });
  }

  async function handleReversal(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!requireSession()) {
      return false;
    }
    return runAction("이체 취소", async () => {
      const result = await api.reverseTransfer(
        reversalForm.transactionReference,
        {
          sourceAccountId: Number(reversalForm.sourceAccountId),
          amountMinor: Number(reversalForm.amountMinor),
          reversalReason: reversalForm.reversalReason,
          summary: reversalForm.summary,
        },
      );
      setReversalResult(result);
      setAlert({
        type: "success",
        text: `취소 거래가 접수되었습니다. 거래번호 ${result.reversalTransactionReference}`,
      });
    });
  }

  async function handleSubmitCustomerApplication(
    input: CustomerApplicationSubmitInput,
  ): Promise<boolean> {
    if (!requireSession()) {
      return false;
    }
    return runAction("업무 신청 접수", async () => {
      const result = await api.submitCustomerApplication({
        applicationType: input.applicationType,
        accountId: toOptionalNumber(input.accountId ?? ""),
        totpCode: input.totpCode,
        payload: input.payload,
      });
      setCustomerApplicationResult(result);
      setAlert({
        type: "success",
        text: `${input.successMessage} 접수번호 ${result.applicationReference}`,
      });
    });
  }

  async function handleSearchTransactions(
    mode = transactionMode,
    cursor = "",
    append = false,
  ) {
    if (!requireSession()) {
      return;
    }
    await runAction("거래내역 조회", async () => {
      const params = {
        accountId: Number(transactionFilters.accountId),
        from: toIsoDateTime(transactionFilters.from),
        to: toIsoDateTime(transactionFilters.to),
        limit: Number(transactionFilters.limit || 50),
        cursor,
        status: transactionFilters.status || undefined,
        direction: transactionFilters.direction || undefined,
        minAmountMinor: toOptionalNumber(transactionFilters.minAmountMinor),
        maxAmountMinor: toOptionalNumber(transactionFilters.maxAmountMinor),
        transactionReference: transactionFilters.transactionReference || undefined,
        responseShape: transactionFilters.responseShape,
      };
      const result =
        mode === "archive"
          ? await api.getArchivedTransactions(params)
          : await api.getTransactions(params);
      setTransactionMode(mode);
      setTransactionSlice(result);
      setTransactions((items) => (append ? [...items, ...result.items] : result.items));
      setTransactionFilters((form) => ({
        ...form,
        cursor: result.nextCursor ?? "",
      }));
      setAlert({ type: "success", text: "거래내역을 불러왔습니다." });
    });
  }

  function handleResetTransactionFilters() {
    setTransactionFilters({
      ...initialTransactionFilters,
      accountId: selectedAccount ? String(selectedAccount.accountId) : "",
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
    const accountId = Number(transactionFilters.accountId || selectedAccount?.accountId);
    if (!accountId) {
      setAlert({ type: "error", text: "거래 상세 조회 계좌를 선택하세요." });
      return;
    }
    await runAction("거래 상세 조회", async () => {
      const result = await api.getTransactionDetail(
        transactionReference,
        accountId,
      );
      setTransactionDetail(result);
      setAlert({ type: "success", text: "거래 상세를 불러왔습니다." });
    });
  }

  async function handleLoadNotifications(
    mode = notificationMode,
    cursor = "",
    append = false,
  ) {
    if (!requireSession()) {
      return;
    }
    await runAction("알림 조회", async () => {
      const commonParams = {
        limit: Number(notificationFilters.limit || 20),
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
            )
          : await api.getNotifications(commonParams);
      setNotificationMode(mode);
      setNotificationSlice(result);
      setNotifications((items) => (append ? [...items, ...result.items] : result.items));
      setNotificationFilters((form) => ({
        ...form,
        cursor: result.nextCursor ?? "",
      }));
      setSelectedNotificationIds([]);
      setAlert({ type: "success", text: "알림을 불러왔습니다." });
    });
  }

  async function handleLoadUnreadCount() {
    if (!requireSession()) {
      return;
    }
    await runAction("미확인 알림 조회", async () => {
      const result = await api.getUnreadCount();
      setUnreadCount(result.unreadCount);
      setAlert({ type: "success", text: "미확인 알림 수를 불러왔습니다." });
    });
  }

  async function handleLoadPreferences() {
    if (!requireSession()) {
      return;
    }
    await runAction("알림 설정 조회", async () => {
      const result = await api.getNotificationPreferences();
      setPreferences(result.items);
      setAlert({ type: "success", text: "알림 설정을 불러왔습니다." });
    });
  }

  async function handleUpdatePreferences() {
    if (!requireSession()) {
      return;
    }
    await runAction("알림 설정 저장", async () => {
      await api.updateNotificationPreferences({ items: preferences });
      setAlert({ type: "success", text: "알림 설정을 저장했습니다." });
    });
  }

  function toggleNotificationId(notificationId: number): void {
    setSelectedNotificationIds((items) =>
      items.includes(notificationId)
        ? items.filter((item) => item !== notificationId)
        : [...items, notificationId],
    );
  }

  async function handleNotificationBulkAction(
    action: "read" | "archive" | "delete",
    notificationId?: number,
  ) {
    if (!requireSession()) {
      return;
    }
    const notificationIds =
      notificationId === undefined ? selectedNotificationIds : [notificationId];
    if (notificationIds.length === 0) {
      setAlert({ type: "error", text: "선택된 알림이 없습니다." });
      return;
    }
    await runAction("알림 처리", async () => {
      if (action === "read" && notificationId !== undefined) {
        await api.markNotificationAsRead(notificationId);
      } else if (action === "read") {
        await api.markNotificationsAsRead({ notificationIds });
      } else if (action === "archive") {
        await api.archiveNotifications({ notificationIds });
      } else {
        await api.deleteNotifications({ notificationIds });
      }
      setNotifications((items) =>
        action === "delete" || action === "archive"
          ? items.filter((item) => !notificationIds.includes(item.notificationId))
          : items.map((item) =>
              notificationIds.includes(item.notificationId)
                ? { ...item, read: true, readAt: new Date().toISOString() }
                : item,
            ),
      );
      setSelectedNotificationIds([]);
      setAlert({ type: "success", text: "알림 처리가 완료되었습니다." });
    });
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
        state: "connecting",
        lastEventAt: "",
        lastEventId: "",
      });
      eventSource.onopen = () => {
        setSseStatus((status) => ({ ...status, state: "connected" }));
        setAlert({ type: "success", text: "알림 스트림에 연결되었습니다." });
      };
      eventSource.onmessage = (event) => {
        setSseStatus({
          state: "connected",
          lastEventAt: new Date().toISOString(),
          lastEventId: event.lastEventId || "",
        });
      };
      eventSource.onerror = () => {
        setSseStatus((status) => ({ ...status, state: "error" }));
      };
    } catch (error) {
      setAlert({ type: "error", text: toErrorMessage(error) });
    }
  }

  function handleDisconnectNotifications() {
    eventSourceRef.current?.close();
    eventSourceRef.current = null;
    setSseStatus((status) => ({ ...status, state: "disconnected" }));
  }

  const isBusy = busyLabel !== null;


  return {
    activeSection,
    setActiveSection,
    session,
    alert,
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
    handleSearchTransactions,
    handleResetTransactionFilters,
    handleLoadTransactionDetail,
    handleLoadNotifications,
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
    setReversalForm,
    setTransactionMode,
    setTransactionFilters,
    setNotificationMode,
    setNotificationFilters,
    setPreferences,
  };
}
