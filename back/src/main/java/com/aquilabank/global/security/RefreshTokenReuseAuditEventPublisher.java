package com.aquilabank.global.security;

import com.aquilabank.domain.auth.model.RefreshTokenReuseAuditEvent;
import com.aquilabank.domain.auth.port.RefreshTokenReuseAuditPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** refresh token reuse 상세는 응답 대신 structured audit log에만 남깁니다. */
@Component
public final class RefreshTokenReuseAuditEventPublisher implements RefreshTokenReuseAuditPort {

  private static final Logger log =
      LoggerFactory.getLogger(RefreshTokenReuseAuditEventPublisher.class);

  @Override
  public void record(RefreshTokenReuseAuditEvent event) {
    log.warn(
        "auth refresh token reuse detected requestId={} userId={} reusedSessionId={} familyRootId={} replacedBySessionId={} revokedCount={} deviceName={} ipAddress={} reasonCode={} occurredAt={}",
        event.requestId(),
        event.userId(),
        event.reusedSessionId(),
        event.familyRootId(),
        event.replacedBySessionId() == null ? "-" : event.replacedBySessionId(),
        event.revokedCount(),
        event.deviceName(),
        event.ipAddress(),
        event.reasonCode().name(),
        event.occurredAt());
  }
}
