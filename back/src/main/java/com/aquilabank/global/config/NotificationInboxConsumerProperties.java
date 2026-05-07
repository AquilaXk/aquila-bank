package com.aquilabank.global.config;

import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** notification inbox consumer 설정을 outbox producer/poller 와 분리합니다. */
@ConfigurationProperties(prefix = "notification.inbox.consumer")
public record NotificationInboxConsumerProperties(
    boolean enabled,
    boolean autoStartup,
    int concurrency,
    String bootstrapServers,
    String groupId,
    String autoOffsetReset,
    TopicProperties transferBooked,
    TopicProperties transferReversed,
    DlqProperties dlq,
    OpsProperties ops) {

  public NotificationInboxConsumerProperties {
    autoStartup = autoStartup || !enabled;
    concurrency = concurrency > 0 ? concurrency : 1;
    groupId = StringUtils.hasText(groupId) ? groupId : "aquila-bank-notification-inbox-consumer";
    autoOffsetReset = StringUtils.hasText(autoOffsetReset) ? autoOffsetReset : "earliest";
    transferBooked = transferBooked == null ? new TopicProperties(null) : transferBooked;
    transferReversed = transferReversed == null ? new TopicProperties(null) : transferReversed;
    dlq = dlq == null ? new DlqProperties(null) : dlq;
    ops = ops == null ? new OpsProperties(false, 20, 5, null) : ops;

    // enabled=true가 bean 미생성으로 통과하면 live evidence가 Kafka consume 경로를 보장하지 못합니다.
    if (enabled) {
      requireText(
          bootstrapServers,
          "notification.inbox.consumer.bootstrap-servers is required when notification.inbox.consumer.enabled=true");
      if (!hasAnyMainTopic(transferBooked, transferReversed)) {
        throw new IllegalArgumentException(
            "notification.inbox.consumer.transfer-booked.topic or notification.inbox.consumer.transfer-reversed.topic is required when notification.inbox.consumer.enabled=true");
      }
    }
    if (ops.enabled()) {
      if (!enabled) {
        throw new IllegalArgumentException(
            "notification.inbox.consumer.enabled=true is required when notification.inbox.consumer.ops.enabled=true");
      }
      requireText(
          dlq.topic(),
          "notification.inbox.consumer.dlq.topic is required when notification.inbox.consumer.ops.enabled=true");
    }
  }

  public String disabledReason() {
    if (!enabled) {
      return "notification.inbox.consumer.enabled=false";
    }
    if (!StringUtils.hasText(bootstrapServers)) {
      return "notification.inbox.consumer.bootstrap-servers is blank";
    }
    if (!hasConfiguredMainTopic()) {
      return "notification.inbox.consumer.transfer-booked.topic and transfer-reversed.topic are blank";
    }
    return "ready";
  }

  public String opsDisabledReason() {
    if (!ops.enabled()) {
      return "notification.inbox.consumer.ops.enabled=false";
    }
    if (!enabled) {
      return "notification.inbox.consumer.enabled=false";
    }
    if (!StringUtils.hasText(bootstrapServers)) {
      return "notification.inbox.consumer.bootstrap-servers is blank";
    }
    if (!hasConfiguredMainTopic()) {
      return "notification.inbox.consumer.transfer-booked.topic and transfer-reversed.topic are blank";
    }
    if (!StringUtils.hasText(dlq.topic())) {
      return "notification.inbox.consumer.dlq.topic is blank";
    }
    return "ready";
  }

  public boolean hasConfiguredMainTopic() {
    return !mainTopics().isEmpty();
  }

  public List<String> mainTopics() {
    LinkedHashSet<String> topics = new LinkedHashSet<>();
    if (StringUtils.hasText(transferBooked.topic())) {
      topics.add(transferBooked.topic());
    }
    if (StringUtils.hasText(transferReversed.topic())) {
      topics.add(transferReversed.topic());
    }
    return List.copyOf(topics);
  }

  public String mainTopicLabel() {
    List<String> topics = mainTopics();
    return topics.isEmpty() ? "-" : String.join(",", topics);
  }

  public record TopicProperties(String topic) {}

  public record DlqProperties(String topic) {}

  public record OpsProperties(
      boolean enabled, int dlqPreviewLimit, long summaryCacheSeconds, Health health) {

    public OpsProperties {
      dlqPreviewLimit = dlqPreviewLimit > 0 ? dlqPreviewLimit : 20;
      summaryCacheSeconds = summaryCacheSeconds > 0 ? summaryCacheSeconds : 5;
      health = health == null ? new Health(100, 0) : health;
    }
  }

  public record Health(long maxLagMessages, long maxDlqCount) {

    public Health {
      if (maxLagMessages < 0) {
        throw new IllegalArgumentException(
            "notification.inbox.consumer.ops.health.max-lag-messages must not be negative");
      }
      if (maxDlqCount < 0) {
        throw new IllegalArgumentException(
            "notification.inbox.consumer.ops.health.max-dlq-count must not be negative");
      }
    }
  }

  private static boolean hasAnyMainTopic(
      TopicProperties transferBooked, TopicProperties transferReversed) {
    return StringUtils.hasText(transferBooked.topic())
        || StringUtils.hasText(transferReversed.topic());
  }

  private static void requireText(String value, String message) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException(message);
    }
  }
}
