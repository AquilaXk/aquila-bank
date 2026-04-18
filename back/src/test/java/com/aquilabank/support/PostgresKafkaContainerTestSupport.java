package com.aquilabank.support;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.junit.jupiter.api.Assertions;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresKafkaContainerTestSupport extends PostgresContainerTestSupport {

  protected static final String OUTBOX_DEFAULT_TOPIC = "bank.notification.outbox.v1";
  protected static final String TRANSFER_BOOKED_TOPIC = "bank.transfer.booked.v1";
  protected static final String TRANSFER_BOOKED_DLQ_TOPIC = "bank.transfer.booked.dlq.v1";
  protected static final String TRANSFER_REVERSED_TOPIC = "bank.transfer.reversed.v1";

  private static final DockerImageName KAFKA_IMAGE =
      DockerImageName.parse("apache/kafka-native:3.8.0");

  @Container private static final KafkaContainer KAFKA = new KafkaContainer(KAFKA_IMAGE);

  private static final AtomicBoolean KAFKA_TOPICS_READY = new AtomicBoolean(false);

  @DynamicPropertySource
  static void registerKafkaProperties(DynamicPropertyRegistry registry) {
    ensureKafkaTopics();
    registry.add("outbox.kafka.enabled", () -> true);
    registry.add("outbox.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add("outbox.kafka.topic.default-name", () -> OUTBOX_DEFAULT_TOPIC);
    registry.add(
        "outbox.kafka.topic.event-type-overrides.TransferBooked", () -> TRANSFER_BOOKED_TOPIC);
    registry.add(
        "outbox.kafka.topic.event-type-overrides.TransferReversed", () -> TRANSFER_REVERSED_TOPIC);
    registry.add("notification.inbox.consumer.enabled", () -> true);
    registry.add("notification.inbox.consumer.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add("notification.inbox.consumer.group-id", () -> "aquila-bank-notification-e2e");
    registry.add("notification.inbox.consumer.transfer-booked.topic", () -> TRANSFER_BOOKED_TOPIC);
    registry.add(
        "notification.inbox.consumer.transfer-reversed.topic", () -> TRANSFER_REVERSED_TOPIC);
    registry.add("notification.inbox.consumer.dlq.topic", () -> TRANSFER_BOOKED_DLQ_TOPIC);
  }

  protected void awaitCondition(
      String description, Duration timeout, Duration interval, BooleanSupplier condition) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      try {
        Thread.sleep(interval.toMillis());
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("condition wait interrupted", ex);
      }
    }
    Assertions.fail(description + " was not satisfied within " + timeout);
  }

  private static void ensureKafkaTopics() {
    if (KAFKA_TOPICS_READY.get()) {
      return;
    }
    synchronized (KAFKA_TOPICS_READY) {
      if (KAFKA_TOPICS_READY.get()) {
        return;
      }
      if (!KAFKA.isRunning()) {
        KAFKA.start();
      }
      try (AdminClient adminClient =
          AdminClient.create(
              Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
        createTopic(adminClient, OUTBOX_DEFAULT_TOPIC);
        createTopic(adminClient, TRANSFER_BOOKED_TOPIC);
        createTopic(adminClient, TRANSFER_BOOKED_DLQ_TOPIC);
        createTopic(adminClient, TRANSFER_REVERSED_TOPIC);
      }
      KAFKA_TOPICS_READY.set(true);
    }
  }

  private static void createTopic(AdminClient adminClient, String topicName) {
    try {
      // 단일 broker 테스트는 1 partition/1 replica 로 두어 ordering과 자원 사용량을 단순화합니다.
      adminClient.createTopics(List.of(new NewTopic(topicName, 1, (short) 1))).all().get();
    } catch (Exception ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof TopicExistsException) {
        return;
      }
      throw new IllegalStateException("failed to create Kafka topic: " + topicName, ex);
    }
  }
}
