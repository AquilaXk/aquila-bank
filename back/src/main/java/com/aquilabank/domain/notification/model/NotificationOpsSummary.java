package com.aquilabank.domain.notification.model;

import java.time.Instant;

/** consumer lag 와 DLQ 적재량을 같은 운영 기준으로 보기 위한 notification summary 입니다. */
public record NotificationOpsSummary(
    Instant observedAt,
    String consumerGroupId,
    String topic,
    String dlqTopic,
    long lagCount,
    long dlqCount) {

  public NotificationOpsSummary {
    if (observedAt == null) {
      throw new IllegalArgumentException("observedAt is required");
    }
    if (consumerGroupId == null || consumerGroupId.isBlank()) {
      throw new IllegalArgumentException("consumerGroupId is required");
    }
    if (topic == null || topic.isBlank()) {
      throw new IllegalArgumentException("topic is required");
    }
    if (dlqTopic == null || dlqTopic.isBlank()) {
      throw new IllegalArgumentException("dlqTopic is required");
    }
    if (lagCount < 0) {
      throw new IllegalArgumentException("lagCount must not be negative");
    }
    if (dlqCount < 0) {
      throw new IllegalArgumentException("dlqCount must not be negative");
    }
  }
}
