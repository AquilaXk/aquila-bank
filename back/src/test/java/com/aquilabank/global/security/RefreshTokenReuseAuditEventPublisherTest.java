package com.aquilabank.global.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aquilabank.domain.auth.model.RefreshTokenReuseAuditEvent;
import com.aquilabank.domain.auth.model.RefreshTokenReuseReason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshTokenReuseAuditEventPublisherTest {

  private static final Instant NOW = Instant.parse("2026-04-22T02:00:00Z");
  private static final String METRIC_NAME = "aquila.auth.refresh_token_reuse.detected.count";

  @Test
  void incrementsReuseCounterByReasonCodeOnly() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    RefreshTokenReuseAuditEventPublisher publisher =
        new RefreshTokenReuseAuditEventPublisher(registry);

    publisher.record(event());
    publisher.record(event());

    assertEquals(2.0, registry.counter(METRIC_NAME, "reason_code", "ROTATED_TOKEN_REUSE").count());
  }

  private RefreshTokenReuseAuditEvent event() {
    return new RefreshTokenReuseAuditEvent(
        "req-1",
        7L,
        11L,
        3L,
        33L,
        1,
        "Windows / Chrome",
        "203.0.113.10",
        RefreshTokenReuseReason.ROTATED_TOKEN_REUSE,
        NOW);
  }
}
