"use client";

import type { FormEvent } from "react";
import { useEffect, useMemo, useState } from "react";
import {
  ApiClientError,
  ApiConfigurationError,
  AquilaBankApiClient,
} from "@/lib/api/client";
import {
  clearCustomerSession,
  loadCustomerSession,
  saveCustomerSession,
  toCustomerSession,
} from "@/lib/api/session";
import type {
  AuthSessionItem,
  BackupCodeIssueResponse,
  CustomerSession,
  LoginResponse,
  PasswordRecoveryRequestResult,
  TotpEnrollmentStartResponse,
} from "@/lib/api/types";

type MenuSection =
  | "dashboard"
  | "accounts"
  | "transfer"
  | "transactions"
  | "notifications"
  | "security";

type AlertMessage = {
  type: "info" | "success" | "error";
  text: string;
};

const mainMenus: Array<{ id: MenuSection; label: string; group: string }> = [
  { id: "dashboard", label: "뱅킹홈", group: "개인뱅킹" },
  { id: "accounts", label: "조회", group: "계좌" },
  { id: "transfer", label: "이체", group: "이체" },
  { id: "transactions", label: "거래내역 조회", group: "조회" },
  { id: "notifications", label: "알림", group: "고객센터" },
  { id: "security", label: "인증/세션 관리", group: "뱅킹관리" },
];

const quickMenus = ["계좌조회", "즉시이체", "거래내역", "인증센터", "알림함"];

function toErrorMessage(error: unknown): string {
  if (error instanceof ApiConfigurationError) {
    return "백엔드 API 주소가 설정되지 않았습니다. NEXT_PUBLIC_API_BASE_URL을 확인하세요.";
  }
  if (error instanceof ApiClientError) {
    return `[${error.status}] ${error.message}`;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "요청 처리 중 오류가 발생했습니다.";
}

function formatDateTime(value?: string | null): string {
  if (!value) {
    return "-";
  }
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}

function maskToken(value: string): string {
  if (value.length <= 16) {
    return value;
  }
  return `${value.slice(0, 10)}...${value.slice(-6)}`;
}

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
          ) : (
            <BusinessPlaceholder section={activeSection} onMove={setActiveSection} />
          )}
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

function BusinessPlaceholder({
  section,
  onMove,
}: {
  section: MenuSection;
  onMove: (section: MenuSection) => void;
}) {
  if (section === "accounts" || section === "transfer" || section === "transactions") {
    return (
      <section className="task-section">
        <div className="section-title">
          <div>
            <p>{section === "transfer" ? "이체" : "조회"}</p>
            <h1>
              {section === "accounts"
                ? "계좌조회"
                : section === "transfer"
                  ? "이체"
                  : "거래내역 조회"}
            </h1>
          </div>
          <button onClick={() => onMove("security")} type="button">
            로그인 확인
          </button>
        </div>
        <div className="empty-business">
          <strong>업무 화면 연결 대기</strong>
          <span>다음 구현 단위에서 백엔드 공개 API와 연결됩니다.</span>
        </div>
      </section>
    );
  }

  if (section === "notifications") {
    return (
      <section className="task-section">
        <div className="section-title">
          <div>
            <p>고객센터</p>
            <h1>알림</h1>
          </div>
          <button onClick={() => onMove("security")} type="button">
            로그인 확인
          </button>
        </div>
        <div className="empty-business">
          <strong>알림 화면 연결 대기</strong>
          <span>다음 구현 단위에서 inbox, 검색, SSE 상태가 연결됩니다.</span>
        </div>
      </section>
    );
  }

  return null;
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
