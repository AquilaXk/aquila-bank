package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSearchQuery;
import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSearchResult;
import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSummary;
import java.util.Optional;

/** 내부 auth 감사 exact lookup read path입니다. */
public interface AuthStatusChangeAuditQueryPort {

  Optional<AuthStatusChangeAuditSummary> findByRequestId(String requestId);

  default AuthStatusChangeAuditSearchResult search(AuthStatusChangeAuditSearchQuery query) {
    throw new UnsupportedOperationException("auth status change audit search is not implemented");
  }
}
