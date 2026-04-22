package com.aquilabank.global.security;

import com.aquilabank.domain.auth.model.RefreshTokenReuseAuditEvent;
import com.aquilabank.domain.auth.model.RefreshTokenReuseReason;
import com.aquilabank.domain.auth.port.RefreshTokenReuseAuditPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** refresh token reuse 상세는 응답 대신 structured audit log에만 남깁니다. */
@Component
public final class RefreshTokenReuseAuditEventPublisher implements RefreshTokenReuseAuditPort {

  private static final Logger log =
      LoggerFactory.getLogger(RefreshTokenReuseAuditEventPublisher.class);
  private static final String REUSE_COUNTER_NAME = "aquila.auth.refresh_token_reuse.detected.count";

  private final Map<RefreshTokenReuseReason, Counter> reuseCounters;

  public RefreshTokenReuseAuditEventPublisher(MeterRegistry meterRegistry) {
    this.reuseCounters = new EnumMap<>(RefreshTokenReuseReason.class);
    for (RefreshTokenReuseReason reason : RefreshTokenReuseReason.values()) {
      reuseCounters.put(
          reason,
          Counter.builder(REUSE_COUNTER_NAME)
              .tag("reason_code", reason.name())
              .description("refresh token reuse detected count")
              .register(meterRegistry));
    }
  }

  @Override
  public void record(RefreshTokenReuseAuditEvent event) {
    reuseCounters.get(event.reasonCode()).increment();
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
