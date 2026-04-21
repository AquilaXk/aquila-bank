package com.aquilabank.domain.notification.model;

import java.time.Instant;

/** channel delivery queue 조회/claim 전 단계에서 사용하는 outbox row snapshot */
public record NotificationChannelOutboxItem(
    long id,
    long notificationId,
    long userId,
    long accountId,
    NotificationPreferenceCategory category,
    NotificationPreferenceChannel channel,
    String eventType,
    String eventKey,
    String payload,
    NotificationChannelDeliveryStatus deliveryStatus,
    Instant availableAt,
    Instant sentAt,
    int retryCount,
    String lastError,
    Instant createdAt,
    Instant updatedAt) {}
