package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.ExternalIdentityAuditNotFoundException;
import com.aquilabank.domain.auth.model.ExternalIdentityAuditSummary;
import com.aquilabank.domain.auth.port.ExternalIdentityAuditQueryPort;

public final class ExternalIdentityAuditQueryService implements ExternalIdentityAuditQueryUseCase {

  private final ExternalIdentityAuditQueryPort externalIdentityAuditQueryPort;

  public ExternalIdentityAuditQueryService(
      ExternalIdentityAuditQueryPort externalIdentityAuditQueryPort) {
    this.externalIdentityAuditQueryPort = externalIdentityAuditQueryPort;
  }

  @Override
  public ExternalIdentityAuditSummary getByRequestId(String requestId) {
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    return externalIdentityAuditQueryPort
        .findByRequestId(requestId)
        .orElseThrow(
            () ->
                new ExternalIdentityAuditNotFoundException("external identity audit is not found"));
  }
}
