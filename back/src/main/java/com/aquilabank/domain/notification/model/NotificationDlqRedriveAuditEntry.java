package com.aquilabank.domain.notification.model;

import java.time.Instant;

/** DLQ redrive 시도별 운영 추적을 위한 audit write 모델 */
public record NotificationDlqRedriveAuditEntry(
    String actor,
    String requestId,
    String sourceTopic,
    int sourcePartition,
    long sourceOffset,
    String eventKey,
    String targetTopic,
    Integer targetPartition,
    Long targetOffset,
    NotificationDlqRedriveOutcome outcome,
    String errorMessage,
    Instant redrivenAt) {

  public NotificationDlqRedriveAuditEntry {
    validateRequiredText(actor, "actor", 120);
    validateRequiredText(requestId, "requestId", 120);
    validateRequiredText(sourceTopic, "sourceTopic", 160);
    validateOptionalText(eventKey, "eventKey", 160);
    validateOptionalText(targetTopic, "targetTopic", 160);
    if (sourcePartition < 0) {
      throw new IllegalArgumentException("sourcePartition must not be negative");
    }
    if (sourceOffset < 0) {
      throw new IllegalArgumentException("sourceOffset must not be negative");
    }
    if (targetPartition != null && targetPartition < 0) {
      throw new IllegalArgumentException("targetPartition must not be negative");
    }
    if (targetOffset != null && targetOffset < 0) {
      throw new IllegalArgumentException("targetOffset must not be negative");
    }
    if (outcome == null) {
      throw new IllegalArgumentException("outcome must not be null");
    }
    if (outcome == NotificationDlqRedriveOutcome.SUCCESS
        && (targetTopic == null || targetPartition == null || targetOffset == null)) {
      throw new IllegalArgumentException("success audit must include target offset");
    }
    if (redrivenAt == null) {
      throw new IllegalArgumentException("redrivenAt must not be null");
    }
  }

  private static void validateRequiredText(String value, String name, int maxLength) {
    if (value == null || value.isBlank() || value.length() > maxLength) {
      throw new IllegalArgumentException(
          "%s must be between 1 and %d characters".formatted(name, maxLength));
    }
  }

  private static void validateOptionalText(String value, String name, int maxLength) {
    if (value != null && (value.isBlank() || value.length() > maxLength)) {
      throw new IllegalArgumentException(
          "%s must be between 1 and %d characters".formatted(name, maxLength));
    }
  }
}
