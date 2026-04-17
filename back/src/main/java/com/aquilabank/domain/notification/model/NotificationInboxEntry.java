package com.aquilabank.domain.notification.model;

import java.time.Instant;

/** notification_inbox 적재용 account-scoped row 모델 */
public record NotificationInboxEntry(
    long accountId,
    String eventKey,
    String eventType,
    String title,
    String message,
    Instant createdAt) {

  public NotificationInboxEntry {
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (eventKey == null || eventKey.isBlank() || eventKey.length() > 80) {
      throw new IllegalArgumentException("eventKey must be between 1 and 80 characters");
    }
    if (eventType == null || eventType.isBlank() || eventType.length() > 60) {
      throw new IllegalArgumentException("eventType must be between 1 and 60 characters");
    }
    if (title == null || title.isBlank() || title.length() > 120) {
      throw new IllegalArgumentException("title must be between 1 and 120 characters");
    }
    if (message == null || message.isBlank() || message.length() > 280) {
      throw new IllegalArgumentException("message must be between 1 and 280 characters");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt must not be null");
    }
  }
}
