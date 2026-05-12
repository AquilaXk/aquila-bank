"use client";

import { useState } from "react";
import Image from "next/image";
import { AccountsSection } from "@/components/customer-banking/sections/accounts-section";
import { BankNoticeStrip } from "@/components/customer-banking/common";
import { DashboardSection } from "@/components/customer-banking/sections/dashboard-section";
import { EnterpriseServicesSection } from "@/components/customer-banking/sections/enterprise-services-section";
import { NotificationsSection } from "@/components/customer-banking/sections/notifications-section";
import { SecurityHubSection } from "@/components/customer-banking/sections/security-hub-section";
import { SecuritySection } from "@/components/customer-banking/sections/security-section";
import { SupportCenterSection } from "@/components/customer-banking/sections/support-center-section";
import { TransactionsSection } from "@/components/customer-banking/sections/transactions-section";
import { TransferSection } from "@/components/customer-banking/sections/transfer-section";
import { useCustomerBanking } from "@/hooks/use-customer-banking";
import {
  mainMenus,
  noticeItems,
  quickMenus,
  recentMenus,
  recommendedKeywords,
  serviceMapGroups,
} from "@/lib/customer-banking/constants";
import { formatDateTime } from "@/lib/customer-banking/format";
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

  function moveToSection(section: MenuSection) {
    setActiveSection(section);
  }

  function moveFromServiceMap(section: MenuSection) {
    setServiceMapOpen(false);
    moveToSection(section);
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
          <small className="login-required-code">권한 만료/미로그인</small>
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
    <main className="bank-shell">
      <a className="skip-link" href="#bank-work-area">
        본문 바로가기
      </a>
      <header className="bank-header">
        <div className="utility-bar" aria-label="상단 유틸리티">
          <div className="utility-left">
            <button className="utility-link active" type="button">
              개인
            </button>
            <button className="utility-link" type="button">
              기업
            </button>
            <button
              className="utility-link"
              onClick={() => moveToSection("security")}
              type="button"
            >
              인증센터
            </button>
            <button
              className="utility-link"
              onClick={() => moveToSection("supportCenter")}
              type="button"
            >
              고객센터
            </button>
          </div>
          <div className="utility-right">
            <button
              aria-controls="service-map-panel"
              aria-expanded={serviceMapOpen}
              className="utility-link service-map"
              onClick={() => setServiceMapOpen((value) => !value)}
              type="button"
            >
              전체서비스
            </button>
            <div className="utility-search">
              <label className="search-field">
                <span>통합검색</span>
                <input
                  aria-label="통합검색"
                  onChange={(event) => setSearchQuery(event.target.value)}
                  placeholder="업무명 또는 메뉴 검색"
                  value={searchQuery}
                />
              </label>
              <div className="keyword-list" aria-label="추천검색어">
                <span>추천검색어</span>
                {recommendedKeywords.map((keyword) => (
                  <button
                    key={keyword}
                    onClick={() => setSearchQuery(keyword)}
                    type="button"
                  >
                    {keyword}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
        <div className="brand-row">
          <div className="brand-mark" aria-label="Aquila Bank">
            <span className="brand-symbol" aria-hidden="true">
              <Image
                alt=""
                height={34}
                priority
                src="/brand-mascot.png"
                width={34}
              />
            </span>
            <div>
              <strong>Aquila Bank</strong>
              <small>Personal Internet Banking</small>
            </div>
          </div>
          <nav className="primary-nav" aria-label="주요 메뉴">
            {mainMenus.map((item) => (
              <button
                aria-current={activeSection === item.id ? "page" : undefined}
                className={activeSection === item.id ? "nav-tab active" : "nav-tab"}
                key={item.id}
                onClick={() => moveToSection(item.id)}
                type="button"
              >
                {item.label}
              </button>
            ))}
          </nav>
        </div>
        <div className="bank-service-strip" aria-label="뱅킹 이용 상태">
          <span>
            보안등급 <strong>정상</strong>
          </span>
          <span>HTTPS 보안접속</span>
          <span>평일 09:00-18:00 상담</span>
          <span>서비스 상태 정상</span>
        </div>
        <div className="layout-health-strip" aria-label="화면 이용 상태">
          <span>개인뱅킹</span>
          <span>{session ? "로그인 정상" : "미로그인"}</span>
          <span>{sessionRequired ? "로그인 필요" : "업무 가능"}</span>
        </div>
        {serviceMapOpen ? (
          <section
            aria-label="전체서비스 메뉴"
            className="service-map-panel"
            id="service-map-panel"
          >
            <div className="service-map-title">
              <strong>전체서비스 메뉴</strong>
              <span>개인뱅킹</span>
            </div>
            <div className="service-map-grid">
              {serviceMapGroups.map((group) => (
                <div className="service-map-group" key={group.title}>
                  <strong>{group.title}</strong>
                  {group.items.map((item) => (
                    <button
                      key={`${group.title}-${item.label}`}
                      onClick={() => moveFromServiceMap(item.section)}
                      type="button"
                    >
                      {item.label}
                    </button>
                  ))}
                </div>
              ))}
            </div>
          </section>
        ) : null}
      </header>

      <div
        className="bank-layout"
        data-active-section={activeSection}
        data-auth-state={session ? "member" : "guest"}
      >
        <aside className="side-menu" aria-label="개인뱅킹 메뉴">
          <div className="side-title">개인뱅킹</div>
          {mainMenus.map((item) => (
            <button
              aria-current={activeSection === item.id ? "page" : undefined}
              className={activeSection === item.id ? "side-item active" : "side-item"}
              key={item.id}
              onClick={() => moveToSection(item.id)}
              type="button"
            >
              <span>{item.group}</span>
              <strong>{item.label}</strong>
            </button>
          ))}
        </aside>

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
                  <dt>세션 방식</dt>
                  <dd>{session.tokenType}</dd>
                </div>
                <div>
                  <dt>만료</dt>
                  <dd>{formatDateTime(session.expiresAt)}</dd>
                </div>
              </dl>
            ) : (
              <p className="rail-copy">로그인 후 조회, 이체, 거래내역 이용 가능</p>
            )}
            <div className="button-row compact rail-auth-actions">
              <button onClick={() => moveToSection("security")} type="button">
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
                <button
                  key={item.label}
                  onClick={() => moveToSection(item.section)}
                  type="button"
                >
                  {item.label}
                </button>
              ))}
            </div>
          </section>

          <section className="rail-panel recent-list">
            <div className="rail-heading">
              <span>최근 이용 메뉴</span>
            </div>
            <div className="recent-menu-list">
              {recentMenus.map((item) => (
                <button
                  key={item.label}
                  onClick={() => moveToSection(item.section)}
                  type="button"
                >
                  {item.label}
                </button>
              ))}
            </div>
          </section>

          <section className="rail-panel security-notice">
            <div className="rail-heading">
              <span>보안알림</span>
            </div>
            <BankNoticeStrip
              items={[
                { label: "보안등급", value: "정상" },
                { label: "접속상태", value: "HTTPS" },
                {
                  label: "인증수단",
                  value: session ? "세션 활성" : "로그인 필요",
                },
              ]}
            />
          </section>

          <section className="rail-panel notice-list">
            <div className="rail-heading">
              <span>공지사항</span>
            </div>
            <ul>
              {noticeItems.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </section>
        </aside>
      </div>
    </main>
  );
}
