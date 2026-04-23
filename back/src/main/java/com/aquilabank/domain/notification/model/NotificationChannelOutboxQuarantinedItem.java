package com.aquilabank.domain.notification.model;

import java.time.Instant;

/** 운영자가 격리 원인과 재처리 대상을 식별할 최소 channel outbox 스냅샷입니다. */
public record NotificationChannelOutboxQuarantinedItem(
    long id,
    long notificationId,
    long userId,
    long accountId,
    NotificationPreferenceCategory category,
    NotificationPreferenceChannel channel,
    String eventType,
    String eventKey,
    int retryCount,
    String lastError,
    Instant createdAt,
    Instant updatedAt) {

  public NotificationChannelOutboxQuarantinedItem {
    if (id <= 0) {
      throw new IllegalArgumentException("id must be positive");
    }
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
    if (retryCount < 0) {
      throw new IllegalArgumentException("retryCount must not be negative");
    }
    if (lastError == null || lastError.isBlank()) {
      throw new IllegalArgumentException("lastError is required");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt must not be null");
    }
    if (updatedAt == null) {
      throw new IllegalArgumentException("updatedAt must not be null");
    }
  }
}
