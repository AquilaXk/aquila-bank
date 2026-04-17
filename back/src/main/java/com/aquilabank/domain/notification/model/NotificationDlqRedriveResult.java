package com.aquilabank.domain.notification.model;

import java.time.Instant;

/** redrive 응답은 source DLQ 좌표와 재발행 결과 좌표를 같이 남깁니다. */
public record NotificationDlqRedriveResult(
    String sourceTopic,
    int sourcePartition,
    long sourceOffset,
    String eventKey,
    String targetTopic,
    int targetPartition,
    long targetOffset,
    Instant redrivenAt) {

  public NotificationDlqRedriveResult {
    if (sourceTopic == null || sourceTopic.isBlank()) {
      throw new IllegalArgumentException("sourceTopic is required");
    }
    if (sourcePartition < 0) {
      throw new IllegalArgumentException("sourcePartition must not be negative");
    }
    if (sourceOffset < 0) {
      throw new IllegalArgumentException("sourceOffset must not be negative");
    }
    if (targetTopic == null || targetTopic.isBlank()) {
      throw new IllegalArgumentException("targetTopic is required");
    }
    if (targetPartition < 0) {
      throw new IllegalArgumentException("targetPartition must not be negative");
    }
    if (targetOffset < 0) {
      throw new IllegalArgumentException("targetOffset must not be negative");
    }
    if (redrivenAt == null) {
      throw new IllegalArgumentException("redrivenAt is required");
    }
  }
}
