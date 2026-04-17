package com.aquilabank.domain.notification.model;

import java.time.Instant;

/** 알림 inbox 목록 응답용 read model projection */
public record NotificationSummary(
    long id,
    long accountId,
    String eventType,
    String title,
    String message,
    Instant createdAt,
    Instant readAt) {}
