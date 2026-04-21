package com.aquilabank.domain.notification.model;

import java.time.Instant;

/** EMAIL/SMS provider worker가 가져갈 durable delivery outbox 적재 모델 */
public record NotificationChannelOutboxEntry(
    long notificationId,
    long userId,
    long accountId,
    NotificationPreferenceCategory category,
    NotificationPreferenceChannel channel,
    String eventType,
    String eventKey,
    String payload,
    Instant availableAt,
    Instant createdAt) {

  public NotificationChannelOutboxEntry {
    if (notificationId <= 0) {
      throw new IllegalArgumentException("notificationId must be positive");
    }
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (category == null) {
      throw new IllegalArgumentException("category must not be null");
    }
    if (channel != NotificationPreferenceChannel.EMAIL
        && channel != NotificationPreferenceChannel.SMS) {
      throw new IllegalArgumentException("channel must be EMAIL or SMS");
    }
    if (eventType == null || eventType.isBlank() || eventType.length() > 60) {
      throw new IllegalArgumentException("eventType must be between 1 and 60 characters");
    }
    if (eventKey == null || eventKey.isBlank() || eventKey.length() > 160) {
      throw new IllegalArgumentException("eventKey must be between 1 and 160 characters");
    }
    if (payload == null || payload.isBlank()) {
      throw new IllegalArgumentException("payload must not be blank");
    }
    if (availableAt == null) {
      throw new IllegalArgumentException("availableAt must not be null");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt must not be null");
    }
  }
}
