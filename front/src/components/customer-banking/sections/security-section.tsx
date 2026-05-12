import type { FormEvent } from 'react';
import type { AuthSessionItem, BackupCodeIssueResponse, CustomerSession, LoginResponse, PasswordRecoveryRequestResult, TotpEnrollmentStartResponse } from '@/lib/api/types';
import { formatDateTime } from '@/lib/customer-banking/format';
import { BankActionBar, BankInput, BankNoticeStrip, StatusBadge, WorkStateGrid, WorkTabs } from '../common';

function stayOnCurrentWorkTab(): void {}

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
  const sensitiveValue = isAuthenticated ? undefined : "로그인 후 확인";
  const sessionExpiryState = isAuthenticated ? "만료 임박" : "로그인 후 확인";
  const currentDeviceState = !isAuthenticated
    ? "로그인 후 확인"
    : props.sessions.some((item) => item.currentSession)
      ? "현재 기기"
      : "조회 전";
  const revokeState = isAuthenticated ? "해지 가능" : "로그인 후 확인";

  return (
    <section className="task-section compact-work-section security-section">
      <div className="section-title">
        <div>
          <p>인증센터</p>
          <h1>로그인 및 보안관리</h1>
        </div>
        <BankActionBar label="인증 업무 action bar">
          <button disabled={!props.session || props.isBusy} onClick={props.onRefresh} type="button">
            토큰 재발급
          </button>
          <button disabled={!props.session || props.isBusy} onClick={props.onLogout} type="button">
            로그아웃
          </button>
        </BankActionBar>
      </div>
      <WorkTabs
        active="login"
        items={[
          { id: "login", label: "로그인", onClick: stayOnCurrentWorkTab, disabled: true },
          { id: "mfa", label: "추가 인증", onClick: stayOnCurrentWorkTab, disabled: true },
          { id: "session", label: "세션", onClick: stayOnCurrentWorkTab, disabled: true },
        ]}
      />
      <BankNoticeStrip
        items={[
          { label: "로그인 상태", value: isAuthenticated ? "로그인됨" : "로그인 필요" },
          { label: "추가 인증", value: props.challenge ? "확인 필요" : "대기" },
          {
            label: "세션",
            value: isAuthenticated
              ? formatDateTime(props.session?.refreshExpiresAt)
              : "로그인 후 확인",
          },
          { label: "보안수단", value: "OTP / 복구코드" },
          {
            label: "세션 해지 상태",
            value: isAuthenticated ? "개별/전체 해지 가능" : "로그인 후 확인",
          },
        ]}
      />
      <WorkStateGrid
        label="인증 업무 상태"
        items={[
          { label: "로그인", value: isAuthenticated ? "정상" : "필요" },
          {
            label: "추가인증",
            value: props.challenge ? getChallengeLabel(props.challenge.challengeType) : "대기",
          },
          {
            label: "세션",
            value: isAuthenticated
              ? props.sessions.length > 0
                ? `${props.sessions.length}건`
                : "조회 전"
              : "로그인 후 확인",
          },
          { label: "보안수단", value: "OTP/복구코드" },
        ]}
      />

      <div className="security-dashboard" aria-label="보안관리">
        <div>
          <span>보안관리</span>
          <strong>{isAuthenticated ? "정상" : "로그인 필요"}</strong>
          <small>인증센터</small>
        </div>
        <div>
          <span>추가 인증</span>
          <strong>{props.challenge ? getChallengeLabel(props.challenge.challengeType) : "대기"}</strong>
          <small>OTP / 복구코드</small>
        </div>
        <div>
          <span>접속관리</span>
          <strong>{isAuthenticated ? `${props.sessions.length}건` : "로그인 후 확인"}</strong>
          <small>{isAuthenticated ? "활성 세션" : "로그인 후 세션 조회 가능"}</small>
        </div>
      </div>

      <div className="security-media-grid" aria-label="보안매체 등록 상태">
        <div>
          <span>보안매체 등록</span>
          <strong>
            {activeTotpEnrollment ? "등록 진행" : isAuthenticated ? "대기" : "로그인 후 확인"}
          </strong>
          <small>OTP / 보안매체</small>
        </div>
        <div>
          <span>OTP 등록 상태</span>
          <strong>
            {activeTotpEnrollment
              ? activeTotpEnrollment.status
              : isAuthenticated
                ? "대기"
                : "로그인 후 확인"}
          </strong>
          <small>{isAuthenticated ? "등록 시작 후 인증번호 확인" : "OTP 상태 로그인 후 확인"}</small>
        </div>
        <div>
          <span>세션 해지 상태</span>
          <strong>{revokeState}</strong>
          <small>세션/기기 목록 기준</small>
        </div>
        <div>
          <span>세션 만료</span>
          <strong>{sessionExpiryState}</strong>
          <small>{isAuthenticated ? formatDateTime(props.session?.expiresAt) : "인증 후 표시"}</small>
        </div>
        <div>
          <span>현재 기기</span>
          <strong>{currentDeviceState}</strong>
          <small>세션 조회 기준</small>
        </div>
      </div>

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

      <div className="two-column">
        <form className="bank-form tight-work-form" onSubmit={props.onLogin}>
          <div className="form-heading">
            <strong>로그인</strong>
            <span>아이디와 비밀번호</span>
          </div>
          <BankInput
            autoComplete="username"
            label="고객 ID"
            onChange={(event) =>
              props.onLoginChange({
                ...props.loginForm,
                loginId: event.target.value,
              })
            }
            required
            value={props.loginForm.loginId}
          />
          <BankInput
            autoComplete="current-password"
            label="비밀번호"
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
          <BankActionBar>
            <button disabled={props.isBusy} type="submit">
              로그인
            </button>
          </BankActionBar>
        </form>

        <div className="bank-form passive">
          <div className="form-heading">
            <strong>현재 세션</strong>
            <span>{isAuthenticated ? "로그인됨" : "로그인 필요"}</span>
          </div>
          <dl className="detail-list">
            <div>
              <dt>고객번호</dt>
              <dd className={isAuthenticated ? undefined : "sensitive-placeholder"}>
                {props.session?.userId ?? sensitiveValue ?? "인증 후 표시"}
              </dd>
            </div>
            <div>
              <dt>세션 방식</dt>
              <dd className={isAuthenticated ? undefined : "sensitive-placeholder"}>
                {props.session?.tokenType ?? sensitiveValue ?? "인증 후 표시"}
              </dd>
            </div>
            <div>
              <dt>세션 만료</dt>
              <dd className={isAuthenticated ? undefined : "sensitive-placeholder"}>
                {isAuthenticated ? formatDateTime(props.session?.refreshExpiresAt) : "인증 후 표시"}
              </dd>
            </div>
          </dl>
        </div>
      </div>

      {props.challenge ? (
        <div className="two-column">
          <form className="bank-form" onSubmit={props.onTotpChallenge}>
            <div className="form-heading">
              <strong>OTP 추가 인증</strong>
              <span>{getChallengeLabel(props.challenge.challengeType)}</span>
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
      ) : null}

      <div className="two-column">
        <form className="bank-form" onSubmit={props.onPasswordRecoveryRequest}>
          <div className="form-heading">
            <strong>비밀번호 찾기</strong>
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
            <strong>복구 확정</strong>
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

      <div className="two-column">
        <form
          className="bank-form authenticated-only-form"
          data-locked={!isAuthenticated}
          onSubmit={props.onPasswordReset}
        >
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
              disabled={!isAuthenticated || props.isBusy}
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
              disabled={!isAuthenticated || props.isBusy}
              type="password"
              value={props.passwordResetForm.newPassword}
            />
          </label>
          <button disabled={!isAuthenticated || props.isBusy} type="submit">
            변경
          </button>
        </form>

        <div
          className="bank-form passive authenticated-only-form"
          data-locked={!isAuthenticated}
        >
          <div className="form-heading">
            <strong>추가 인증 관리</strong>
            <span>OTP / 복구코드</span>
          </div>
          <label>
            <span>OTP 인증번호</span>
            <input
              inputMode="numeric"
              maxLength={6}
              disabled={!isAuthenticated || props.isBusy}
              onChange={(event) => props.onTotpCodeChange(event.target.value)}
              value={props.totpCode}
            />
          </label>
          <div className="button-row compact">
            <button
              disabled={!isAuthenticated || props.isBusy}
              onClick={props.onStartTotpEnrollment}
              type="button"
            >
              등록 시작
            </button>
            <button
              disabled={!isAuthenticated || props.isBusy}
              onClick={props.onVerifyTotpEnrollment}
              type="button"
            >
              등록 확인
            </button>
            <button
              disabled={!isAuthenticated || props.isBusy}
              onClick={props.onIssueBackupCodes}
              type="button"
            >
              코드 발급
            </button>
            <button
              disabled={!isAuthenticated || props.isBusy}
              onClick={props.onDisableTotp}
              type="button"
            >
              해지
            </button>
          </div>
          {activeTotpEnrollment ? (
            <dl className="detail-list compact-list">
              <div>
                <dt>등록키</dt>
                <dd>{activeTotpEnrollment.secretKey}</dd>
              </div>
              <div>
                <dt>만료</dt>
                <dd>{formatDateTime(activeTotpEnrollment.expiresAt)}</dd>
              </div>
            </dl>
          ) : null}
          {activeBackupCodes ? (
            <div className="code-list">
              {activeBackupCodes.backupCodes.map((code) => (
                <code key={code}>{code}</code>
              ))}
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
            <button
              disabled={!isAuthenticated || props.isBusy}
              onClick={props.onLoadSessions}
              type="button"
            >
              조회
            </button>
            <button
              disabled={!isAuthenticated || props.isBusy}
              onClick={props.onRevokeAllSessions}
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
              {!isAuthenticated ? (
                <tr>
                  <td className="sensitive-placeholder" colSpan={6}>
                    로그인 후 세션 조회 가능
                  </td>
                </tr>
              ) : props.sessions.length === 0 ? (
                <tr>
                  <td colSpan={6}>세션 조회 결과가 없습니다.</td>
                </tr>
              ) : (
                props.sessions.map((item) => (
                  <tr key={item.sessionId}>
                    <td>{item.sessionId}</td>
                    <td>
                      <StatusBadge tone={item.currentSession ? "success" : "muted"}>
                        {item.currentSession ? "현재 세션" : item.sessionStatus}
                      </StatusBadge>
                    </td>
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
