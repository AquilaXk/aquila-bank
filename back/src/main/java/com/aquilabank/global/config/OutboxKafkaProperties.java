package com.aquilabank.global.config;

import java.util.Map;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Kafka producer 전용 설정을 poller 설정과 분리해 운영 경계를 명확히 둡니다. */
@ConfigurationProperties(prefix = "outbox.kafka")
public record OutboxKafkaProperties(
    boolean enabled,
    String bootstrapServers,
    long sendTimeoutMs,
    TopicProperties topic,
    SerializerProperties serializer,
    RetryProperties retry,
    ProducerProperties producer) {

  public OutboxKafkaProperties {
    sendTimeoutMs = sendTimeoutMs > 0 ? sendTimeoutMs : 5000;
    topic = topic == null ? new TopicProperties(null, Map.of()) : topic;
    serializer =
        serializer == null
            ? new SerializerProperties(
                StringSerializer.class.getName(), StringSerializer.class.getName())
            : serializer;
    retry = retry == null ? new RetryProperties(3, 1000, 30000, 3000) : retry;
    producer =
        producer == null
            ? new ProducerProperties("aquila-bank-outbox-producer", "all", true, 1)
            : producer;

    // enabled=true가 fallback으로 통과하면 live evidence가 Kafka publish 경로를 보장하지 못합니다.
    if (enabled) {
      requireText(
          bootstrapServers,
          "outbox.kafka.bootstrap-servers is required when outbox.kafka.enabled=true");
      if (!topic.hasDefaultTopic()) {
        throw new IllegalArgumentException(
            "outbox.kafka.topic.default-name is required when outbox.kafka.enabled=true");
      }
    }
  }

  public String disabledReason() {
    if (!enabled) {
      return "outbox.kafka.enabled=false";
    }
    if (!StringUtils.hasText(bootstrapServers)) {
      return "outbox.kafka.bootstrap-servers is blank";
    }
    if (!topic.hasDefaultTopic()) {
      return "outbox.kafka.topic.default-name is blank";
    }
    return "ready";
  }

  public record TopicProperties(String defaultName, Map<String, String> eventTypeOverrides) {

    public TopicProperties {
      eventTypeOverrides = eventTypeOverrides == null ? Map.of() : Map.copyOf(eventTypeOverrides);
    }

    boolean hasDefaultTopic() {
      return StringUtils.hasText(defaultName);
    }
  }

  public record SerializerProperties(String keyClass, String valueClass) {}

  public record RetryProperties(
      int retries, long retryBackoffMs, long deliveryTimeoutMs, long requestTimeoutMs) {}

  public record ProducerProperties(
      String clientId,
      String acks,
      boolean enableIdempotence,
      int maxInFlightRequestsPerConnection) {}

  private static void requireText(String value, String message) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException(message);
    }
  }
}
