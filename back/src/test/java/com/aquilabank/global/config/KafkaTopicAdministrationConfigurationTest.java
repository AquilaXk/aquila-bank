package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaAdmin;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class KafkaTopicAdministrationConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(KafkaTopicAdministrationConfiguration.class);

  @Test
  void provisionsConfiguredTopicsWhenProvisioningIsEnabled() {
    String suffix = UUID.randomUUID().toString().replace("-", "");
    String outboxTopic = "bank.topic.provisioning.outbox." + suffix;
    String transferBookedTopic = "bank.topic.provisioning.transfer-booked." + suffix;
    String transferReversedTopic = "bank.topic.provisioning.transfer-reversed." + suffix;
    String dlqTopic = "bank.topic.provisioning.transfer-booked.dlq." + suffix;

    contextRunner
        .withPropertyValues(
            "outbox.kafka.enabled=true",
            "outbox.kafka.bootstrap-servers=" + KafkaTopicTestSupport.kafkaBootstrapServers(),
            "outbox.kafka.topic.default-name=" + outboxTopic,
            "outbox.kafka.topic.event-type-overrides.TransferBooked=" + transferBookedTopic,
            "outbox.kafka.topic.event-type-overrides.TransferReversed=" + transferReversedTopic,
            "notification.inbox.consumer.enabled=true",
            "notification.inbox.consumer.bootstrap-servers="
                + KafkaTopicTestSupport.kafkaBootstrapServers(),
            "notification.inbox.consumer.transfer-booked.topic=" + transferBookedTopic,
            "notification.inbox.consumer.dlq.topic=" + dlqTopic,
            "kafka.topic.provisioning.enabled=true",
            "kafka.topic.startup-validation.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(listTopics())
                  .contains(outboxTopic, transferBookedTopic, transferReversedTopic, dlqTopic);
            });
  }

  @Test
  void failsStartupValidationWhenProvisioningIsDisabledAndTopicIsMissing() {
    String suffix = UUID.randomUUID().toString().replace("-", "");
    String missingTopic = "bank.topic.validation.missing." + suffix;

    contextRunner
        .withPropertyValues(
            "outbox.kafka.enabled=true",
            "outbox.kafka.bootstrap-servers=" + KafkaTopicTestSupport.kafkaBootstrapServers(),
            "outbox.kafka.topic.default-name=" + missingTopic,
            "kafka.topic.provisioning.enabled=false",
            "kafka.topic.startup-validation.enabled=true")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasMessageContaining("Kafka topic startup validation failed");
            });
  }

  @Test
  void failsWhenOutboxAndNotificationBootstrapServersDiffer() {
    contextRunner
        .withPropertyValues(
            "outbox.kafka.enabled=true",
            "outbox.kafka.bootstrap-servers=localhost:19092",
            "outbox.kafka.topic.default-name=bank.topic.mismatch.outbox",
            "notification.inbox.consumer.enabled=true",
            "notification.inbox.consumer.bootstrap-servers=localhost:29092",
            "notification.inbox.consumer.transfer-booked.topic=bank.topic.mismatch.transfer-booked",
            "kafka.topic.provisioning.enabled=true",
            "kafka.topic.startup-validation.enabled=false")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasMessageContaining("Kafka bootstrap servers must match across integrations");
            });
  }

  @Test
  void skipsNotificationTopicAdministrationWhenConsumerDoesNotStart() {
    contextRunner
        .withPropertyValues(
            "notification.inbox.consumer.enabled=true",
            "notification.inbox.consumer.auto-startup=false",
            "notification.inbox.consumer.bootstrap-servers=localhost:9092",
            "notification.inbox.consumer.transfer-booked.topic=bank.topic.skip.transfer-booked",
            "kafka.topic.provisioning.enabled=true",
            "kafka.topic.startup-validation.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(KafkaTopicTopology.class);
              assertThat(context).doesNotHaveBean(KafkaAdmin.class);
            });
  }

  private Set<String> listTopics() {
    try (AdminClient adminClient =
        AdminClient.create(
            Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                KafkaTopicTestSupport.kafkaBootstrapServers()))) {
      return adminClient.listTopics().names().get();
    } catch (Exception ex) {
      throw new IllegalStateException("failed to list Kafka topics in test", ex);
    }
  }
}
