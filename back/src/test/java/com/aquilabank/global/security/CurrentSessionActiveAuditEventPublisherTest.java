package com.aquilabank.global.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aquilabank.domain.auth.model.CurrentSessionActiveAuditEvent;
import com.aquilabank.domain.auth.model.CurrentSessionActiveRejectReason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CurrentSessionActiveAuditEventPublisherTest {

  private static final Instant NOW = Instant.parse("2026-04-22T01:00:00Z");
  private static final String METRIC_NAME = "aquila.auth.current_session_gate.reject.count";

  @Test
  void incrementsRejectCounterByReasonCodeOnly() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    CurrentSessionActiveAuditEventPublisher publisher =
        new CurrentSessionActiveAuditEventPublisher(registry);

    publisher.record(event(CurrentSessionActiveRejectReason.MISSING_SESSION_ID, null));
    publisher.record(event(CurrentSessionActiveRejectReason.MISSING_SESSION_ID, null));
    publisher.record(event(CurrentSessionActiveRejectReason.INACTIVE_OR_MISMATCHED_SESSION, 11L));

    assertEquals(2.0, registry.counter(METRIC_NAME, "reason_code", "MISSING_SESSION_ID").count());
    assertEquals(
        1.0,
        registry.counter(METRIC_NAME, "reason_code", "INACTIVE_OR_MISMATCHED_SESSION").count());
  }

  private CurrentSessionActiveAuditEvent event(
      CurrentSessionActiveRejectReason reasonCode, Long sessionId) {
    return new CurrentSessionActiveAuditEvent(
        "req-1", 7L, sessionId, "POST", "/api/v1/auth/password-reset", reasonCode, NOW);
  }
}
