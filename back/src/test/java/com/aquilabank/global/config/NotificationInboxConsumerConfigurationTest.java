package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aquilabank.domain.notification.port.NotificationInboxAppendPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationInboxConsumerConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(NotificationInboxConsumerConfiguration.class)
          .withBean(
              NotificationInboxAppendPort.class, () -> mock(NotificationInboxAppendPort.class))
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
