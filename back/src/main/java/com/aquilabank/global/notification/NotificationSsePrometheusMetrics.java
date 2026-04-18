package com.aquilabank.global.notification;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/** SSE broker 메모리 상태를 그대로 gauge로 노출해 운영자가 연결 수를 바로 확인하게 합니다. */
@Component
public final class NotificationSsePrometheusMetrics implements MeterBinder {

  private final NotificationSseBroker notificationSseBroker;

  public NotificationSsePrometheusMetrics(NotificationSseBroker notificationSseBroker) {
    this.notificationSseBroker = notificationSseBroker;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder(
            "aquila.notification.sse.sessions",
            notificationSseBroker,
            broker -> broker.accountSessionCount())
        .tag("principal_type", "account")
        .description("active notification SSE session count")
        .register(registry);
    Gauge.builder(
            "aquila.notification.sse.sessions",
            notificationSseBroker,
            broker -> broker.userSessionCount())
        .tag("principal_type", "user")
        .description("active notification SSE session count")
        .register(registry);
    Gauge.builder(
            "aquila.notification.sse.sessions",
            notificationSseBroker,
            broker -> broker.totalSessionCount())
        .tag("principal_type", "total")
        .description("active notification SSE session count")
        .register(registry);
    Gauge.builder(
            "aquila.notification.sse.subscription.rejected.count",
            notificationSseBroker,
            broker -> broker.rejectedSubscriptionCount())
        .tag("reason", "session_limit")
        .description("rejected notification SSE subscription count")
        .register(registry);
    Gauge.builder(
            "aquila.notification.sse.session.dropped.count",
            notificationSseBroker,
            broker -> broker.backpressureDropCount())
        .tag("reason", "pending_overflow")
        .description("dropped notification SSE session count")
        .register(registry);
  }
}
