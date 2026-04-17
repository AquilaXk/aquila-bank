package com.aquilabank.global.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import com.aquilabank.domain.notification.port.OutboxEventPublishPort;
import com.aquilabank.domain.notification.port.OutboxEventStore;
import com.aquilabank.domain.notification.port.OutboxOpsReadPort;
import com.aquilabank.domain.notification.port.OutboxOpsRecoveryPort;
import com.aquilabank.global.notification.KafkaOutboxEventPublisher;
import com.aquilabank.global.notification.LoggingOutboxEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OutboxConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(OutboxConfiguration.class)
          .withBean(OutboxEventStore.class, () -> mock(OutboxEventStore.class))
          .withBean(OutboxOpsReadPort.class, () -> mock(OutboxOpsReadPort.class))
          .withBean(OutboxOpsRecoveryPort.class, () -> mock(OutboxOpsRecoveryPort.class))
          .withPropertyValues(
              "outbox.poller.enabled=false",
              "outbox.poller.fixed-delay-ms=1000",
              "outbox.poller.initial-delay-ms=3000",
              "outbox.poller.batch-size=20",
              "outbox.poller.stale-after-seconds=30",
              "outbox.poller.max-retry-delay-seconds=60",
              "outbox.ops.enabled=false",
              "outbox.ops.token-header=X-Outbox-Ops-Token",
              "outbox.ops.failed-list-limit=20",
              "outbox.ops.health.max-lag-seconds=120",
              "outbox.ops.health.max-failed-count=10",
              "outbox.ops.health.max-stale-sending-count=0");

  @Test
  void usesLoggingFallbackWhenKafkaIsDisabled() {
    contextRunner
        .withPropertyValues("outbox.kafka.enabled=false")
        .run(
            context ->
                assertInstanceOf(
                    LoggingOutboxEventPublisher.class,
                    context.getBean("outboxEventPublishPort", OutboxEventPublishPort.class)));
  }

  @Test
  void usesKafkaPublisherWhenRequiredKafkaPropertiesArePresent() {
    contextRunner
        .withPropertyValues(
            "outbox.kafka.enabled=true",
            "outbox.kafka.bootstrap-servers=localhost:9092",
            "outbox.kafka.topic.default-name=bank.notification.outbox.v1")
        .run(
            context ->
                assertInstanceOf(
                    KafkaOutboxEventPublisher.class,
                    context.getBean("outboxEventPublishPort", OutboxEventPublishPort.class)));
  }

  @Test
  void usesLoggingFallbackWhenKafkaIsEnabledButRequiredValuesAreMissing() {
    contextRunner
        .withPropertyValues("outbox.kafka.enabled=true")
        .run(
            context ->
                assertInstanceOf(
                    LoggingOutboxEventPublisher.class,
                    context.getBean("outboxEventPublishPort", OutboxEventPublishPort.class)));
  }
}
