package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.RefreshTokenReuseAuditEvent;

public interface RefreshTokenReuseAuditPort {

  void record(RefreshTokenReuseAuditEvent event);
}
