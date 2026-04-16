package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.AuthStatusChangeAuditNotFoundException;
import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSummary;
import com.aquilabank.domain.auth.port.AuthStatusChangeAuditQueryPort;

/** requestId exact lookup 결과를 domain 예외와 함께 고정합니다. */
public final class AuthStatusChangeAuditQueryService implements AuthStatusChangeAuditQueryUseCase {

  private final AuthStatusChangeAuditQueryPort authStatusChangeAuditQueryPort;

  public AuthStatusChangeAuditQueryService(
      AuthStatusChangeAuditQueryPort authStatusChangeAuditQueryPort) {
    this.authStatusChangeAuditQueryPort = authStatusChangeAuditQueryPort;
  }

  @Override
  public AuthStatusChangeAuditSummary getByRequestId(String requestId) {
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    return authStatusChangeAuditQueryPort
        .findByRequestId(requestId)
        .orElseThrow(() -> new AuthStatusChangeAuditNotFoundException("audit record is not found"));
  }
}
