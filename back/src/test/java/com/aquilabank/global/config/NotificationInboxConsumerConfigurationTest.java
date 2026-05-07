package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aquilabank.domain.notification.port.NotificationDlqRedriveAuditPort;
import com.aquilabank.domain.notification.port.NotificationInboxAppendPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

class NotificationInboxConsumerConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(NotificationInboxConsumerConfiguration.class)
          .withBean(
              NotificationInboxAppendPort.class, () -> mock(NotificationInboxAppendPort.class))
          .withBean(
              NotificationDlqRedriveAuditPort.class,
              () -> mock(NotificationDlqRedriveAuditPort.class))
          .withBean(ObjectMapper.class, ObjectMapper::new);

  @Test
  void doesNotCreateKafkaConsumerBeansWhenConsumerIsDisabled() {
    contextRunner
        .withPropertyValues("notification.inbox.consumer.enabled=false")
        .run(
            context -> {
              assertThat(context)
                  .hasSingleBean(
                      com.aquilabank.domain.notification.usecase.NotificationInboxIngestUseCase
                          .class);
              assertThat(context).doesNotHaveBean("notificationInboxConsumerFactory");
              assertThat(context).doesNotHaveBean("transferBookedNotificationConsumer");
              assertThat(context).doesNotHaveBean("transferReversedNotificationConsumer");
            });
  }

  @Test
  void createsKafkaConsumerBeansWhenRequiredPropertiesArePresent() {
    contextRunner
        .withPropertyValues(
            "notification.inbox.consumer.enabled=true",
            "notification.inbox.consumer.bootstrap-servers=localhost:9092",
            "notification.inbox.consumer.transfer-booked.topic=bank.transfer.booked.v1")
        .run(
            context -> {
              assertThat(context).hasBean("notificationInboxConsumerFactory");
              assertThat(context).hasBean("notificationInboxKafkaListenerContainerFactory");
              assertThat(context).hasBean("transferBookedNotificationConsumer");
              assertThat(context).doesNotHaveBean("transferReversedNotificationConsumer");
            });
  }

  @Test
  void failsFastWhenConsumerIsEnabledButBootstrapServersAreMissing() {
    contextRunner
        .withPropertyValues(
            "notification.inbox.consumer.enabled=true",
            "notification.inbox.consumer.transfer-booked.topic=bank.transfer.booked.v1")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "notification.inbox.consumer.bootstrap-servers is required when notification.inbox.consumer.enabled=true");
            });
  }

  @Test
  void failsFastWhenOpsIsEnabledButDlqTopicIsMissing() {
    contextRunner
        .withPropertyValues(
            "notification.inbox.consumer.enabled=true",
            "notification.inbox.consumer.bootstrap-servers=localhost:9092",
            "notification.inbox.consumer.transfer-booked.topic=bank.transfer.booked.v1",
            "notification.inbox.consumer.ops.enabled=true")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "notification.inbox.consumer.dlq.topic is required when notification.inbox.consumer.ops.enabled=true");
            });
  }

  @Test
  void appliesConfiguredConsumerConcurrencyToListenerFactory() {
    contextRunner
        .withPropertyValues(
            "notification.inbox.consumer.enabled=true",
            "notification.inbox.consumer.bootstrap-servers=localhost:9092",
            "notification.inbox.consumer.transfer-booked.topic=bank.transfer.booked.v1",
            "notification.inbox.consumer.concurrency=3")
        .run(
            context -> {
              ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                  context.getBean(
                      "notificationInboxKafkaListenerContainerFactory",
                      ConcurrentKafkaListenerContainerFactory.class);

              assertThat(new DirectFieldAccessor(factory).getPropertyValue("concurrency"))
                  .isEqualTo(3);
            });
  }

  @Test
  void createsTransferReversedConsumerWhenOnlyReversalTopicIsPresent() {
    contextRunner
        .withPropertyValues(
            "notification.inbox.consumer.enabled=true",
            "notification.inbox.consumer.bootstrap-servers=localhost:9092",
            "notification.inbox.consumer.transfer-reversed.topic=bank.transfer.reversed.v1")
        .run(
            context -> {
              assertThat(context).hasBean("notificationInboxConsumerFactory");
              assertThat(context).hasBean("notificationInboxKafkaListenerContainerFactory");
              assertThat(context).doesNotHaveBean("transferBookedNotificationConsumer");
              assertThat(context).hasBean("transferReversedNotificationConsumer");
            });
  }

  @Test
  void createsNotificationOpsBeansWhenDlqAndOpsPropertiesArePresent() {
    contextRunner
        .withPropertyValues(
            "notification.inbox.consumer.enabled=true",
            "notification.inbox.consumer.bootstrap-servers=localhost:9092",
            "notification.inbox.consumer.group-id=test-notification-ops",
            "notification.inbox.consumer.transfer-booked.topic=bank.transfer.booked.v1",
            "notification.inbox.consumer.dlq.topic=bank.transfer.booked.dlq.v1",
            "notification.inbox.consumer.ops.enabled=true")
        .run(
            context -> {
              assertThat(context).hasBean("notificationInboxDlqProducerFactory");
              assertThat(context).hasBean("notificationInboxDlqKafkaTemplate");
              assertThat(context).hasBean("notificationOpsReadPort");
              assertThat(context).hasBean("notificationOpsQueryUseCase");
              assertThat(context).hasBean("notificationOpsRecoveryPort");
              assertThat(context).hasBean("notificationOpsRecoveryUseCase");
              assertThat(context).hasBean("notificationInboxHealthIndicator");
            });
  }
}
