package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSearchQuery;
import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSearchResult;
import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSummary;

public interface AuthStatusChangeAuditQueryUseCase {

  AuthStatusChangeAuditSummary getByRequestId(String requestId);

  AuthStatusChangeAuditSearchResult search(AuthStatusChangeAuditSearchQuery query);
}
