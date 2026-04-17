package com.aquilabank.domain.notification.model;

import java.time.Instant;

/** 최근 DLQ 적재 항목을 bounded preview로 보여주기 위한 최소 스냅샷입니다. */
public record NotificationDlqEvent(
    String topic,
    int partition,
    long offset,
    String eventKey,
    Instant publishedAt,
    String originalTopic,
    Integer originalPartition,
    Long originalOffset,
    String errorClass,
    String errorMessage,
    String payloadPreview) {

  public NotificationDlqEvent {
    if (topic == null || topic.isBlank()) {
      throw new IllegalArgumentException("topic is required");
    }
    if (partition < 0) {
      throw new IllegalArgumentException("partition must not be negative");
    }
    if (offset < 0) {
      throw new IllegalArgumentException("offset must not be negative");
    }
    if (publishedAt == null) {
      throw new IllegalArgumentException("publishedAt is required");
    }
    if (errorClass == null || errorClass.isBlank()) {
      throw new IllegalArgumentException("errorClass is required");
    }
    if (errorMessage == null || errorMessage.isBlank()) {
      throw new IllegalArgumentException("errorMessage is required");
    }
  }
}
