import type { FormEvent } from 'react';
import type { AuthSessionItem, BackupCodeIssueResponse, CustomerSession, LoginResponse, PasswordRecoveryRequestResult, TotpEnrollmentStartResponse } from '@/lib/api/types';
import { formatDateTime } from '@/lib/customer-banking/format';

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
              <dt>세션 방식</dt>
              <dd>{props.session?.tokenType ?? "-"}</dd>
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
