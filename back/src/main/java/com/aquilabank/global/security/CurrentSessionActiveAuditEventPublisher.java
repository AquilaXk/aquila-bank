package com.aquilabank.global.security;

import com.aquilabank.domain.auth.model.CurrentSessionActiveAuditEvent;
import com.aquilabank.domain.auth.model.CurrentSessionActiveRejectReason;
import com.aquilabank.domain.auth.port.CurrentSessionActiveAuditPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 상세 차단 사유는 응답 대신 structured audit log에만 남깁니다. */
@Component
public final class CurrentSessionActiveAuditEventPublisher
    implements CurrentSessionActiveAuditPort {

  private static final Logger log =
      LoggerFactory.getLogger(CurrentSessionActiveAuditEventPublisher.class);
  private static final String REJECT_COUNTER_NAME = "aquila.auth.current_session_gate.reject.count";

  private final Map<CurrentSessionActiveRejectReason, Counter> rejectCounters;

  public CurrentSessionActiveAuditEventPublisher(MeterRegistry meterRegistry) {
    this.rejectCounters = new EnumMap<>(CurrentSessionActiveRejectReason.class);
    for (CurrentSessionActiveRejectReason reason : CurrentSessionActiveRejectReason.values()) {
      rejectCounters.put(
          reason,
          Counter.builder(REJECT_COUNTER_NAME)
              .tag("reason_code", reason.name())
              .description("current session active gate rejected request count")
              .register(meterRegistry));
    }
  }

  @Override
  public void record(CurrentSessionActiveAuditEvent event) {
    rejectCounters.get(event.reasonCode()).increment();
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
