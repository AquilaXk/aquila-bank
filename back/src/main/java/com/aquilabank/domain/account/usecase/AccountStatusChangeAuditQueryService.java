package com.aquilabank.domain.account.usecase;

import com.aquilabank.domain.account.exception.AccountStatusChangeAuditNotFoundException;
import com.aquilabank.domain.account.model.AccountStatusChangeAuditSummary;
import com.aquilabank.domain.account.port.AccountStatusChangeAuditQueryPort;

/** 계좌 상태 변경 감사 requestId exact lookup을 not found 예외와 함께 고정합니다. */
public final class AccountStatusChangeAuditQueryService
    implements AccountStatusChangeAuditQueryUseCase {

  private final AccountStatusChangeAuditQueryPort accountStatusChangeAuditQueryPort;

  public AccountStatusChangeAuditQueryService(
      AccountStatusChangeAuditQueryPort accountStatusChangeAuditQueryPort) {
    this.accountStatusChangeAuditQueryPort = accountStatusChangeAuditQueryPort;
  }

  @Override
  public AccountStatusChangeAuditSummary getByRequestId(String requestId) {
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    return accountStatusChangeAuditQueryPort
        .findByRequestId(requestId)
        .orElseThrow(
            () -> new AccountStatusChangeAuditNotFoundException("audit record is not found"));
  }
}
