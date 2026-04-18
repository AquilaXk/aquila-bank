package com.aquilabank.global.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.util.StringUtils;

/** Kafka topic provisioning 과 startup validation 을 설정값 기준으로 묶습니다. */
@Configuration
@EnableConfigurationProperties({
  KafkaTopicAdministrationProperties.class,
  OutboxKafkaProperties.class,
  NotificationInboxConsumerProperties.class
})
public class KafkaTopicAdministrationConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(KafkaTopicAdministrationConfiguration.class);

  @Bean
  @Conditional(KafkaTopicAdministrationCondition.class)
  KafkaTopicTopology kafkaTopicTopology(
      OutboxKafkaProperties outboxKafkaProperties,
      NotificationInboxConsumerProperties notificationInboxConsumerProperties,
      KafkaTopicAdministrationProperties kafkaTopicAdministrationProperties) {
    return new KafkaTopicTopologyResolver(kafkaTopicAdministrationProperties)
        .resolve(outboxKafkaProperties, notificationInboxConsumerProperties);
  }

  @Bean
  @Conditional(KafkaTopicAdministrationCondition.class)
  KafkaAdmin kafkaTopicAdmin(KafkaTopicTopology kafkaTopicTopology) {
    KafkaAdmin kafkaAdmin =
        new KafkaAdmin(
            Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaTopicTopology.bootstrapServers()));
    kafkaAdmin.setFatalIfBrokerNotAvailable(true);
    return kafkaAdmin;
  }

  @Bean
  @Conditional(KafkaTopicProvisioningCondition.class)
  KafkaAdmin.NewTopics kafkaProvisioningTopics(KafkaTopicTopology kafkaTopicTopology) {
    return new KafkaAdmin.NewTopics(
        kafkaTopicTopology.topics().stream()
            .map(
                topic ->
                    TopicBuilder.name(topic.name())
                        .partitions(topic.partitions())
                        .replicas(topic.replicationFactor())
                        .build())
            .toArray(org.apache.kafka.clients.admin.NewTopic[]::new));
  }

  @Bean
  @Conditional(KafkaTopicStartupValidationCondition.class)
  InitializingBean kafkaTopicStartupValidationInitializer(
      KafkaAdmin kafkaTopicAdmin, KafkaTopicTopology kafkaTopicTopology) {
    return () -> {
      kafkaTopicAdmin.initialize();
      validateTopics(kafkaTopicTopology);
    };
  }

  private void validateTopics(KafkaTopicTopology kafkaTopicTopology) {
    try (AdminClient adminClient =
        AdminClient.create(
            Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaTopicTopology.bootstrapServers()))) {
      Map<String, KafkaFuture<TopicDescription>> describeResults =
          adminClient
              .describeTopics(
                  kafkaTopicTopology.topics().stream().map(KafkaTopicSpec::name).toList())
              .topicNameValues();
      for (KafkaTopicSpec topic : kafkaTopicTopology.topics()) {
        try {
          TopicDescription description = describeResults.get(topic.name()).get(5, TimeUnit.SECONDS);
          int actualPartitions = description.partitions().size();
          if (actualPartitions < topic.partitions()) {
            throw new IllegalStateException(
                "Kafka topic has fewer partitions than required: topic="
                    + topic.name()
                    + ", required="
                    + topic.partitions()
                    + ", actual="
                    + actualPartitions);
          }
        } catch (ExecutionException ex) {
          Throwable rootCause = rootCause(ex);
          if (rootCause instanceof UnknownTopicOrPartitionException) {
            throw new IllegalStateException(
                "Kafka topic startup validation failed: missing topic=" + topic.name(), rootCause);
          }
          throw new IllegalStateException(
              "Kafka topic startup validation failed: topic=" + topic.name(), rootCause);
        }
      }
      log.info(
          "Kafka topic startup validation passed. bootstrapServers={}, topics={}",
          kafkaTopicTopology.bootstrapServers(),
          kafkaTopicTopology.topics().stream().map(KafkaTopicSpec::name).toList());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Kafka topic startup validation interrupted", ex);
    } catch (TimeoutException ex) {
      throw new IllegalStateException("Kafka topic startup validation timed out", ex);
    }
  }

  private Throwable rootCause(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }
}

record KafkaTopicTopology(String bootstrapServers, List<KafkaTopicSpec> topics) {}

record KafkaTopicSpec(String name, int partitions, int replicationFactor) {}

final class KafkaTopicTopologyResolver {

  private final KafkaTopicAdministrationProperties kafkaTopicAdministrationProperties;

  KafkaTopicTopologyResolver(
      KafkaTopicAdministrationProperties kafkaTopicAdministrationProperties) {
    this.kafkaTopicAdministrationProperties = kafkaTopicAdministrationProperties;
  }

  KafkaTopicTopology resolve(
      OutboxKafkaProperties outboxKafkaProperties,
      NotificationInboxConsumerProperties notificationInboxConsumerProperties) {
    LinkedHashMap<String, KafkaTopicSpec> topics = new LinkedHashMap<>();
    String bootstrapServers = null;
    bootstrapServers = resolveOutboxTopology(outboxKafkaProperties, topics, bootstrapServers);
    bootstrapServers =
        resolveNotificationTopology(notificationInboxConsumerProperties, topics, bootstrapServers);
    if (!StringUtils.hasText(bootstrapServers) || topics.isEmpty()) {
      throw new IllegalStateException(
          "Kafka topic administration requires at least one ready Kafka integration");
    }
    return new KafkaTopicTopology(bootstrapServers, List.copyOf(topics.values()));
  }

  private String resolveOutboxTopology(
      OutboxKafkaProperties outboxKafkaProperties,
      LinkedHashMap<String, KafkaTopicSpec> topics,
      String bootstrapServers) {
    if (!"ready".equals(outboxKafkaProperties.disabledReason())) {
      return bootstrapServers;
    }
    String resolvedBootstrapServers =
        mergeBootstrapServers(
            bootstrapServers,
            outboxKafkaProperties.bootstrapServers(),
            "outbox.kafka.bootstrap-servers");
    addTopic(topics, outboxKafkaProperties.topic().defaultName());
    for (String topicName : outboxKafkaProperties.topic().eventTypeOverrides().values()) {
      addTopic(topics, topicName);
    }
    return resolvedBootstrapServers;
  }

  private String resolveNotificationTopology(
      NotificationInboxConsumerProperties notificationInboxConsumerProperties,
      LinkedHashMap<String, KafkaTopicSpec> topics,
      String bootstrapServers) {
    if (!"ready".equals(notificationInboxConsumerProperties.disabledReason())) {
      return bootstrapServers;
    }
    String resolvedBootstrapServers =
        mergeBootstrapServers(
            bootstrapServers,
            notificationInboxConsumerProperties.bootstrapServers(),
            "notification.inbox.consumer.bootstrap-servers");
    for (String topicName : notificationInboxConsumerProperties.mainTopics()) {
      addTopic(topics, topicName);
    }
    addTopic(topics, notificationInboxConsumerProperties.dlq().topic());
    return resolvedBootstrapServers;
  }

  private String mergeBootstrapServers(
      String currentBootstrapServers, String candidateBootstrapServers, String propertyName) {
    if (!StringUtils.hasText(currentBootstrapServers)) {
      return candidateBootstrapServers;
    }
    if (!currentBootstrapServers.equals(candidateBootstrapServers)) {
      throw new IllegalStateException(
          "Kafka bootstrap servers must match across integrations. current="
              + currentBootstrapServers
              + ", candidate="
              + candidateBootstrapServers
              + ", property="
              + propertyName);
    }
    return currentBootstrapServers;
  }

  private void addTopic(LinkedHashMap<String, KafkaTopicSpec> topics, String topicName) {
    if (!StringUtils.hasText(topicName)) {
      return;
    }
    topics.putIfAbsent(
        topicName,
        new KafkaTopicSpec(
            topicName,
            kafkaTopicAdministrationProperties.provisioning().partitions(),
            kafkaTopicAdministrationProperties.provisioning().replicationFactor()));
  }
}

final class KafkaTopicAdministrationCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    boolean provisioningEnabled =
        context
            .getEnvironment()
            .getProperty("kafka.topic.provisioning.enabled", Boolean.class, true);
    boolean startupValidationEnabled =
        context
            .getEnvironment()
            .getProperty("kafka.topic.startup-validation.enabled", Boolean.class, true);
    return (provisioningEnabled || startupValidationEnabled) && hasReadyKafkaIntegration(context);
  }

  boolean hasReadyKafkaIntegration(ConditionContext context) {
    return hasReadyOutbox(context) || hasReadyNotificationConsumer(context);
  }

  private boolean hasReadyOutbox(ConditionContext context) {
    return context.getEnvironment().getProperty("outbox.kafka.enabled", Boolean.class, false)
        && StringUtils.hasText(
            context.getEnvironment().getProperty("outbox.kafka.bootstrap-servers"))
        && StringUtils.hasText(
            context.getEnvironment().getProperty("outbox.kafka.topic.default-name"));
  }

  private boolean hasReadyNotificationConsumer(ConditionContext context) {
    return context
            .getEnvironment()
            .getProperty("notification.inbox.consumer.enabled", Boolean.class, false)
        && usesNotificationKafkaRuntime(context)
        && StringUtils.hasText(
            context.getEnvironment().getProperty("notification.inbox.consumer.bootstrap-servers"))
        && (StringUtils.hasText(
                context
                    .getEnvironment()
                    .getProperty("notification.inbox.consumer.transfer-booked.topic"))
            || StringUtils.hasText(
                context
                    .getEnvironment()
                    .getProperty("notification.inbox.consumer.transfer-reversed.topic")));
  }

  private boolean usesNotificationKafkaRuntime(ConditionContext context) {
    // auto-startup=false + ops 비활성 경로는 broker 없이 consumer bean만 쓰는 테스트/로컬 구성을 허용합니다.
    return context
            .getEnvironment()
            .getProperty("notification.inbox.consumer.auto-startup", Boolean.class, false)
        || context
            .getEnvironment()
            .getProperty("notification.inbox.consumer.ops.enabled", Boolean.class, false);
  }
}

final class KafkaTopicProvisioningCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return new KafkaTopicAdministrationCondition().matches(context, metadata)
        && context
            .getEnvironment()
            .getProperty("kafka.topic.provisioning.enabled", Boolean.class, true);
  }
}

final class KafkaTopicStartupValidationCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return new KafkaTopicAdministrationCondition().matches(context, metadata)
        && context
            .getEnvironment()
            .getProperty("kafka.topic.startup-validation.enabled", Boolean.class, true);
  }
}
