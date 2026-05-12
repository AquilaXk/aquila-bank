"use client";

import { useState } from "react";
import { AccountsSection } from "@/components/customer-banking/sections/accounts-section";
import { BankHeader, RightRail, SideMenu } from "@/components/customer-banking/layout";
import { DashboardSection } from "@/components/customer-banking/sections/dashboard-section";
import { EnterpriseServicesSection } from "@/components/customer-banking/sections/enterprise-services-section";
import { NotificationsSection } from "@/components/customer-banking/sections/notifications-section";
import { SecurityHubSection } from "@/components/customer-banking/sections/security-hub-section";
import { SecuritySection } from "@/components/customer-banking/sections/security-section";
import { SupportCenterSection } from "@/components/customer-banking/sections/support-center-section";
import { TransactionsSection } from "@/components/customer-banking/sections/transactions-section";
import { TransferSection } from "@/components/customer-banking/sections/transfer-section";
import { useCustomerBanking } from "@/hooks/use-customer-banking";
import type { MenuSection } from "@/lib/customer-banking/types";

const publicSections: MenuSection[] = ["dashboard", "security"];

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
    setTransferForm,
    setReversalForm,
    setTransactionMode,
    setTransactionFilters,
    setNotificationMode,
    setNotificationFilters,
    setPreferences,
  } = useCustomerBanking();
  const sessionRequired =
    !session && !publicSections.includes(activeSection);
  const canRenderWorkSection = !sessionRequired;
  const [searchQuery, setSearchQuery] = useState("");
  const [serviceMapOpen, setServiceMapOpen] = useState(false);
  const [mobileSearchOpen, setMobileSearchOpen] = useState(false);

  function moveToSection(section: MenuSection) {
    setActiveSection(section);
  }

  function renderLoginRequiredWork() {
    return (
      <div
        aria-label="로그인 필요 안내"
        className="session-required login-required-work"
        role="status"
      >
        <div>
          <strong>로그인이 필요한 업무</strong>
          <small className="login-required-code">권한 만료 또는 미로그인</small>
          <span>
            조회, 이체, 거래내역, 알림, 신청 업무는 로그인 후 이용하세요.
          </span>
        </div>
        <div className="login-required-actions">
          <div className="login-method-grid" aria-label="로그인 방식">
            <button onClick={() => moveToSection("security")} type="button">
              공동인증서 로그인
            </button>
            <button onClick={() => moveToSection("security")} type="button">
              금융인증서 로그인
            </button>
            <button onClick={() => moveToSection("security")} type="button">
              아이디 로그인
            </button>
          </div>
          <button
            className="primary-login-button"
            onClick={() => moveToSection("security")}
            type="button"
          >
            인증센터 로그인
          </button>
        </div>
      </div>
    );
  }

  return (
    <main className="bank-shell dense-banking-shell gothic-banking-shell">
      <a className="skip-link" href="#bank-work-area">
        본문 바로가기
      </a>
      <BankHeader
        activeSection={activeSection}
        mobileSearchOpen={mobileSearchOpen}
        searchQuery={searchQuery}
        serviceMapOpen={serviceMapOpen}
        session={session}
        sessionRequired={sessionRequired}
        onMobileSearchToggle={() => setMobileSearchOpen((value) => !value)}
        onMove={moveToSection}
        onSearchChange={setSearchQuery}
        onServiceMapToggle={() => setServiceMapOpen((value) => !value)}
      />

      <div
        className="bank-layout mobile-priority-work"
        data-active-section={activeSection}
        data-auth-state={session ? "member" : "guest"}
      >
        <SideMenu activeSection={activeSection} onMove={moveToSection} />

        <section
          aria-live="polite"
          className="work-area"
          id="bank-work-area"
          tabIndex={-1}
        >
          <div className={`alert ${alert.type}`}>
            <strong>{alert.type === "error" ? "확인 필요" : "안내"}</strong>
            <span>{alert.text}</span>
          </div>
          {busyLabel ? (
            <div className="busy-strip" role="status">
              {busyLabel} 처리 중
            </div>
          ) : null}
          {sessionRequired ? renderLoginRequiredWork() : null}
          {activeSection === "dashboard" ? (
            <DashboardSection
              hasSession={Boolean(session)}
              onMove={moveToSection}
              onRefresh={handleRefresh}
              isBusy={isBusy}
            />
          ) : null}
          {canRenderWorkSection && activeSection === "accounts" ? (
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
          {canRenderWorkSection && activeSection === "transfer" ? (
            <TransferSection
              isBusy={isBusy}
              reversalForm={reversalForm}
              reversalResult={reversalResult}
              transferForm={transferForm}
              transferPreview={transferPreview}
              transferResult={transferResult}
              onPreviewTransfer={handlePreviewTransfer}
              onReversal={handleReversal}
              onReversalChange={setReversalForm}
              onTransfer={handleTransfer}
              onTransferChange={setTransferForm}
            />
          ) : null}
          {canRenderWorkSection && activeSection === "transactions" ? (
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
              onReset={handleResetTransactionFilters}
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
          {canRenderWorkSection && activeSection === "securityHub" ? (
            <SecurityHubSection
              applicationResult={customerApplicationResult}
              isBusy={isBusy}
              onSubmitCustomerApplication={handleSubmitCustomerApplication}
            />
          ) : null}
          {canRenderWorkSection && activeSection === "supportCenter" ? (
            <SupportCenterSection
              applicationResult={customerApplicationResult}
              isBusy={isBusy}
              onSubmitCustomerApplication={handleSubmitCustomerApplication}
            />
          ) : null}
          {canRenderWorkSection && activeSection === "enterpriseServices" ? (
            <EnterpriseServicesSection
              applicationResult={customerApplicationResult}
              isBusy={isBusy}
              onSubmitCustomerApplication={handleSubmitCustomerApplication}
            />
          ) : null}
          {canRenderWorkSection && activeSection === "notifications" ? (
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

        <RightRail
          isBusy={isBusy}
          session={session}
          onMove={moveToSection}
          onRefresh={handleRefresh}
        />
      </div>
    </main>
  );
}
