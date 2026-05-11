"use client";

import type { FormEvent } from "react";
import { useEffect, useMemo, useRef, useState } from "react";
import { AquilaBankApiClient } from "@/lib/api/client";
import {
  clearCustomerSession,
  loadCustomerSession,
  saveCustomerSession,
  toCustomerSession,
} from "@/lib/api/session";
import type {
  AccountItem,
  AccountSummaryResponse,
  AuthSessionItem,
  BackupCodeIssueResponse,
  CustomerSession,
  LoginResponse,
  NotificationItem,
  NotificationPreferenceItem,
  NotificationQueryResponse,
  PasswordRecoveryRequestResult,
  TransactionDetailResponse,
  TransactionItem,
  TransactionQueryResponse,
  TotpEnrollmentStartResponse,
  TransferResponse,
  TransferReversalResponse,
} from "@/lib/api/types";
import { mainMenus, quickMenus } from "@/lib/customer-banking/constants";
import {
  formatDateTime,
  formatMinorAmount,
  maskToken,
  toErrorMessage,
  toIsoDateTime,
  toLocalInputValue,
  toOptionalNumber,
} from "@/lib/customer-banking/format";
import type { AlertMessage, MenuSection } from "@/lib/customer-banking/types";

export default function HomePage() {
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
    targetAccountId: "",
    amountMinor: "",
    currencyCode: "KRW",
    summary: "",
  });
  const [transferResult, setTransferResult] = useState<TransferResponse | null>(
    null,
  );
  const [reversalForm, setReversalForm] = useState({
    transactionReference: "",
    sourceAccountId: "",
    amountMinor: "",
    reversalReason: "CUSTOMER_REQUEST",
    summary: "",
  });
  const [reversalResult, setReversalResult] =
    useState<TransferReversalResponse | null>(null);
  const [transactionMode, setTransactionMode] = useState<"active" | "archive">(
    "active",
  );
  const [transactionFilters, setTransactionFilters] = useState({
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
    const storedSession = loadCustomerSession();
    if (storedSession) {
      setSession(storedSession);
      setAlert({
        type: "success",
        text: "저장된 세션을 불러왔습니다.",
      });
    }
  }, []);

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
        text: "로그인 응답에 accessToken 또는 refreshToken이 없습니다.",
      });
      return;
    }

    saveCustomerSession(nextSession);
    setSession(nextSession);
    setChallenge(null);
    setAlert({
      type: "success",
      text: "로그인되었습니다.",
    });
  }

  async function runAction(label: string, action: () => Promise<void>): Promise<void> {
    setBusyLabel(label);
    try {
      await action();
    } catch (error) {
      setAlert({ type: "error", text: toErrorMessage(error) });
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
    const currentSession = requireSession();
    if (!currentSession) {
      return;
    }
    await runAction("토큰 재발급", async () => {
      const result = await api.refresh({ refreshToken: currentSession.refreshToken });
      applyLoginResult(result);
    });
  }

  async function handleLogout() {
    const currentSession = requireSession();
    if (!currentSession) {
      return;
    }
    await runAction("로그아웃", async () => {
      await api.logout(
        { refreshToken: currentSession.refreshToken },
        currentSession.accessToken,
      );
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
    const currentSession = requireSession();
    if (!currentSession) {
      return;
    }
    await runAction("비밀번호 변경", async () => {
      await api.resetPassword(passwordResetForm, currentSession.accessToken);
      clearCustomerSession();
      setSession(null);
      setAlert({
        type: "success",
        text: "비밀번호가 변경되었습니다. 다시 로그인하세요.",
      });
    });
  }

  async function handleLoadSessions() {
    const currentSession = requireSession();
    if (!currentSession) {
      return;
    }
    await runAction("세션 조회", async () => {
      const result = await api.getSessions(currentSession.accessToken);
      setSessions(result.items);
      setAlert({ type: "success", text: "세션 목록을 불러왔습니다." });
    });
  }

  async function handleRevokeSession(sessionId: number) {
    const currentSession = requireSession();
    if (!currentSession) {
      return;
    }
    await runAction("세션 해지", async () => {
      await api.revokeSession(sessionId, currentSession.accessToken);
      setSessions((items) => items.filter((item) => item.sessionId !== sessionId));
      setAlert({ type: "success", text: "선택한 세션을 해지했습니다." });
    });
  }

  async function handleRevokeAllSessions() {
    const currentSession = requireSession();
    if (!currentSession) {
      return;
    }
    await runAction("전체 세션 해지", async () => {
      await api.revokeAllSessions(currentSession.accessToken);
      clearCustomerSession();
      setSession(null);
      setSessions([]);
      setAlert({ type: "success", text: "전체 세션을 해지했습니다." });
    });
  }

  async function handleStartTotpEnrollment() {
    const currentSession = requireSession();
    if (!currentSession) {
      return;
    }
    await runAction("TOTP 등록 시작", async () => {
      const result = await api.startTotpEnrollment(currentSession.accessToken);
      setTotpEnrollment(result);
      setAlert({ type: "success", text: "TOTP 등록 정보가 발급되었습니다." });
    });
  }

  async function handleVerifyTotpEnrollment() {
    const currentSession = requireSession();
    if (!currentSession) {
      return;
    }
    await runAction("TOTP 등록 확인", async () => {
      const result = await api.verifyTotpEnrollment(
        { totpCode },
        currentSession.accessToken,
      );
      setAlert({ type: "success", text: `TOTP 상태: ${result.status}` });
    });
  }

  async function handleDisableTotp() {
    const currentSession = requireSession();
    if (!currentSession) {
      return;
    }
    await runAction("TOTP 해지", async () => {
      await api.disableTotp({ totpCode }, currentSession.accessToken);
      clearCustomerSession();
      setSession(null);
      setAlert({ type: "success", text: "TOTP가 해지되었습니다." });
    });
  }

  async function handleIssueBackupCodes() {
    const currentSession = requireSession();
    if (!currentSession) {
      return;
    }
    await runAction("Backup code 발급", async () => {
      const result = await api.issueBackupCodes(
        { totpCode },
        currentSession.accessToken,
      );
      setBackupCodes(result);
      setAlert({ type: "success", text: "Backup code가 발급되었습니다." });
    });
  }

  async function handleLoadAccounts(cursor = "", append = false) {
    const currentSession = requireSession();
    if (!currentSession) {
      return;
    }
    await runAction("계좌 조회", async () => {
      const result = await api.getAccounts(currentSession.accessToken, {
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
    const currentSession = requireSession();
    if (!currentSession) {
      return;
    }
    await runAction("계좌 상세 조회", async () => {
      const result = await api.getAccount(accountId, currentSession.accessToken);
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

  async function handleTransfer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const currentSession = requireSession();
    if (!currentSession) {
      return;
    }
    await runAction("이체", async () => {
      const result = await api.transfer(
        {
          sourceAccountId: Number(transferForm.sourceAccountId),
          targetAccountId: Number(transferForm.targetAccountId),
          amountMinor: Number(transferForm.amountMinor),
          currencyCode: transferForm.currencyCode,
          summary: transferForm.summary,
        },
        currentSession.accessToken,
      );
      setTransferResult(result);
      setAlert({
        type: "success",
        text: `이체가 접수되었습니다. 거래번호 ${result.transactionReference}`,
      });
    });
  }

  async function handleReversal(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const currentSession = requireSession();
    if (!currentSession) {
      return;
    }
    await runAction("이체 취소", async () => {
      const result = await api.reverseTransfer(
        reversalForm.transactionReference,
        {
          sourceAccountId: Number(reversalForm.sourceAccountId),
          amountMinor: Number(reversalForm.amountMinor),
          reversalReason: reversalForm.reversalReason,
          summary: reversalForm.summary,
        },
        currentSession.accessToken,
      );
      setReversalResult(result);
      setAlert({
        type: "success",
        text: `취소 거래가 접수되었습니다. 거래번호 ${result.reversalTransactionReference}`,
      });
    });
  }

  async function handleSearchTransactions(
    mode = transactionMode,
    cursor = "",
    append = false,
  ) {
    const currentSession = requireSession();
    if (!currentSession) {
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
          ? await api.getArchivedTransactions(params, currentSession.accessToken)
          : await api.getTransactions(params, currentSession.accessToken);
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

  async function handleLoadTransactionDetail(transactionReference: string) {
    const currentSession = requireSession();
    if (!currentSession) {
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
        currentSession.accessToken,
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
    const currentSession = requireSession();
    if (!currentSession) {
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
              currentSession.accessToken,
            )
          : await api.getNotifications(commonParams, currentSession.accessToken);
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
    const currentSession = requireSession();
    if (!currentSession) {
      return;
    }
    await runAction("미확인 알림 조회", async () => {
      const result = await api.getUnreadCount(currentSession.accessToken);
      setUnreadCount(result.unreadCount);
      setAlert({ type: "success", text: "미확인 알림 수를 불러왔습니다." });
    });
  }

  async function handleLoadPreferences() {
    const currentSession = requireSession();
    if (!currentSession) {
      return;
    }
    await runAction("알림 설정 조회", async () => {
      const result = await api.getNotificationPreferences(currentSession.accessToken);
      setPreferences(result.items);
      setAlert({ type: "success", text: "알림 설정을 불러왔습니다." });
    });
  }

  async function handleUpdatePreferences() {
    const currentSession = requireSession();
    if (!currentSession) {
      return;
    }
    await runAction("알림 설정 저장", async () => {
      await api.updateNotificationPreferences({ items: preferences }, currentSession.accessToken);
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
    const currentSession = requireSession();
    if (!currentSession) {
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
        await api.markNotificationAsRead(notificationId, currentSession.accessToken);
      } else if (action === "read") {
        await api.markNotificationsAsRead({ notificationIds }, currentSession.accessToken);
      } else if (action === "archive") {
        await api.archiveNotifications({ notificationIds }, currentSession.accessToken);
      } else {
        await api.deleteNotifications({ notificationIds }, currentSession.accessToken);
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
    const currentSession = requireSession();
    if (!currentSession) {
      return;
    }
    try {
      eventSourceRef.current?.close();
      const eventSource = new EventSource(
        api.streamUrl("/api/v1/notifications/stream", currentSession.accessToken),
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

  return (
    <main className="bank-shell">
      <header className="bank-header">
        <div className="utility-bar" aria-label="상단 유틸리티">
          <div className="utility-left">
            <button className="utility-link active" type="button">
              개인
            </button>
            <button className="utility-link" type="button">
              기업
            </button>
            <button className="utility-link" type="button">
              인증센터
            </button>
            <button className="utility-link" type="button">
              고객센터
            </button>
          </div>
          <div className="utility-right">
            <label className="search-field">
              <span>검색</span>
              <input aria-label="통합 검색" placeholder="업무명 또는 메뉴 검색" />
            </label>
          </div>
        </div>
        <div className="brand-row">
          <div className="brand-mark" aria-label="Aquila Bank">
            <span className="brand-symbol">A</span>
            <div>
              <strong>Aquila Bank</strong>
              <small>Personal Internet Banking</small>
            </div>
          </div>
          <nav className="primary-nav" aria-label="주요 메뉴">
            {mainMenus.map((item) => (
              <button
                className={activeSection === item.id ? "nav-tab active" : "nav-tab"}
                key={item.id}
                onClick={() => setActiveSection(item.id)}
                type="button"
              >
                {item.label}
              </button>
            ))}
          </nav>
        </div>
      </header>

      <div className="bank-layout">
        <aside className="side-menu" aria-label="개인뱅킹 메뉴">
          <div className="side-title">개인뱅킹</div>
          {mainMenus.map((item) => (
            <button
              className={activeSection === item.id ? "side-item active" : "side-item"}
              key={item.id}
              onClick={() => setActiveSection(item.id)}
              type="button"
            >
              <span>{item.group}</span>
              <strong>{item.label}</strong>
            </button>
          ))}
        </aside>

        <section className="work-area" aria-live="polite">
          <div className={`alert ${alert.type}`}>
            <strong>{alert.type === "error" ? "확인 필요" : "안내"}</strong>
            <span>{alert.text}</span>
          </div>
          {busyLabel ? (
            <div className="busy-strip" role="status">
              {busyLabel} 처리 중
            </div>
          ) : null}
          {activeSection === "dashboard" ? (
            <DashboardSection
              hasSession={Boolean(session)}
              onMove={setActiveSection}
              onRefresh={handleRefresh}
              isBusy={isBusy}
            />
          ) : null}
          {activeSection === "accounts" ? (
            <AccountsSection
              accountCursor={accountCursor}
              accountLimit={accountLimit}
              accounts={accounts}
              isBusy={isBusy}
              selectedAccount={selectedAccount}
              onAccountLimitChange={setAccountLimit}
              onLoadAccountDetail={handleLoadAccountDetail}
              onLoadAccounts={() => handleLoadAccounts()}
              onLoadNextAccounts={() => handleLoadAccounts(accountCursor, true)}
            />
          ) : null}
          {activeSection === "transfer" ? (
            <TransferSection
              isBusy={isBusy}
              reversalForm={reversalForm}
              reversalResult={reversalResult}
              transferForm={transferForm}
              transferResult={transferResult}
              onReversal={handleReversal}
              onReversalChange={setReversalForm}
              onTransfer={handleTransfer}
              onTransferChange={setTransferForm}
            />
          ) : null}
          {activeSection === "transactions" ? (
            <TransactionsSection
              filters={transactionFilters}
              isBusy={isBusy}
              mode={transactionMode}
              slice={transactionSlice}
              transactionDetail={transactionDetail}
              transactions={transactions}
              onDetail={handleLoadTransactionDetail}
              onFilterChange={setTransactionFilters}
              onModeChange={setTransactionMode}
              onNext={() =>
                transactionSlice?.nextCursor
                  ? handleSearchTransactions(
                      transactionMode,
                      transactionSlice.nextCursor,
                      true,
                    )
                  : undefined
              }
              onSearch={(event) => {
                event.preventDefault();
                handleSearchTransactions(transactionMode);
              }}
            />
          ) : null}
          {activeSection === "security" ? (
            <SecuritySection
              backupChallengeForm={backupChallengeForm}
              backupCodes={backupCodes}
              challenge={challenge}
              isBusy={isBusy}
              loginForm={loginForm}
              passwordRecoveryForm={passwordRecoveryForm}
              passwordRecoveryResult={passwordRecoveryResult}
              passwordResetForm={passwordResetForm}
              session={session}
              sessions={sessions}
              totpChallengeForm={totpChallengeForm}
              totpCode={totpCode}
              totpEnrollment={totpEnrollment}
              onBackupChallengeChange={setBackupChallengeForm}
              onBackupChallenge={handleBackupChallenge}
              onDisableTotp={handleDisableTotp}
              onIssueBackupCodes={handleIssueBackupCodes}
              onLoadSessions={handleLoadSessions}
              onLogin={handleLogin}
              onLoginChange={setLoginForm}
              onLogout={handleLogout}
              onPasswordRecoveryChange={setPasswordRecoveryForm}
              onPasswordRecoveryConfirm={handlePasswordRecoveryConfirm}
              onPasswordRecoveryRequest={handlePasswordRecoveryRequest}
              onPasswordReset={handlePasswordReset}
              onPasswordResetChange={setPasswordResetForm}
              onRefresh={handleRefresh}
              onRevokeAllSessions={handleRevokeAllSessions}
              onRevokeSession={handleRevokeSession}
              onStartTotpEnrollment={handleStartTotpEnrollment}
              onTotpChallenge={handleTotpChallenge}
              onTotpChallengeChange={setTotpChallengeForm}
              onTotpCodeChange={setTotpCode}
              onVerifyTotpEnrollment={handleVerifyTotpEnrollment}
            />
          ) : null}
          {activeSection === "notifications" ? (
            <NotificationsSection
              filters={notificationFilters}
              isBusy={isBusy}
              mode={notificationMode}
              notifications={notifications}
              preferences={preferences}
              selectedIds={selectedNotificationIds}
              slice={notificationSlice}
              sseStatus={sseStatus}
              unreadCount={unreadCount}
              onBulkAction={handleNotificationBulkAction}
              onConnect={handleConnectNotifications}
              onDisconnect={handleDisconnectNotifications}
              onFilterChange={setNotificationFilters}
              onLoadPreferences={handleLoadPreferences}
              onLoadUnreadCount={handleLoadUnreadCount}
              onModeChange={setNotificationMode}
              onNext={() =>
                notificationSlice?.nextCursor
                  ? handleLoadNotifications(
                      notificationMode,
                      notificationSlice.nextCursor,
                      true,
                    )
                  : undefined
              }
              onPreferenceChange={setPreferences}
              onSearch={(event) => {
                event.preventDefault();
                handleLoadNotifications(notificationMode);
              }}
              onToggleId={toggleNotificationId}
              onUpdatePreferences={handleUpdatePreferences}
            />
          ) : null}
        </section>

        <aside className="right-rail" aria-label="빠른 업무">
          <section className="rail-panel login-panel">
            <div className="rail-heading">
              <span>로그인 상태</span>
              <strong>{session ? "정상" : "미로그인"}</strong>
            </div>
            {session ? (
              <dl className="session-summary">
                <div>
                  <dt>User ID</dt>
                  <dd>{session.userId ?? "-"}</dd>
                </div>
                <div>
                  <dt>Access Token</dt>
                  <dd>{maskToken(session.accessToken)}</dd>
                </div>
                <div>
                  <dt>만료</dt>
                  <dd>{formatDateTime(session.expiresAt)}</dd>
                </div>
              </dl>
            ) : (
              <p className="rail-copy">인증 후 조회, 이체, 거래내역 업무를 이용할 수 있습니다.</p>
            )}
            <div className="button-row compact">
              <button onClick={() => setActiveSection("security")} type="button">
                인증센터
              </button>
              <button disabled={!session || isBusy} onClick={handleRefresh} type="button">
                재발급
              </button>
            </div>
          </section>

          <section className="rail-panel">
            <div className="rail-heading">
              <span>빠른메뉴</span>
            </div>
            <div className="quick-grid">
              {quickMenus.map((item) => (
                <button key={item} type="button">
                  {item}
                </button>
              ))}
            </div>
          </section>

          <section className="rail-panel notice-list">
            <div className="rail-heading">
              <span>보안안내</span>
            </div>
            <ul>
              <li>OTP 전체 번호 요구 시 즉시 거래를 중단하세요.</li>
              <li>타 계좌 거래 조회는 권한 확인 후 차단됩니다.</li>
              <li>대량 거래 조회는 기간과 계좌 기준으로 제한됩니다.</li>
            </ul>
          </section>
        </aside>
      </div>
    </main>
  );
}

function DashboardSection({
  hasSession,
  isBusy,
  onMove,
  onRefresh,
}: {
  hasSession: boolean;
  isBusy: boolean;
  onMove: (section: MenuSection) => void;
  onRefresh: () => void;
}) {
  return (
    <section className="task-section">
      <div className="section-title">
        <div>
          <p>개인뱅킹</p>
          <h1>뱅킹 업무</h1>
        </div>
        <div className="button-row">
          <button onClick={() => onMove("accounts")} type="button">
            계좌조회
          </button>
          <button onClick={() => onMove("transfer")} type="button">
            이체
          </button>
          <button disabled={!hasSession || isBusy} onClick={onRefresh} type="button">
            토큰 재발급
          </button>
        </div>
      </div>
      <div className="summary-grid">
        <article className="summary-box">
          <span>조회</span>
          <strong>계좌 목록/잔액</strong>
          <button onClick={() => onMove("accounts")} type="button">
            바로가기
          </button>
        </article>
        <article className="summary-box">
          <span>이체</span>
          <strong>즉시이체/취소</strong>
          <button onClick={() => onMove("transfer")} type="button">
            바로가기
          </button>
        </article>
        <article className="summary-box">
          <span>조회</span>
          <strong>거래내역</strong>
          <button onClick={() => onMove("transactions")} type="button">
            바로가기
          </button>
        </article>
        <article className="summary-box">
          <span>알림</span>
          <strong>알림함/설정</strong>
          <button onClick={() => onMove("notifications")} type="button">
            바로가기
          </button>
        </article>
      </div>
      <div className="bank-table-wrap">
        <table className="bank-table">
          <caption>업무별 처리 기준</caption>
          <thead>
            <tr>
              <th>업무</th>
              <th>기준</th>
              <th>상태</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>거래내역 조회</td>
              <td>계좌, 기간, limit, cursor 기반</td>
              <td>bounded query</td>
            </tr>
            <tr>
              <td>이체</td>
              <td>요청 단위 Idempotency-Key 발급</td>
              <td>중복 방지</td>
            </tr>
            <tr>
              <td>알림</td>
              <td>SSE 연결 상태와 inbox 동시 제공</td>
              <td>실시간</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  );
}

function AccountsSection({
  accountCursor,
  accountLimit,
  accounts,
  isBusy,
  selectedAccount,
  onAccountLimitChange,
  onLoadAccountDetail,
  onLoadAccounts,
  onLoadNextAccounts,
}: {
  accountCursor: string;
  accountLimit: number;
  accounts: AccountItem[];
  isBusy: boolean;
  selectedAccount: AccountSummaryResponse | null;
  onAccountLimitChange: (value: number) => void;
  onLoadAccountDetail: (accountId: number) => void;
  onLoadAccounts: () => void;
  onLoadNextAccounts: () => void;
}) {
  return (
    <section className="task-section">
      <div className="section-title">
        <div>
          <p>조회</p>
          <h1>계좌조회</h1>
        </div>
        <div className="button-row">
          <label className="inline-control">
            <span>건수</span>
            <select
              onChange={(event) => onAccountLimitChange(Number(event.target.value))}
              value={accountLimit}
            >
              <option value={10}>10</option>
              <option value={20}>20</option>
              <option value={50}>50</option>
            </select>
          </label>
          <button disabled={isBusy} onClick={onLoadAccounts} type="button">
            조회
          </button>
          <button disabled={!accountCursor || isBusy} onClick={onLoadNextAccounts} type="button">
            다음
          </button>
        </div>
      </div>

      <div className="account-grid">
        <section className="table-panel embedded">
          <div className="panel-toolbar">
            <div>
              <strong>보유계좌</strong>
              <span>계좌별 잔액과 상태</span>
            </div>
          </div>
          <div className="bank-table-wrap">
            <table className="bank-table">
              <caption>계좌 목록</caption>
              <thead>
                <tr>
                  <th>계좌번호</th>
                  <th>계좌명</th>
                  <th>상태</th>
                  <th>출금가능액</th>
                  <th>관리</th>
                </tr>
              </thead>
              <tbody>
                {accounts.length === 0 ? (
                  <tr>
                    <td colSpan={5}>조회된 계좌가 없습니다.</td>
                  </tr>
                ) : (
                  accounts.map((account) => (
                    <tr key={account.accountId}>
                      <td>{account.accountNumber}</td>
                      <td>{account.displayName}</td>
                      <td>
                        <span className="status-badge">{account.accountStatus}</span>
                      </td>
                      <td className="amount-cell">
                        {formatMinorAmount(
                          account.availableBalanceMinor,
                          account.currencyCode,
                        )}
                      </td>
                      <td>
                        <button
                          disabled={isBusy}
                          onClick={() => onLoadAccountDetail(account.accountId)}
                          type="button"
                        >
                          상세
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>

        <aside className="detail-panel">
          <div className="form-heading">
            <strong>계좌상세</strong>
            <span>{selectedAccount ? selectedAccount.accountStatus : "미선택"}</span>
          </div>
          {selectedAccount ? (
            <>
              <div className="balance-card">
                <span>{selectedAccount.displayName}</span>
                <strong>
                  {formatMinorAmount(
                    selectedAccount.availableBalanceMinor,
                    selectedAccount.currencyCode,
                  )}
                </strong>
                <small>출금가능금액</small>
              </div>
              <dl className="detail-list">
                <div>
                  <dt>Account ID</dt>
                  <dd>{selectedAccount.accountId}</dd>
                </div>
                <div>
                  <dt>계좌번호</dt>
                  <dd>{selectedAccount.accountNumber}</dd>
                </div>
                <div>
                  <dt>보류금액</dt>
                  <dd>
                    {formatMinorAmount(
                      selectedAccount.pendingBalanceMinor,
                      selectedAccount.currencyCode,
                    )}
                  </dd>
                </div>
                <div>
                  <dt>잔액 갱신</dt>
                  <dd>{formatDateTime(selectedAccount.balanceUpdatedAt)}</dd>
                </div>
              </dl>
            </>
          ) : (
            <p className="rail-copy">계좌 목록에서 상세 버튼을 선택하세요.</p>
          )}
        </aside>
      </div>
    </section>
  );
}

function TransferSection({
  isBusy,
  reversalForm,
  reversalResult,
  transferForm,
  transferResult,
  onReversal,
  onReversalChange,
  onTransfer,
  onTransferChange,
}: {
  isBusy: boolean;
  reversalForm: {
    transactionReference: string;
    sourceAccountId: string;
    amountMinor: string;
    reversalReason: string;
    summary: string;
  };
  reversalResult: TransferReversalResponse | null;
  transferForm: {
    sourceAccountId: string;
    targetAccountId: string;
    amountMinor: string;
    currencyCode: string;
    summary: string;
  };
  transferResult: TransferResponse | null;
  onReversal: (event: FormEvent<HTMLFormElement>) => void;
  onReversalChange: (value: {
    transactionReference: string;
    sourceAccountId: string;
    amountMinor: string;
    reversalReason: string;
    summary: string;
  }) => void;
  onTransfer: (event: FormEvent<HTMLFormElement>) => void;
  onTransferChange: (value: {
    sourceAccountId: string;
    targetAccountId: string;
    amountMinor: string;
    currencyCode: string;
    summary: string;
  }) => void;
}) {
  return (
    <section className="task-section">
      <div className="section-title">
        <div>
          <p>이체</p>
          <h1>즉시이체</h1>
        </div>
      </div>

      <div className="two-column">
        <form className="bank-form" onSubmit={onTransfer}>
          <div className="form-heading">
            <strong>이체정보 입력</strong>
            <span>요청 단위 중복 방지</span>
          </div>
          <div className="form-grid">
            <label>
              <span>출금계좌 ID</span>
              <input
                inputMode="numeric"
                onChange={(event) =>
                  onTransferChange({
                    ...transferForm,
                    sourceAccountId: event.target.value,
                  })
                }
                required
                value={transferForm.sourceAccountId}
              />
            </label>
            <label>
              <span>입금계좌 ID</span>
              <input
                inputMode="numeric"
                onChange={(event) =>
                  onTransferChange({
                    ...transferForm,
                    targetAccountId: event.target.value,
                  })
                }
                required
                value={transferForm.targetAccountId}
              />
            </label>
            <label>
              <span>금액 minor</span>
              <input
                inputMode="numeric"
                onChange={(event) =>
                  onTransferChange({
                    ...transferForm,
                    amountMinor: event.target.value,
                  })
                }
                required
                value={transferForm.amountMinor}
              />
            </label>
            <label>
              <span>통화</span>
              <input
                maxLength={3}
                onChange={(event) =>
                  onTransferChange({
                    ...transferForm,
                    currencyCode: event.target.value.toUpperCase(),
                  })
                }
                required
                value={transferForm.currencyCode}
              />
            </label>
          </div>
          <label>
            <span>받는 분 통장 표시</span>
            <input
              maxLength={120}
              onChange={(event) =>
                onTransferChange({ ...transferForm, summary: event.target.value })
              }
              required
              value={transferForm.summary}
            />
          </label>
          <button disabled={isBusy} type="submit">
            이체 실행
          </button>
        </form>

        <ResultPanel
          title="이체 결과"
          rows={
            transferResult
              ? [
                  ["거래번호", transferResult.transactionReference],
                  ["상태", transferResult.status],
                  [
                    "이체금액",
                    formatMinorAmount(
                      transferResult.amountMinor,
                      transferResult.currencyCode,
                    ),
                  ],
                  [
                    "이체 후 잔액",
                    formatMinorAmount(
                      transferResult.availableBalanceAfterMinor,
                      transferResult.currencyCode,
                    ),
                  ],
                  ["기장시각", formatDateTime(transferResult.bookedAt)],
                ]
              : []
          }
        />
      </div>

      <div className="two-column">
        <form className="bank-form" onSubmit={onReversal}>
          <div className="form-heading">
            <strong>이체 취소</strong>
            <span>원거래 reference 기준</span>
          </div>
          <label>
            <span>원거래번호</span>
            <input
              onChange={(event) =>
                onReversalChange({
                  ...reversalForm,
                  transactionReference: event.target.value,
                })
              }
              required
              value={reversalForm.transactionReference}
            />
          </label>
          <div className="form-grid">
            <label>
              <span>출금계좌 ID</span>
              <input
                inputMode="numeric"
                onChange={(event) =>
                  onReversalChange({
                    ...reversalForm,
                    sourceAccountId: event.target.value,
                  })
                }
                required
                value={reversalForm.sourceAccountId}
              />
            </label>
            <label>
              <span>취소금액 minor</span>
              <input
                inputMode="numeric"
                onChange={(event) =>
                  onReversalChange({
                    ...reversalForm,
                    amountMinor: event.target.value,
                  })
                }
                required
                value={reversalForm.amountMinor}
              />
            </label>
          </div>
          <label>
            <span>취소사유</span>
            <select
              onChange={(event) =>
                onReversalChange({
                  ...reversalForm,
                  reversalReason: event.target.value,
                })
              }
              value={reversalForm.reversalReason}
            >
              <option value="CUSTOMER_REQUEST">CUSTOMER_REQUEST</option>
              <option value="DUPLICATE">DUPLICATE</option>
              <option value="WRONG_AMOUNT">WRONG_AMOUNT</option>
              <option value="WRONG_TARGET">WRONG_TARGET</option>
              <option value="FRAUD_REPORTED">FRAUD_REPORTED</option>
            </select>
          </label>
          <label>
            <span>적요</span>
            <input
              maxLength={120}
              onChange={(event) =>
                onReversalChange({ ...reversalForm, summary: event.target.value })
              }
              required
              value={reversalForm.summary}
            />
          </label>
          <button disabled={isBusy} type="submit">
            취소 실행
          </button>
        </form>

        <ResultPanel
          title="취소 결과"
          rows={
            reversalResult
              ? [
                  ["원거래", reversalResult.originalTransactionReference],
                  ["취소거래", reversalResult.reversalTransactionReference],
                  ["상태", reversalResult.status],
                  [
                    "취소금액",
                    formatMinorAmount(
                      reversalResult.amountMinor,
                      reversalResult.currencyCode,
                    ),
                  ],
                  ["기장시각", formatDateTime(reversalResult.bookedAt)],
                ]
              : []
          }
        />
      </div>
    </section>
  );
}

function TransactionsSection({
  filters,
  isBusy,
  mode,
  slice,
  transactionDetail,
  transactions,
  onDetail,
  onFilterChange,
  onModeChange,
  onNext,
  onSearch,
}: {
  filters: {
    accountId: string;
    from: string;
    to: string;
    limit: string;
    cursor: string;
    status: string;
    direction: string;
    minAmountMinor: string;
    maxAmountMinor: string;
    transactionReference: string;
    responseShape: "full" | "slim";
  };
  isBusy: boolean;
  mode: "active" | "archive";
  slice: TransactionQueryResponse | null;
  transactionDetail: TransactionDetailResponse | null;
  transactions: TransactionItem[];
  onDetail: (transactionReference: string) => void;
  onFilterChange: (value: {
    accountId: string;
    from: string;
    to: string;
    limit: string;
    cursor: string;
    status: string;
    direction: string;
    minAmountMinor: string;
    maxAmountMinor: string;
    transactionReference: string;
    responseShape: "full" | "slim";
  }) => void;
  onModeChange: (mode: "active" | "archive") => void;
  onNext: () => void;
  onSearch: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <section className="task-section">
      <div className="section-title">
        <div>
          <p>조회</p>
          <h1>거래내역 조회</h1>
        </div>
        <div className="tab-switch" role="tablist" aria-label="거래 조회 구분">
          <button
            aria-selected={mode === "active"}
            className={mode === "active" ? "active" : ""}
            onClick={() => onModeChange("active")}
            role="tab"
            type="button"
          >
            일반
          </button>
          <button
            aria-selected={mode === "archive"}
            className={mode === "archive" ? "active" : ""}
            onClick={() => onModeChange("archive")}
            role="tab"
            type="button"
          >
            아카이브
          </button>
        </div>
      </div>

      <form className="bank-form filter-form" onSubmit={onSearch}>
        <div className="filter-grid">
          <label>
            <span>계좌 ID</span>
            <input
              inputMode="numeric"
              onChange={(event) =>
                onFilterChange({ ...filters, accountId: event.target.value })
              }
              required
              value={filters.accountId}
            />
          </label>
          <label>
            <span>시작일시</span>
            <input
              onChange={(event) =>
                onFilterChange({ ...filters, from: event.target.value })
              }
              required
              type="datetime-local"
              value={filters.from}
            />
          </label>
          <label>
            <span>종료일시</span>
            <input
              onChange={(event) =>
                onFilterChange({ ...filters, to: event.target.value })
              }
              required
              type="datetime-local"
              value={filters.to}
            />
          </label>
          <label>
            <span>건수</span>
            <select
              onChange={(event) =>
                onFilterChange({ ...filters, limit: event.target.value })
              }
              value={filters.limit}
            >
              <option value="20">20</option>
              <option value="50">50</option>
              <option value="100">100</option>
            </select>
          </label>
          <label>
            <span>상태</span>
            <select
              onChange={(event) =>
                onFilterChange({ ...filters, status: event.target.value })
              }
              value={filters.status}
            >
              <option value="">전체</option>
              <option value="PENDING">PENDING</option>
              <option value="BOOKED">BOOKED</option>
              <option value="REVERSED">REVERSED</option>
              <option value="FAILED">FAILED</option>
            </select>
          </label>
          <label>
            <span>입출금</span>
            <select
              onChange={(event) =>
                onFilterChange({ ...filters, direction: event.target.value })
              }
              value={filters.direction}
            >
              <option value="">전체</option>
              <option value="DEBIT">출금</option>
              <option value="CREDIT">입금</option>
            </select>
          </label>
          <label>
            <span>최소금액 minor</span>
            <input
              inputMode="numeric"
              onChange={(event) =>
                onFilterChange({
                  ...filters,
                  minAmountMinor: event.target.value,
                })
              }
              value={filters.minAmountMinor}
            />
          </label>
          <label>
            <span>최대금액 minor</span>
            <input
              inputMode="numeric"
              onChange={(event) =>
                onFilterChange({
                  ...filters,
                  maxAmountMinor: event.target.value,
                })
              }
              value={filters.maxAmountMinor}
            />
          </label>
          <label>
            <span>거래번호</span>
            <input
              onChange={(event) =>
                onFilterChange({
                  ...filters,
                  transactionReference: event.target.value,
                })
              }
              value={filters.transactionReference}
            />
          </label>
          <label>
            <span>응답형태</span>
            <select
              onChange={(event) =>
                onFilterChange({
                  ...filters,
                  responseShape: event.target.value as "full" | "slim",
                })
              }
              value={filters.responseShape}
            >
              <option value="full">full</option>
              <option value="slim">slim</option>
            </select>
          </label>
        </div>
        <div className="button-row">
          <button disabled={isBusy} type="submit">
            조회
          </button>
          <button disabled={!slice?.nextCursor || isBusy} onClick={onNext} type="button">
            다음 거래
          </button>
        </div>
      </form>

      <div className="split-work">
        <section className="table-panel embedded">
          <div className="panel-toolbar">
            <div>
              <strong>거래내역</strong>
              <span>
                {slice
                  ? `${transactions.length}건 표시 / next ${slice.hasNext ? "있음" : "없음"}`
                  : "조회 전"}
              </span>
            </div>
          </div>
          <div className="bank-table-wrap">
            <table className="bank-table">
              <caption>거래내역 목록</caption>
              <thead>
                <tr>
                  <th>기장일시</th>
                  <th>거래번호</th>
                  <th>입출금</th>
                  <th>상태</th>
                  <th>금액</th>
                  <th>잔액</th>
                  <th>상세</th>
                </tr>
              </thead>
              <tbody>
                {transactions.length === 0 ? (
                  <tr>
                    <td colSpan={7}>조회된 거래가 없습니다.</td>
                  </tr>
                ) : (
                  transactions.map((item) => (
                    <tr key={`${item.id}-${item.transactionReference}`}>
                      <td>{formatDateTime(item.bookedAt)}</td>
                      <td>{item.transactionReference}</td>
                      <td>{item.direction}</td>
                      <td>
                        <span className="status-badge">{item.status}</span>
                      </td>
                      <td className={item.direction === "DEBIT" ? "amount debit" : "amount credit"}>
                        {formatMinorAmount(item.amountMinor, item.currencyCode)}
                      </td>
                      <td>
                        {item.balanceAfterMinor == null
                          ? "-"
                          : formatMinorAmount(
                              item.balanceAfterMinor,
                              item.currencyCode,
                            )}
                      </td>
                      <td>
                        <button
                          disabled={isBusy}
                          onClick={() => onDetail(item.transactionReference)}
                          type="button"
                        >
                          상세
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>

        <aside className="detail-panel">
          <div className="form-heading">
            <strong>거래상세</strong>
            <span>{transactionDetail?.transactionStatus ?? "미선택"}</span>
          </div>
          {transactionDetail ? (
            <dl className="detail-list">
              <div>
                <dt>거래번호</dt>
                <dd>{transactionDetail.transactionReference}</dd>
              </div>
              <div>
                <dt>입출금</dt>
                <dd>{transactionDetail.direction}</dd>
              </div>
              <div>
                <dt>금액</dt>
                <dd>
                  {formatMinorAmount(
                    transactionDetail.amountMinor,
                    transactionDetail.currencyCode,
                  )}
                </dd>
              </div>
              <div>
                <dt>잔액</dt>
                <dd>
                  {formatMinorAmount(
                    transactionDetail.balanceAfterMinor,
                    transactionDetail.currencyCode,
                  )}
                </dd>
              </div>
              <div>
                <dt>상대방</dt>
                <dd>{transactionDetail.counterpartyMaskedName ?? "-"}</dd>
              </div>
              <div>
                <dt>Ledger</dt>
                <dd>{transactionDetail.entryReference}</dd>
              </div>
              <div>
                <dt>발생시각</dt>
                <dd>{formatDateTime(transactionDetail.occurredAt)}</dd>
              </div>
              <div>
                <dt>설명</dt>
                <dd>{transactionDetail.description ?? "-"}</dd>
              </div>
            </dl>
          ) : (
            <p className="rail-copy">거래내역에서 상세 버튼을 선택하세요.</p>
          )}
        </aside>
      </div>
    </section>
  );
}

function ResultPanel({ title, rows }: { title: string; rows: string[][] }) {
  return (
    <div className="result-panel">
      <div className="form-heading">
        <strong>{title}</strong>
        <span>{rows.length > 0 ? "완료" : "대기"}</span>
      </div>
      {rows.length === 0 ? (
        <p className="rail-copy">처리 결과가 여기에 표시됩니다.</p>
      ) : (
        <dl className="detail-list">
          {rows.map(([key, value]) => (
            <div key={key}>
              <dt>{key}</dt>
              <dd>{value}</dd>
            </div>
          ))}
        </dl>
      )}
    </div>
  );
}

function NotificationsSection({
  filters,
  isBusy,
  mode,
  notifications,
  preferences,
  selectedIds,
  slice,
  sseStatus,
  unreadCount,
  onBulkAction,
  onConnect,
  onDisconnect,
  onFilterChange,
  onLoadPreferences,
  onLoadUnreadCount,
  onModeChange,
  onNext,
  onPreferenceChange,
  onSearch,
  onToggleId,
  onUpdatePreferences,
}: {
  filters: {
    limit: string;
    cursor: string;
    readStatus: string;
    eventType: string;
    from: string;
    to: string;
  };
  isBusy: boolean;
  mode: "inbox" | "search";
  notifications: NotificationItem[];
  preferences: NotificationPreferenceItem[];
  selectedIds: number[];
  slice: NotificationQueryResponse | null;
  sseStatus: {
    state: string;
    lastEventAt: string;
    lastEventId: string;
  };
  unreadCount: number | null;
  onBulkAction: (
    action: "read" | "archive" | "delete",
    notificationId?: number,
  ) => void;
  onConnect: () => void;
  onDisconnect: () => void;
  onFilterChange: (value: {
    limit: string;
    cursor: string;
    readStatus: string;
    eventType: string;
    from: string;
    to: string;
  }) => void;
  onLoadPreferences: () => void;
  onLoadUnreadCount: () => void;
  onModeChange: (mode: "inbox" | "search") => void;
  onNext: () => void;
  onPreferenceChange: (value: NotificationPreferenceItem[]) => void;
  onSearch: (event: FormEvent<HTMLFormElement>) => void;
  onToggleId: (notificationId: number) => void;
  onUpdatePreferences: () => void;
}) {
  return (
    <section className="task-section">
      <div className="section-title">
        <div>
          <p>고객센터</p>
          <h1>알림</h1>
        </div>
        <div className="button-row">
          <button disabled={isBusy} onClick={onLoadUnreadCount} type="button">
            미확인 조회
          </button>
          <button disabled={isBusy} onClick={onConnect} type="button">
            SSE 연결
          </button>
          <button onClick={onDisconnect} type="button">
            연결 해제
          </button>
        </div>
      </div>

      <div className="notification-status">
        <div>
          <span>SSE</span>
          <strong>{sseStatus.state}</strong>
        </div>
        <div>
          <span>Unread</span>
          <strong>{unreadCount ?? "-"}</strong>
        </div>
        <div>
          <span>Last Event</span>
          <strong>{sseStatus.lastEventId || "-"}</strong>
        </div>
        <div>
          <span>수신시각</span>
          <strong>{formatDateTime(sseStatus.lastEventAt)}</strong>
        </div>
      </div>

      <form className="bank-form filter-form" onSubmit={onSearch}>
        <div className="panel-toolbar inline-toolbar">
          <div className="tab-switch" role="tablist" aria-label="알림 조회 구분">
            <button
              aria-selected={mode === "inbox"}
              className={mode === "inbox" ? "active" : ""}
              onClick={() => onModeChange("inbox")}
              role="tab"
              type="button"
            >
              Inbox
            </button>
            <button
              aria-selected={mode === "search"}
              className={mode === "search" ? "active" : ""}
              onClick={() => onModeChange("search")}
              role="tab"
              type="button"
            >
              Search
            </button>
          </div>
          <div className="button-row compact">
            <button disabled={isBusy} type="submit">
              조회
            </button>
            <button disabled={!slice?.nextCursor || isBusy} onClick={onNext} type="button">
              다음
            </button>
          </div>
        </div>
        <div className="filter-grid notification-filter">
          <label>
            <span>건수</span>
            <select
              onChange={(event) =>
                onFilterChange({ ...filters, limit: event.target.value })
              }
              value={filters.limit}
            >
              <option value="20">20</option>
              <option value="50">50</option>
              <option value="100">100</option>
            </select>
          </label>
          <label>
            <span>읽음상태</span>
            <select
              disabled={mode === "inbox"}
              onChange={(event) =>
                onFilterChange({ ...filters, readStatus: event.target.value })
              }
              value={filters.readStatus}
            >
              <option value="ALL">ALL</option>
              <option value="READ">READ</option>
              <option value="UNREAD">UNREAD</option>
            </select>
          </label>
          <label>
            <span>이벤트 유형</span>
            <input
              disabled={mode === "inbox"}
              onChange={(event) =>
                onFilterChange({ ...filters, eventType: event.target.value })
              }
              value={filters.eventType}
            />
          </label>
          <label>
            <span>시작일시</span>
            <input
              disabled={mode === "inbox"}
              onChange={(event) =>
                onFilterChange({ ...filters, from: event.target.value })
              }
              type="datetime-local"
              value={filters.from}
            />
          </label>
          <label>
            <span>종료일시</span>
            <input
              disabled={mode === "inbox"}
              onChange={(event) =>
                onFilterChange({ ...filters, to: event.target.value })
              }
              type="datetime-local"
              value={filters.to}
            />
          </label>
        </div>
      </form>

      <div className="split-work">
        <section className="table-panel embedded">
          <div className="panel-toolbar">
            <div>
              <strong>알림함</strong>
              <span>
                {slice
                  ? `${notifications.length}건 표시 / next ${slice.hasNext ? "있음" : "없음"}`
                  : "조회 전"}
              </span>
            </div>
            <div className="button-row compact">
              <button
                disabled={selectedIds.length === 0 || isBusy}
                onClick={() => onBulkAction("read")}
                type="button"
              >
                읽음
              </button>
              <button
                disabled={selectedIds.length === 0 || isBusy}
                onClick={() => onBulkAction("archive")}
                type="button"
              >
                보관
              </button>
              <button
                disabled={selectedIds.length === 0 || isBusy}
                onClick={() => onBulkAction("delete")}
                type="button"
              >
                삭제
              </button>
            </div>
          </div>
          <div className="bank-table-wrap">
            <table className="bank-table">
              <caption>알림 목록</caption>
              <thead>
                <tr>
                  <th>선택</th>
                  <th>상태</th>
                  <th>유형</th>
                  <th>제목</th>
                  <th>수신시각</th>
                  <th>관리</th>
                </tr>
              </thead>
              <tbody>
                {notifications.length === 0 ? (
                  <tr>
                    <td colSpan={6}>조회된 알림이 없습니다.</td>
                  </tr>
                ) : (
                  notifications.map((item) => (
                    <tr key={item.notificationId}>
                      <td>
                        <input
                          aria-label={`${item.notificationId} 선택`}
                          checked={selectedIds.includes(item.notificationId)}
                          onChange={() => onToggleId(item.notificationId)}
                          type="checkbox"
                        />
                      </td>
                      <td>
                        <span className={item.read ? "status-badge" : "status-badge unread"}>
                          {item.read ? "READ" : "UNREAD"}
                        </span>
                      </td>
                      <td>{item.eventType}</td>
                      <td>
                        <strong>{item.title}</strong>
                        <p className="table-message">{item.message}</p>
                      </td>
                      <td>{formatDateTime(item.createdAt)}</td>
                      <td>
                        <button
                          disabled={item.read || isBusy}
                          onClick={() => onBulkAction("read", item.notificationId)}
                          type="button"
                        >
                          읽음
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>

        <aside className="detail-panel">
          <div className="form-heading">
            <strong>알림 수신 설정</strong>
            <span>{preferences.length}건</span>
          </div>
          <div className="button-row compact preference-actions">
            <button disabled={isBusy} onClick={onLoadPreferences} type="button">
              설정 조회
            </button>
            <button disabled={preferences.length === 0 || isBusy} onClick={onUpdatePreferences} type="button">
              저장
            </button>
          </div>
          {preferences.length === 0 ? (
            <p className="rail-copy">알림 설정을 조회하세요.</p>
          ) : (
            <div className="preference-list">
              {preferences.map((item, index) => (
                <label className="preference-row" key={`${item.category}-${item.channel}`}>
                  <input
                    checked={item.enabled}
                    onChange={(event) => {
                      const nextItems = [...preferences];
                      nextItems[index] = {
                        ...item,
                        enabled: event.target.checked,
                      };
                      onPreferenceChange(nextItems);
                    }}
                    type="checkbox"
                  />
                  <span>
                    <strong>{item.category}</strong>
                    <small>{item.channel}</small>
                  </span>
                </label>
              ))}
            </div>
          )}
        </aside>
      </div>
    </section>
  );
}

function SecuritySection(props: {
  backupChallengeForm: {
    backupCode: string;
    rememberDevice: boolean;
  };
  backupCodes: BackupCodeIssueResponse | null;
  challenge: LoginResponse | null;
  isBusy: boolean;
  loginForm: {
    loginId: string;
    password: string;
  };
  passwordRecoveryForm: {
    loginId: string;
    recoveryToken: string;
    newPassword: string;
  };
  passwordRecoveryResult: PasswordRecoveryRequestResult | null;
  passwordResetForm: {
    currentPassword: string;
    newPassword: string;
  };
  session: CustomerSession | null;
  sessions: AuthSessionItem[];
  totpChallengeForm: {
    totpCode: string;
    rememberDevice: boolean;
  };
  totpCode: string;
  totpEnrollment: TotpEnrollmentStartResponse | null;
  onBackupChallengeChange: (value: {
    backupCode: string;
    rememberDevice: boolean;
  }) => void;
  onBackupChallenge: (event: FormEvent<HTMLFormElement>) => void;
  onDisableTotp: () => void;
  onIssueBackupCodes: () => void;
  onLoadSessions: () => void;
  onLogin: (event: FormEvent<HTMLFormElement>) => void;
  onLoginChange: (value: { loginId: string; password: string }) => void;
  onLogout: () => void;
  onPasswordRecoveryChange: (value: {
    loginId: string;
    recoveryToken: string;
    newPassword: string;
  }) => void;
  onPasswordRecoveryConfirm: (event: FormEvent<HTMLFormElement>) => void;
  onPasswordRecoveryRequest: (event: FormEvent<HTMLFormElement>) => void;
  onPasswordReset: (event: FormEvent<HTMLFormElement>) => void;
  onPasswordResetChange: (value: {
    currentPassword: string;
    newPassword: string;
  }) => void;
  onRefresh: () => void;
  onRevokeAllSessions: () => void;
  onRevokeSession: (sessionId: number) => void;
  onStartTotpEnrollment: () => void;
  onTotpChallenge: (event: FormEvent<HTMLFormElement>) => void;
  onTotpChallengeChange: (value: { totpCode: string; rememberDevice: boolean }) => void;
  onTotpCodeChange: (value: string) => void;
  onVerifyTotpEnrollment: () => void;
}) {
  return (
    <section className="task-section security-section">
      <div className="section-title">
        <div>
          <p>인증센터</p>
          <h1>로그인 및 보안관리</h1>
        </div>
        <div className="button-row">
          <button disabled={!props.session || props.isBusy} onClick={props.onRefresh} type="button">
            토큰 재발급
          </button>
          <button disabled={!props.session || props.isBusy} onClick={props.onLogout} type="button">
            로그아웃
          </button>
        </div>
      </div>

      <div className="two-column">
        <form className="bank-form" onSubmit={props.onLogin}>
          <div className="form-heading">
            <strong>로그인</strong>
            <span>아이디와 비밀번호</span>
          </div>
          <label>
            <span>이용자 ID</span>
            <input
              autoComplete="username"
              onChange={(event) =>
                props.onLoginChange({
                  ...props.loginForm,
                  loginId: event.target.value,
                })
              }
              required
              value={props.loginForm.loginId}
            />
          </label>
          <label>
            <span>비밀번호</span>
            <input
              autoComplete="current-password"
              onChange={(event) =>
                props.onLoginChange({
                  ...props.loginForm,
                  password: event.target.value,
                })
              }
              required
              type="password"
              value={props.loginForm.password}
            />
          </label>
          <button disabled={props.isBusy} type="submit">
            로그인
          </button>
        </form>

        <div className="bank-form passive">
          <div className="form-heading">
            <strong>현재 세션</strong>
            <span>{props.session ? "로그인됨" : "로그인 필요"}</span>
          </div>
          <dl className="detail-list">
            <div>
              <dt>User ID</dt>
              <dd>{props.session?.userId ?? "-"}</dd>
            </div>
            <div>
              <dt>Access Token</dt>
              <dd>{props.session ? maskToken(props.session.accessToken) : "-"}</dd>
            </div>
            <div>
              <dt>Refresh 만료</dt>
              <dd>{formatDateTime(props.session?.refreshExpiresAt)}</dd>
            </div>
          </dl>
        </div>
      </div>

      {props.challenge ? (
        <div className="two-column">
          <form className="bank-form" onSubmit={props.onTotpChallenge}>
            <div className="form-heading">
              <strong>TOTP 추가 인증</strong>
              <span>{props.challenge.challengeType ?? "TOTP"}</span>
            </div>
            <label>
              <span>인증번호 6자리</span>
              <input
                inputMode="numeric"
                maxLength={6}
                onChange={(event) =>
                  props.onTotpChallengeChange({
                    ...props.totpChallengeForm,
                    totpCode: event.target.value,
                  })
                }
                required
                value={props.totpChallengeForm.totpCode}
              />
            </label>
            <label className="checkbox-line">
              <input
                checked={props.totpChallengeForm.rememberDevice}
                onChange={(event) =>
                  props.onTotpChallengeChange({
                    ...props.totpChallengeForm,
                    rememberDevice: event.target.checked,
                  })
                }
                type="checkbox"
              />
              <span>이 기기 기억</span>
            </label>
            <button disabled={props.isBusy} type="submit">
              인증
            </button>
          </form>

          <form className="bank-form" onSubmit={props.onBackupChallenge}>
            <div className="form-heading">
              <strong>Backup Code 인증</strong>
              <span>대체 인증수단</span>
            </div>
            <label>
              <span>Backup Code</span>
              <input
                onChange={(event) =>
                  props.onBackupChallengeChange({
                    ...props.backupChallengeForm,
                    backupCode: event.target.value,
                  })
                }
                required
                value={props.backupChallengeForm.backupCode}
              />
            </label>
            <label className="checkbox-line">
              <input
                checked={props.backupChallengeForm.rememberDevice}
                onChange={(event) =>
                  props.onBackupChallengeChange({
                    ...props.backupChallengeForm,
                    rememberDevice: event.target.checked,
                  })
                }
                type="checkbox"
              />
              <span>이 기기 기억</span>
            </label>
            <button disabled={props.isBusy} type="submit">
              인증
            </button>
          </form>
        </div>
      ) : null}

      <div className="two-column">
        <form className="bank-form" onSubmit={props.onPasswordRecoveryRequest}>
          <div className="form-heading">
            <strong>비밀번호 찾기</strong>
            <span>복구 요청</span>
          </div>
          <label>
            <span>이용자 ID</span>
            <input
              onChange={(event) =>
                props.onPasswordRecoveryChange({
                  ...props.passwordRecoveryForm,
                  loginId: event.target.value,
                })
              }
              required
              value={props.passwordRecoveryForm.loginId}
            />
          </label>
          <button disabled={props.isBusy} type="submit">
            복구 요청
          </button>
          {props.passwordRecoveryResult?.handoffRequestId ? (
            <p className="field-note">
              Request ID: {props.passwordRecoveryResult.handoffRequestId}
            </p>
          ) : null}
        </form>

        <form className="bank-form" onSubmit={props.onPasswordRecoveryConfirm}>
          <div className="form-heading">
            <strong>복구 확정</strong>
            <span>토큰 기반 변경</span>
          </div>
          <label>
            <span>복구 토큰</span>
            <input
              onChange={(event) =>
                props.onPasswordRecoveryChange({
                  ...props.passwordRecoveryForm,
                  recoveryToken: event.target.value,
                })
              }
              required
              value={props.passwordRecoveryForm.recoveryToken}
            />
          </label>
          <label>
            <span>새 비밀번호</span>
            <input
              onChange={(event) =>
                props.onPasswordRecoveryChange({
                  ...props.passwordRecoveryForm,
                  newPassword: event.target.value,
                })
              }
              required
              type="password"
              value={props.passwordRecoveryForm.newPassword}
            />
          </label>
          <button disabled={props.isBusy} type="submit">
            변경
          </button>
        </form>
      </div>

      <div className="two-column">
        <form className="bank-form" onSubmit={props.onPasswordReset}>
          <div className="form-heading">
            <strong>비밀번호 변경</strong>
            <span>로그인 세션 필요</span>
          </div>
          <label>
            <span>현재 비밀번호</span>
            <input
              onChange={(event) =>
                props.onPasswordResetChange({
                  ...props.passwordResetForm,
                  currentPassword: event.target.value,
                })
              }
              required
              type="password"
              value={props.passwordResetForm.currentPassword}
            />
          </label>
          <label>
            <span>새 비밀번호</span>
            <input
              onChange={(event) =>
                props.onPasswordResetChange({
                  ...props.passwordResetForm,
                  newPassword: event.target.value,
                })
              }
              required
              type="password"
              value={props.passwordResetForm.newPassword}
            />
          </label>
          <button disabled={!props.session || props.isBusy} type="submit">
            변경
          </button>
        </form>

        <div className="bank-form passive">
          <div className="form-heading">
            <strong>MFA 관리</strong>
            <span>TOTP / Backup Code</span>
          </div>
          <label>
            <span>TOTP 인증번호</span>
            <input
              inputMode="numeric"
              maxLength={6}
              onChange={(event) => props.onTotpCodeChange(event.target.value)}
              value={props.totpCode}
            />
          </label>
          <div className="button-row compact">
            <button
              disabled={!props.session || props.isBusy}
              onClick={props.onStartTotpEnrollment}
              type="button"
            >
              등록 시작
            </button>
            <button
              disabled={!props.session || props.isBusy}
              onClick={props.onVerifyTotpEnrollment}
              type="button"
            >
              등록 확인
            </button>
            <button
              disabled={!props.session || props.isBusy}
              onClick={props.onIssueBackupCodes}
              type="button"
            >
              코드 발급
            </button>
            <button
              disabled={!props.session || props.isBusy}
              onClick={props.onDisableTotp}
              type="button"
            >
              해지
            </button>
          </div>
          {props.totpEnrollment ? (
            <dl className="detail-list compact-list">
              <div>
                <dt>Secret</dt>
                <dd>{props.totpEnrollment.secretKey}</dd>
              </div>
              <div>
                <dt>만료</dt>
                <dd>{formatDateTime(props.totpEnrollment.expiresAt)}</dd>
              </div>
            </dl>
          ) : null}
          {props.backupCodes ? (
            <div className="code-list">
              {props.backupCodes.backupCodes.map((code) => (
                <code key={code}>{code}</code>
              ))}
            </div>
          ) : null}
        </div>
      </div>

      <section className="table-panel">
        <div className="panel-toolbar">
          <div>
            <strong>접속 세션</strong>
            <span>현재 사용자 refresh token session</span>
          </div>
          <div className="button-row compact">
            <button
              disabled={!props.session || props.isBusy}
              onClick={props.onLoadSessions}
              type="button"
            >
              조회
            </button>
            <button
              disabled={!props.session || props.isBusy}
              onClick={props.onRevokeAllSessions}
              type="button"
            >
              전체 해지
            </button>
          </div>
        </div>
        <div className="bank-table-wrap">
          <table className="bank-table">
            <caption>인증 세션 목록</caption>
            <thead>
              <tr>
                <th>Session ID</th>
                <th>상태</th>
                <th>기기</th>
                <th>IP</th>
                <th>최종 사용</th>
                <th>관리</th>
              </tr>
            </thead>
            <tbody>
              {props.sessions.length === 0 ? (
                <tr>
                  <td colSpan={6}>조회된 세션이 없습니다.</td>
                </tr>
              ) : (
                props.sessions.map((item) => (
                  <tr key={item.sessionId}>
                    <td>{item.sessionId}</td>
                    <td>{item.currentSession ? "현재 세션" : item.sessionStatus}</td>
                    <td>{item.deviceName ?? "-"}</td>
                    <td>{item.ipAddress ?? "-"}</td>
                    <td>{formatDateTime(item.lastUsedAt)}</td>
                    <td>
                      <button
                        disabled={props.isBusy}
                        onClick={() => props.onRevokeSession(item.sessionId)}
                        type="button"
                      >
                        해지
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>
    </section>
  );
}
