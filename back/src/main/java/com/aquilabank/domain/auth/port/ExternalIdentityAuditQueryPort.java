package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.ExternalIdentityAuditSummary;
import java.util.Optional;

/** external identity 감사 row requestId exact lookup port입니다. */
public interface ExternalIdentityAuditQueryPort {

  Optional<ExternalIdentityAuditSummary> findByRequestId(String requestId);
}
