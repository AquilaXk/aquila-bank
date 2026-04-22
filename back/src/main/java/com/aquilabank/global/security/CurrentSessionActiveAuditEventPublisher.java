package com.aquilabank.global.security;

import com.aquilabank.domain.auth.model.CurrentSessionActiveAuditEvent;
import com.aquilabank.domain.auth.port.CurrentSessionActiveAuditPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 상세 차단 사유는 응답 대신 structured audit log에만 남깁니다. */
@Component
public final class CurrentSessionActiveAuditEventPublisher
    implements CurrentSessionActiveAuditPort {

  private static final Logger log =
      LoggerFactory.getLogger(CurrentSessionActiveAuditEventPublisher.class);

  @Override
  public void record(CurrentSessionActiveAuditEvent event) {
    log.warn(
        "auth current session gate rejected requestId={} userId={} sessionId={} method={} path={} reasonCode={} occurredAt={}",
        event.requestId(),
        event.userId(),
        event.sessionId() == null ? "-" : event.sessionId(),
        event.method(),
        event.path(),
        event.reasonCode().name(),
        event.occurredAt());
  }
}
