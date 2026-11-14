import { useState, type FormEvent } from "react";
import type {
  AuthSessionItem,
  BackupCodeIssueResponse,
  CustomerSession,
  LoginResponse,
  PasswordRecoveryRequestResult,
  TotpEnrollmentStartResponse,
} from "@/lib/api/types";
import { formatDateTime } from "@/lib/customer-banking/format";
import { getSessionStatusLabel } from "@/lib/customer-banking/labels";
import {
  BankActionBar,
  BankDialog,
  BankInput,
  BankNoticeStrip,
  CopyButton,
  OtpCodeInput,
  StatusBadge,
  WorkStateGrid,
} from "../common";

function getChallengeLabel(challengeType: string | null | undefined): string {
  switch (challengeType) {
    case "TOTP":
      return "OTP 인증";
    case "BACKUP_CODE":
      return "복구코드 인증";
    default:
      return "추가 인증";
  }
}

function downloadBackupCodesText(codes: string[]): void {
  if (codes.length === 0) {
    return;
  }
  const content = [
    "Aquila Bank 일회용 복구 코드",
    "각 코드는 1회만 사용할 수 있으며 안전한 곳에 별도 보관하세요.",
    "",
    ...codes,
  ].join("\n");
  const blob = new Blob([content], { type: "text/plain;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "aquila-bank-backup-codes.txt";
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

function isSafeOtpAuthUri(uri: string | null | undefined): uri is string {
  return typeof uri === "string" && uri.startsWith("otpauth://");
}

export function SecuritySection(props: {
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
  const isAuthenticated = Boolean(props.session);
  const activeBackupCodes = isAuthenticated ? props.backupCodes : null;
  const activeTotpEnrollment = isAuthenticated ? props.totpEnrollment : null;
  const [loginSubmitAttempted, setLoginSubmitAttempted] = useState(false);
  const [confirmAction, setConfirmAction] = useState<
    "disableTotp" | "revokeAllSessions" | null
  >(null);

  function renderAuthMethodGrid() {
    return (
      <section className="auth-method-grid" aria-label="로그인 방식">
        <div>
          <span>로그인 방식</span>
          <strong>공동인증서</strong>
          <small>전자서명 기반 로그인</small>
        </div>
        <div>
          <span>로그인 방식</span>
          <strong>금융인증서</strong>
          <small>클라우드 인증서</small>
        </div>
        <div>
          <span>로그인 방식</span>
          <strong>아이디 로그인</strong>
          <small>추가 인증 연계</small>
        </div>
      </section>
    );
  }

  function renderLoginForm() {
    return (
      <form
        className="bank-form tight-work-form"
        onSubmit={(event) => {
          setLoginSubmitAttempted(true);
          props.onLogin(event);
        }}
      >
        <div className="form-heading">
          <strong>로그인</strong>
          <span>아이디와 비밀번호</span>
        </div>
        <BankInput
          autoComplete="username"
          error={props.loginForm.loginId ? "" : "고객 ID를 입력하세요."}
          label="고객 ID"
          onChange={(event) =>
            props.onLoginChange({
              ...props.loginForm,
              loginId: event.target.value,
            })
          }
          required
          submitAttempted={loginSubmitAttempted}
          value={props.loginForm.loginId}
        />
        <BankInput
          autoComplete="current-password"
          error={props.loginForm.password ? "" : "비밀번호를 입력하세요."}
          label="비밀번호"
          onChange={(event) =>
            props.onLoginChange({
              ...props.loginForm,
              password: event.target.value,
            })
          }
          required
          submitAttempted={loginSubmitAttempted}
          type="password"
          value={props.loginForm.password}
        />
        <BankActionBar>
          <button disabled={props.isBusy} type="submit">
            로그인
          </button>
        </BankActionBar>
      </form>
    );
  }

  function renderChallengeForms() {
    if (!props.challenge) {
      return null;
    }

    return (
      <div className="two-column">
        <form className="bank-form" onSubmit={props.onTotpChallenge}>
          <div className="form-heading">
            <strong>OTP 추가 인증</strong>
            <span>{getChallengeLabel(props.challenge.challengeType)}</span>
          </div>
          <OtpCodeInput
            disabled={props.isBusy}
            hint="인증 앱에 표시된 6자리 숫자를 입력하세요."
            label="인증번호 6자리"
            onValueChange={(nextCode) =>
              props.onTotpChallengeChange({
                ...props.totpChallengeForm,
                totpCode: nextCode,
              })
            }
            required
            value={props.totpChallengeForm.totpCode}
          />
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
            <strong>복구코드 인증</strong>
            <span>대체 인증수단</span>
          </div>
          <label>
            <span>복구코드</span>
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
    );
  }

  function renderPasswordRecoveryForms() {
    return (
      <details className="recovery-disclosure">
        <summary>
          <strong>비밀번호 찾기</strong>
          <span>복구 확정 · 비로그인 복구 가능</span>
        </summary>
        <div className="two-column">
          <form className="bank-form" onSubmit={props.onPasswordRecoveryRequest}>
            <div className="form-heading">
              <strong>비밀번호 복구 요청</strong>
              <span>비로그인 복구 가능</span>
            </div>
            <label>
              <span>고객 ID</span>
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
                요청번호: {props.passwordRecoveryResult.handoffRequestId}
              </p>
            ) : null}
          </form>

          <form className="bank-form" onSubmit={props.onPasswordRecoveryConfirm}>
            <div className="form-heading">
              <strong>새 비밀번호 확정</strong>
              <span>본인확인 후 변경</span>
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
      </details>
    );
  }

  function renderGuestSecurityCenter() {
    return (
      <>
        <div className="section-title">
          <div>
            <p>인증센터</p>
            <h1>로그인 및 인증</h1>
          </div>
        </div>
        <BankNoticeStrip
          items={[
            { label: "이용 상태", value: "로그인 필요" },
            { label: "이용 가능", value: "로그인 / 비밀번호 찾기" },
          ]}
        />
        {renderAuthMethodGrid()}
        <div className="two-column auth-public-grid">{renderLoginForm()}</div>
        {renderChallengeForms()}
        {renderPasswordRecoveryForms()}
      </>
    );
  }

  function renderAuthenticatedSecurityCenter() {
    const currentDeviceState = props.sessions.some((item) => item.currentSession)
      ? "현재 기기"
      : "조회 전";

    return (
      <>
        <div className="section-title">
          <div>
            <p>인증센터</p>
            <h1>인증 후 보안관리</h1>
          </div>
          <BankActionBar label="인증 업무 action bar">
            <button disabled={props.isBusy} onClick={props.onRefresh} type="button">
              토큰 재발급
            </button>
            <button disabled={props.isBusy} onClick={props.onLogout} type="button">
              로그아웃
            </button>
          </BankActionBar>
        </div>
        <BankNoticeStrip
          items={[
            { label: "로그인 상태", value: "로그인됨" },
            {
              label: "추가 인증",
              value: props.challenge ? "확인 필요" : "대기",
            },
            { label: "세션", value: formatDateTime(props.session?.refreshExpiresAt) },
            { label: "보안수단", value: "OTP / 복구코드" },
            { label: "세션 해지 상태", value: "개별/전체 해지 가능" },
          ]}
        />
        <WorkStateGrid
          label="인증 업무 상태"
          items={[
            { label: "보안관리", value: "정상" },
            {
              label: "추가인증",
              value: props.challenge
                ? getChallengeLabel(props.challenge.challengeType)
                : "대기",
            },
            {
              label: "접속관리",
              value: props.sessions.length > 0 ? `${props.sessions.length}건` : "조회 전",
            },
            { label: "보안수단", value: "OTP/복구코드" },
          ]}
        />

        <div className="security-media-grid" aria-label="보안매체 등록 상태">
          <div>
            <span>보안매체 등록</span>
            <strong>{activeTotpEnrollment ? "등록 진행" : "대기"}</strong>
            <small>OTP / 보안매체</small>
          </div>
          <div>
            <span>OTP 등록 상태</span>
            <strong>
              {activeTotpEnrollment
                ? activeTotpEnrollment.status === "PENDING_VERIFICATION"
                  ? "인증 대기"
                  : activeTotpEnrollment.status
                : "대기"}
            </strong>
            <small>등록 시작 후 인증번호 확인</small>
          </div>
          <div>
            <span>세션 해지 상태</span>
            <strong>{props.session ? "해지 가능" : "해지 불가"}</strong>
            <small>세션/기기 목록 기준</small>
          </div>
          <div>
            <span>세션 만료</span>
            <strong>{props.session?.expiresAt ? "유효" : "확인 필요"}</strong>
            <small className="tabular-nums">{formatDateTime(props.session?.expiresAt)}</small>
          </div>
          <div>
            <span>현재 기기</span>
            <strong>{currentDeviceState}</strong>
            <small>세션 조회 기준</small>
          </div>
        </div>

        <div className="two-column">
          <div className="bank-form passive">
            <div className="form-heading">
              <strong>현재 세션</strong>
              <span>로그인됨</span>
            </div>
            <dl className="detail-list">
              <div>
                <dt>고객번호</dt>
                <dd className="tabular-nums">{props.session?.userId ?? "확인 전"}</dd>
              </div>
              <div>
                <dt>인증 방식</dt>
                <dd>{props.session ? "보안 세션" : "확인 전"}</dd>
              </div>
              <div>
                <dt>세션 만료</dt>
                <dd className="tabular-nums">
                  {formatDateTime(props.session?.refreshExpiresAt)}
                </dd>
              </div>
            </dl>
          </div>
        </div>

        {renderChallengeForms()}

        <div className="two-column">
          <form
            className="bank-form authenticated-only-form"
            onSubmit={props.onPasswordReset}
          >
            <div className="form-heading">
              <strong>비밀번호 변경</strong>
              <span>로그인 세션 필요</span>
            </div>
            <label>
              <span>현재 비밀번호</span>
              <input
                disabled={props.isBusy}
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
                disabled={props.isBusy}
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
            <button disabled={props.isBusy} type="submit">
              변경
            </button>
          </form>

          <div className="bank-form passive authenticated-only-form">
            <div className="form-heading">
              <strong>추가 인증 관리</strong>
              <span>OTP / 복구코드</span>
            </div>
            <OtpCodeInput
              disabled={props.isBusy}
              hint="등록 확인, 복구코드 발급, 해지 시 현재 6자리 코드를 입력하세요."
              label="OTP 인증번호"
              onValueChange={props.onTotpCodeChange}
              value={props.totpCode}
            />
            <div className="button-row compact">
              <button
                disabled={props.isBusy}
                onClick={props.onStartTotpEnrollment}
                type="button"
              >
                등록 시작
              </button>
              <button
                disabled={props.isBusy}
                onClick={props.onVerifyTotpEnrollment}
                type="button"
              >
                등록 확인
              </button>
              <button
                disabled={props.isBusy}
                onClick={props.onIssueBackupCodes}
                type="button"
              >
                코드 발급
              </button>
              <button
                disabled={props.isBusy}
                onClick={() => setConfirmAction("disableTotp")}
                type="button"
              >
                해지
              </button>
            </div>
            {activeTotpEnrollment ? (
              <dl className="detail-list compact-list">
                <div>
                  <dt>등록키</dt>
                  <dd className="inline-copy-cell font-mono">
                    <span>{activeTotpEnrollment.secretKey}</span>
                    <CopyButton
                      label="등록키"
                      value={activeTotpEnrollment.secretKey}
                    />
                  </dd>
                </div>
                {isSafeOtpAuthUri(activeTotpEnrollment.otpauthUri) ? (
                  <div>
                    <dt>앱 등록</dt>
                    <dd>
                      <a
                        className="otp-app-link"
                        href={activeTotpEnrollment.otpauthUri}
                      >
                        인증 앱에서 바로 등록
                      </a>
                    </dd>
                  </div>
                ) : null}
                <div>
                  <dt>만료</dt>
                  <dd className="tabular-nums">
                    {formatDateTime(activeTotpEnrollment.expiresAt)}
                  </dd>
                </div>
              </dl>
            ) : null}
            {activeBackupCodes ? (
              <div className="backup-codes-panel">
                <div className="backup-codes-actions">
                  <CopyButton
                    label="복구코드 전체"
                    value={activeBackupCodes.backupCodes.join("\n")}
                  />
                  <button
                    onClick={() =>
                      downloadBackupCodesText(activeBackupCodes.backupCodes)
                    }
                    type="button"
                  >
                    .txt 저장
                  </button>
                </div>
                <div className="code-list">
                  {activeBackupCodes.backupCodes.map((code) => (
                    <code className="tabular-nums" key={code}>
                      {code}
                    </code>
                  ))}
                </div>
              </div>
            ) : null}
          </div>
        </div>

        <section className="table-panel">
          <div className="panel-toolbar">
            <div>
              <strong>세션/기기 목록</strong>
              <span>현재 로그인 기기와 세션 상태</span>
            </div>
            <BankActionBar>
              <button disabled={props.isBusy} onClick={props.onLoadSessions} type="button">
                조회
              </button>
              <button
                disabled={props.isBusy}
                onClick={() => setConfirmAction("revokeAllSessions")}
                type="button"
              >
                전체 해지
              </button>
            </BankActionBar>
          </div>
          <div className="bank-table-wrap">
            <table className="bank-table">
              <caption>인증 세션 목록</caption>
              <thead>
                <tr>
                  <th>세션번호</th>
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
                    <td colSpan={6}>세션 조회 결과가 없습니다.</td>
                  </tr>
                ) : (
                  props.sessions.map((item) => (
                    <tr key={item.sessionId}>
                      <td className="tabular-nums">{item.sessionId}</td>
                      <td>
                        <StatusBadge tone={item.currentSession ? "success" : "muted"}>
                          {item.currentSession
                            ? "현재 세션"
                            : getSessionStatusLabel(item.sessionStatus)}
                        </StatusBadge>
                      </td>
                      <td>{item.deviceName ?? "-"}</td>
                      <td className="tabular-nums">{item.ipAddress ?? "-"}</td>
                      <td className="tabular-nums">{formatDateTime(item.lastUsedAt)}</td>
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

        <BankDialog
          busy={props.isBusy}
          cancelLabel="취소"
          confirmLabel={
            confirmAction === "disableTotp" ? "OTP 해지 실행" : "전체 세션 해지 실행"
          }
          confirmTone="danger"
          onClose={() => setConfirmAction(null)}
          onConfirm={() => {
            const action = confirmAction;
            setConfirmAction(null);
            if (action === "disableTotp") {
              props.onDisableTotp();
            } else if (action === "revokeAllSessions") {
              props.onRevokeAllSessions();
            }
          }}
          open={confirmAction !== null}
          title={
            confirmAction === "disableTotp"
              ? "OTP 보안매체 해지 확인"
              : "전체 세션 해지 확인"
          }
        >
          <p className="confirm-dialog-message">
            {confirmAction === "disableTotp"
              ? "OTP 보안매체를 해지하면 현재 세션이 종료되며 다시 로그인해야 합니다. 계속하시겠습니까?"
              : "모든 기기의 로그인 세션이 즉시 만료되며 현재 기기도 로그아웃됩니다. 계속하시겠습니까?"}
          </p>
        </BankDialog>
      </>
    );
  }

  return (
    <section className="task-section compact-work-section security-section">
      {isAuthenticated
        ? renderAuthenticatedSecurityCenter()
        : renderGuestSecurityCenter()}
    </section>
  );
}
