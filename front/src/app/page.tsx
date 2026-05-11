"use client";

import { AccountsSection } from "@/components/customer-banking/sections/accounts-section";
import { DashboardSection } from "@/components/customer-banking/sections/dashboard-section";
import { NotificationsSection } from "@/components/customer-banking/sections/notifications-section";
import { SecuritySection } from "@/components/customer-banking/sections/security-section";
import { TransactionsSection } from "@/components/customer-banking/sections/transactions-section";
import { TransferSection } from "@/components/customer-banking/sections/transfer-section";
import { useCustomerBanking } from "@/hooks/use-customer-banking";
import { mainMenus, quickMenus } from "@/lib/customer-banking/constants";
import { formatDateTime, maskToken } from "@/lib/customer-banking/format";

export default function HomePage() {
  const {
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
    reversalForm,
    reversalResult,
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
    handleReversal,
    handleSearchTransactions,
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
    setTransferForm,
    setReversalForm,
    setTransactionMode,
    setTransactionFilters,
    setNotificationMode,
    setNotificationFilters,
    setPreferences,
  } = useCustomerBanking();

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
