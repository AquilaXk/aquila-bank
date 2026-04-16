package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSummary;

public interface AuthStatusChangeAuditQueryUseCase {

  AuthStatusChangeAuditSummary getByRequestId(String requestId);
}
