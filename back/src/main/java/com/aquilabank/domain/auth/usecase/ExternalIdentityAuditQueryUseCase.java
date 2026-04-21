package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.ExternalIdentityAuditSummary;

public interface ExternalIdentityAuditQueryUseCase {

  ExternalIdentityAuditSummary getByRequestId(String requestId);
}
