package com.aquilabank.domain.account.usecase;

import com.aquilabank.domain.account.model.AccountStatusChangeAuditSummary;

public interface AccountStatusChangeAuditQueryUseCase {

  AccountStatusChangeAuditSummary getByRequestId(String requestId);
}
