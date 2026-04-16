package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSummary;
import java.util.Optional;

/** 내부 auth 감사 exact lookup read path입니다. */
public interface AuthStatusChangeAuditQueryPort {

  Optional<AuthStatusChangeAuditSummary> findByRequestId(String requestId);
}
