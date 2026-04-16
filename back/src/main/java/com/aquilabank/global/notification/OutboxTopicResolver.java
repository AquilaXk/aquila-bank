package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.OutboxEvent;
import com.aquilabank.global.config.OutboxKafkaProperties;
import java.util.Map;
import org.springframework.util.StringUtils;

/** 기본 topic + event type override 만 허용해 운영 topic 관리 복잡도를 낮춥니다. */
public final class OutboxTopicResolver {

  private final String defaultTopic;
  private final Map<String, String> eventTypeOverrides;

  public OutboxTopicResolver(OutboxKafkaProperties.TopicProperties topicProperties) {
    this.defaultTopic = topicProperties.defaultName();
    this.eventTypeOverrides = topicProperties.eventTypeOverrides();
  }

  public String resolve(OutboxEvent event) {
    String topic = eventTypeOverrides.get(event.eventType());
    if (StringUtils.hasText(topic)) {
      return topic;
    }
    if (StringUtils.hasText(defaultTopic)) {
      return defaultTopic;
    }
    throw new IllegalStateException(
        "outbox Kafka topic is missing for eventType=" + event.eventType());
  }
}
